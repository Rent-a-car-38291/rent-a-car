package com.example.trips

import com.example.accounts.Users
import com.example.reservations.Reservations
import com.example.reservations.createReservations
import com.example.reservations.Daypart
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import javax.sql.DataSource

private const val FOREIGN_KEY_VIOLATION = "23503"

// A reservation has at most one trip, the unique index decides. Deleting the reservation takes its trip along.
internal object Trips : Table("trips") {
    val id = integer("id").autoIncrement()
    val reservationId = integer("reservation_id").references(Reservations.id, onDelete = ReferenceOption.CASCADE).uniqueIndex("trips_reservation_id_key")
    val distanceKm = integer("distance_km")
    val score = integer("score")
    // Only set for a trip that was started and ended; null for one sent in one call.
    val startedAt = timestampWithTimeZone("started_at").nullable()
    val durationMinutes = integer("duration_minutes").nullable()
    override val primaryKey = PrimaryKey(id)
}

// A reservation is started at most once, the primary key decides. Deleting the reservation takes it along.
internal object TripStarts : Table("trip_starts") {
    val reservationId = integer("reservation_id").references(Reservations.id, onDelete = ReferenceOption.CASCADE)
    val startedAt = timestampWithTimeZone("started_at")
    override val primaryKey = PrimaryKey(reservationId)
}

// Also used by the reservations, which look for a trip before they cancel.
internal fun Database.createTrips() {
    createReservations() // trips reference reservations
    transaction(this) {
        SchemaUtils.create(Trips, TripStarts)
        // SchemaUtils.create leaves an older table alone, so add the columns added later.
        exec("ALTER TABLE trips ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ, ADD COLUMN IF NOT EXISTS duration_minutes INTEGER")
    }
}

class TripRepository(dataSource: DataSource) {
    private val db = Database.connect(dataSource)

    init {
        db.createTrips()
    }

    // null when the reservation was already started: the primary key decides, so two requests at once can't both win.
    // A reservation that already has a trip sent in one call can't be started either.
    // shortcut: a reservation deleted just before the insert also gives null, so the route answers 409 instead of 404.
    fun start(reservationId: Int, now: OffsetDateTime): OffsetDateTime? = try {
        transaction(db) {
            val finished = !Trips.selectAll().where { Trips.reservationId eq reservationId }.empty()
            val inserted = !finished && TripStarts.insertIgnore {
                it[TripStarts.reservationId] = reservationId
                it[startedAt] = now
            }.insertedCount.let { it > 0 }
            if (inserted) now else null
        }
    } catch (e: ExposedSQLException) {
        if (e.sqlState == FOREIGN_KEY_VIOLATION) null else throw e
    }

    // Saves the trip and adds the points to the renter in one transaction, so there are never points without a trip.
    // Returns the trip and the renter's new total, or null when the reservation already has a trip.
    // With endedAt the trip must have been started: its duration runs from the start to endedAt. Without it the trip must not have been started.
    // null also means one of those two was not the case.
    // shortcut: a reservation deleted just before the insert also gives null, so the route answers 409 instead of 404.
    fun record(reservationId: Int, userId: Int, distanceKm: Int, score: Int, points: Int, endedAt: OffsetDateTime? = null): Pair<Trip, Int>? = try {
        transaction(db) {
            val start = TripStarts.selectAll().where { TripStarts.reservationId eq reservationId }.singleOrNull()?.get(TripStarts.startedAt)
            // A started trip is ended with endedAt, never in one call, and an end needs its start.
            if ((endedAt == null) == (start != null)) return@transaction null
            val startedAt = start
            val minutes = endedAt?.let { durationMinutes(startedAt!!.toInstant(), it.toInstant()) }
            val inserted = Trips.insertIgnore {
                it[Trips.reservationId] = reservationId
                it[Trips.distanceKm] = distanceKm
                it[Trips.score] = score
                it[Trips.startedAt] = startedAt
                it[durationMinutes] = minutes
            }
            if (inserted.insertedCount == 0) {
                null
            } else {
                Users.update({ Users.id eq userId }) { it[bonusPoints] = bonusPoints + points }
                val total = Users.selectAll().where { Users.id eq userId }.single()[Users.bonusPoints]
                Trip(inserted[Trips.id], reservationId, distanceKm, score, startedAt?.toInstant()?.toString(), minutes) to total
            }
        }
    } catch (e: ExposedSQLException) {
        if (e.sqlState == FOREIGN_KEY_VIOLATION) null else throw e
    }

    // One joined query. Pass the renter (the logged-in user's own trips) or the car (the trips of a car), or both.
    // Newest first: the date, then the daypart, then the order the trips were recorded in.
    fun history(userId: Int?, carId: Int?, filter: TripFilter): List<TripHistoryItem> = transaction(db) {
        val daypartRank = Case()
            .When(Reservations.daypart eq Daypart.EVENING.name, intLiteral(Daypart.EVENING.ordinal))
            .When(Reservations.daypart eq Daypart.AFTERNOON.name, intLiteral(Daypart.AFTERNOON.ordinal))
            .Else(intLiteral(Daypart.MORNING.ordinal))
        (Trips innerJoin Reservations).selectAll()
            .where {
                (userId?.let { Reservations.userId eq it } ?: Op.TRUE) and
                    (carId?.let { Reservations.carId eq it } ?: Op.TRUE) and
                    (filter.from?.let { Reservations.date greaterEq it } ?: Op.TRUE) and
                    (filter.to?.let { Reservations.date lessEq it } ?: Op.TRUE)
            }
            .orderBy(Reservations.date to SortOrder.DESC, daypartRank to SortOrder.DESC, Trips.id to SortOrder.DESC)
            .limit(filter.limit).offset(filter.offset.toLong())
            .map {
                TripHistoryItem(it[Trips.id], it[Trips.reservationId], it[Reservations.carId], it[Reservations.date].toString(),
                    Daypart.valueOf(it[Reservations.daypart]), it[Trips.distanceKm], it[Trips.score],
                    it[Trips.startedAt]?.toInstant()?.toString(), it[Trips.durationMinutes])
            }
    }
}
