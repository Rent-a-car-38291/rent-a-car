package com.example.trips

import com.example.Paging
import com.example.accounts.Users
import com.example.cars.Cars
import com.example.cars.MAX_MILEAGE
import com.example.reservations.ReservationStatus
import com.example.reservations.Reservations
import com.example.reservations.createReservations
import com.example.reservations.Daypart
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.castTo
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
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

// Also used by the reservations, which look for a started trip (trip_starts) before they cancel.
internal fun Database.createTrips() {
    createReservations() // trips reference reservations
    transaction(this) {
        SchemaUtils.create(Trips, TripStarts)
        // SchemaUtils.create leaves an older table alone, so add the columns added later.
        exec("ALTER TABLE trips ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ, ADD COLUMN IF NOT EXISTS duration_minutes INTEGER")
        // Reservations from before they had a status: the ones with a trip are completed. A new trip completes its reservation itself.
        exec("UPDATE reservations SET status = 'COMPLETED' WHERE status = 'RESERVED' AND id IN (SELECT reservation_id FROM trips)")
    }
}

class TripRepository(dataSource: DataSource) {
    private val db = Database.connect(dataSource)

    init {
        db.createTrips()
    }

    // Locks the reservation row, so a cancel and a trip of the same reservation wait for each other. True while it is still RESERVED.
    // Must run inside a transaction.
    private fun lockReserved(reservationId: Int): Boolean =
        Reservations.select(Reservations.status).where { Reservations.id eq reservationId }.forUpdate().singleOrNull()
            ?.get(Reservations.status) == ReservationStatus.RESERVED.name

    // null when the reservation was already started: the primary key decides, so two requests at once can't both win.
    // A reservation that already has a trip sent in one call can't be started either, nor can a cancelled one.
    // shortcut: a reservation deleted just before the insert also gives null, so the route answers 409 instead of 404.
    fun start(reservationId: Int, now: OffsetDateTime): OffsetDateTime? = try {
        transaction(db) {
            if (!lockReserved(reservationId)) return@transaction null
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
    // null also means one of those two was not the case, or that the reservation is cancelled. The reservation becomes COMPLETED in the same transaction.
    // shortcut: a reservation deleted just before the insert also gives null, so the route answers 409 instead of 404.
    fun record(reservationId: Int, userId: Int, distanceKm: Int, score: Int, points: Int, endedAt: OffsetDateTime? = null): Pair<Trip, Int>? = try {
        transaction(db) {
            if (!lockReserved(reservationId)) return@transaction null
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
                Reservations.update({ Reservations.id eq reservationId }) { it[status] = ReservationStatus.COMPLETED.name }
                // An increment in the statement itself, so a trip never overwrites a mileage that changed meanwhile.
                val carId = Reservations.select(Reservations.carId).where { Reservations.id eq reservationId }.single()[Reservations.carId]
                // Capped at MAX_MILEAGE, so a car near the limit can still be updated with the mileage it shows.
                Cars.update({ Cars.id eq carId }) { it[mileage] = CustomFunction("LEAST", IntegerColumnType(), mileage + distanceKm, intLiteral(MAX_MILEAGE)) }
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
            .where { historyCondition(userId, carId, filter) }
            .orderBy(Reservations.date to SortOrder.DESC, daypartRank to SortOrder.DESC, Trips.id to SortOrder.DESC)
            .limit(filter.limit).offset(filter.offset.toLong())
            .map {
                TripHistoryItem(it[Trips.id], it[Trips.reservationId], it[Reservations.carId], it[Reservations.date].toString(),
                    Daypart.valueOf(it[Reservations.daypart]), it[Trips.distanceKm], it[Trips.score],
                    it[Trips.startedAt]?.toInstant()?.toString(), it[Trips.durationMinutes])
            }
    }

    // The number of trips behind a history, ignoring the paging.
    fun historyCount(userId: Int?, carId: Int?, filter: TripFilter): Int = transaction(db) {
        (Trips innerJoin Reservations).selectAll().where { historyCondition(userId, carId, filter) }.count().toInt()
    }

    private fun historyCondition(userId: Int?, carId: Int?, filter: TripFilter): Op<Boolean> =
        (userId?.let { Reservations.userId eq it } ?: Op.TRUE) and
            (carId?.let { Reservations.carId eq it } ?: Op.TRUE) and
            (filter.from?.let { Reservations.date greaterEq it } ?: Op.TRUE) and
            (filter.to?.let { Reservations.date lessEq it } ?: Op.TRUE)

    // null for an unknown user. The trips are added up per daypart in one query, so only finished trips count
    // (a started trip is not in the trips table until it ends).
    fun stats(userId: Int): DrivingStats? = transaction(db) {
        val bonus = Users.selectAll().where { Users.id eq userId }.singleOrNull()?.get(Users.bonusPoints) ?: return@transaction null
        val count = Trips.id.count()
        val km = Trips.distanceKm.castTo(LongColumnType()).sum()
        val scoreSum = Trips.score.castTo(LongColumnType()).sum()
        val totals = (Trips innerJoin Reservations).select(Reservations.daypart, count, km, scoreSum)
            .where { Reservations.userId eq userId }
            .groupBy(Reservations.daypart)
            .map { DaypartTotals(Daypart.valueOf(it[Reservations.daypart]), it[count].toInt(), it[km]!!.toLong(), it[scoreSum]!!.toLong()) }
        calculateStats(totals, bonus)
    }

    // Reservations and finished trips per car in one query. Cancelled reservations are left out (they never have a trip);
    // a car without reservations is not in the map.
    fun usageByCar(carIds: Collection<Int>): Map<Int, CarUsage> = transaction(db) {
        if (carIds.isEmpty()) return@transaction emptyMap()
        val reservations = Reservations.id.count()
        val trips = Trips.id.count()
        val km = Trips.distanceKm.castTo(LongColumnType()).sum()
        val scoreSum = Trips.score.castTo(LongColumnType()).sum()
        Reservations.leftJoin(Trips).select(Reservations.carId, reservations, trips, km, scoreSum)
            .where { (Reservations.carId inList carIds) and (Reservations.status neq ReservationStatus.CANCELLED.name) }
            .groupBy(Reservations.carId)
            .associate { it[Reservations.carId] to CarUsage(it[reservations].toInt(), it[trips].toInt(), it[km] ?: 0, it[scoreSum] ?: 0) }
    }

    // The most points first, equal points by name and then id so the order is stable. A user without points is not listed,
    // which also leaves out the reserved sample fleet user. One query with the page in the database.
    fun leaderboard(paging: Paging): List<LeaderboardEntry> = transaction(db) {
        Users.select(Users.name, Users.bonusPoints).where { Users.bonusPoints greater 0 }
            .orderBy(Users.bonusPoints to SortOrder.DESC, Users.name to SortOrder.ASC, Users.id to SortOrder.ASC)
            .limit(paging.limit).offset(paging.offset.toLong())
            .map { LeaderboardEntry(it[Users.name], it[Users.bonusPoints]) }
    }

    fun leaderboardSize(): Int = transaction(db) { Users.selectAll().where { Users.bonusPoints greater 0 }.count().toInt() }
}
