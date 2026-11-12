package com.example.reservations

import com.example.accounts.Users
import com.example.cars.Cars
import com.example.cars.createCars
import com.example.trips.TripStarts
import com.example.trips.createTrips
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import javax.sql.DataSource

private const val UNIQUE_VIOLATION = "23505"
private const val FOREIGN_KEY_VIOLATION = "23503"

// Deleting the car or the renter takes their reservations along. A cancelled reservation is kept; the unique index
// reservations_active_slot_key (made in createReservations) keeps an active one from being booked twice for the same daypart.
internal object Reservations : Table("reservations") {
    val id = integer("id").autoIncrement()
    val carId = integer("car_id").references(Cars.id, onDelete = ReferenceOption.CASCADE)
    val userId = integer("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val date = date("date")
    val daypart = text("daypart").check { it inList Daypart.entries.map { daypart -> daypart.name } }
    val status = text("status").default(ReservationStatus.RESERVED.name).check { it inList ReservationStatus.entries.map { status -> status.name } }
    override val primaryKey = PrimaryKey(id)
}

private fun ResultRow.toReservation() =
    Reservation(this[Reservations.id], this[Reservations.carId], this[Reservations.userId], this[Reservations.date].toString(), Daypart.valueOf(this[Reservations.daypart]), ReservationStatus.valueOf(this[Reservations.status]))

// Also used by the trips, which reference reservations.
internal fun Database.createReservations() {
    createCars() // reservations reference cars and users
    transaction(this) {
        SchemaUtils.create(Reservations)
        // SchemaUtils.create leaves an older table alone, so add the column added later (reservations that exist are still reserved)
        // and replace the old unique index, which would keep a cancelled reservation's daypart from being booked again.
        exec("ALTER TABLE reservations ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'RESERVED' CHECK (status IN ('RESERVED', 'COMPLETED', 'CANCELLED'))")
        exec("DROP INDEX IF EXISTS reservations_car_id_date_daypart_key")
        exec("CREATE UNIQUE INDEX IF NOT EXISTS reservations_active_slot_key ON reservations (car_id, date, daypart) WHERE status <> 'CANCELLED'")
    }
}

/** NOT_ALLOWED: the reservation is completed or already cancelled, or its trip was started. */
enum class CancelResult { CANCELLED, GONE, NOT_ALLOWED }

class ReservationRepository(dataSource: DataSource) {
    // createTrips: cancelling looks at the trip_starts table, so it has to exist even when nothing else made it.
    private val db = Database.connect(dataSource).also { it.createTrips() }

    fun find(id: Int): Reservation? = transaction(db) { Reservations.selectAll().where { Reservations.id eq id }.singleOrNull()?.toReservation() }

    /**
     * Null when the daypart is taken: the unique index on active reservations decides, so two requests at once can't both win.
     * shortcut: a car or user deleted just before the insert also gives null, so the route answers 409 instead of 404 or 401.
     */
    fun reserve(carId: Int, userId: Int, date: LocalDate, daypart: Daypart): Reservation? = try {
        transaction(db) {
            Reservations.insert {
                it[Reservations.carId] = carId
                it[Reservations.userId] = userId
                it[Reservations.date] = date
                it[Reservations.daypart] = daypart.name
            }.resultedValues!!.single().toReservation()
        }
    } catch (e: ExposedSQLException) {
        if (e.sqlState == UNIQUE_VIOLATION || e.sqlState == FOREIGN_KEY_VIOLATION) null else throw e
    }

    /**
     * Sets the status to CANCELLED and keeps the row. Starting and ending a trip lock the same row, so a trip that is being started or saved
     * for this reservation is seen by the checks below, and a cancelled reservation can't get a trip afterwards.
     */
    fun cancel(id: Int): CancelResult = transaction(db) {
        val status = Reservations.select(Reservations.status).where { Reservations.id eq id }.forUpdate().singleOrNull()
            ?.let { ReservationStatus.valueOf(it[Reservations.status]) }
        when {
            status == null -> CancelResult.GONE
            !status.canMoveTo(ReservationStatus.CANCELLED) || !TripStarts.selectAll().where { TripStarts.reservationId eq id }.empty() -> CancelResult.NOT_ALLOWED
            else -> {
                Reservations.update({ Reservations.id eq id }) { it[Reservations.status] = ReservationStatus.CANCELLED.name }
                CancelResult.CANCELLED
            }
        }
    }

    // The reservations of a renter in one query, newest first: the date, then the daypart, then the order they were made in.
    fun list(userId: Int, filter: ReservationFilter): List<Reservation> = transaction(db) {
        val daypartRank = Case()
            .When(Reservations.daypart eq Daypart.EVENING.name, intLiteral(Daypart.EVENING.ordinal))
            .When(Reservations.daypart eq Daypart.AFTERNOON.name, intLiteral(Daypart.AFTERNOON.ordinal))
            .Else(intLiteral(Daypart.MORNING.ordinal))
        Reservations.selectAll()
            .where { listCondition(userId, filter) }
            .orderBy(Reservations.date to SortOrder.DESC, daypartRank to SortOrder.DESC, Reservations.id to SortOrder.DESC)
            .limit(filter.limit).offset(filter.offset.toLong())
            .map { it.toReservation() }
    }

    // The number of reservations behind a list, ignoring the paging.
    fun count(userId: Int, filter: ReservationFilter): Int = transaction(db) {
        Reservations.selectAll().where { listCondition(userId, filter) }.count().toInt()
    }

    private fun listCondition(userId: Int, filter: ReservationFilter): Op<Boolean> =
        (Reservations.userId eq userId) and (filter.status?.let { Reservations.status eq it.name } ?: Op.TRUE)

    // One query for all cars, so searching free cars doesn't ask per car.
    fun reservedCarIds(date: LocalDate, daypart: Daypart): Set<Int> = transaction(db) {
        Reservations.select(Reservations.carId)
            .where { (Reservations.date eq date) and (Reservations.daypart eq daypart.name) and (Reservations.status neq ReservationStatus.CANCELLED.name) }
            .map { it[Reservations.carId] }.toSet()
    }

    fun reservedDayparts(carId: Int, date: LocalDate): Set<Daypart> = transaction(db) {
        Reservations.selectAll().where { (Reservations.carId eq carId) and (Reservations.date eq date) and (Reservations.status neq ReservationStatus.CANCELLED.name) }
            .map { Daypart.valueOf(it[Reservations.daypart]) }.toSet()
    }
}
