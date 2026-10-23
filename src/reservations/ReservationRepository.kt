package com.example.reservations

import com.example.accounts.Users
import com.example.cars.Cars
import com.example.cars.createCars
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import javax.sql.DataSource

private const val UNIQUE_VIOLATION = "23505"
private const val FOREIGN_KEY_VIOLATION = "23503"

// Deleting the car or the renter takes their reservations along. The unique constraint is what keeps a daypart from being booked twice.
internal object Reservations : Table("reservations") {
    val id = integer("id").autoIncrement()
    val carId = integer("car_id").references(Cars.id, onDelete = ReferenceOption.CASCADE)
    val userId = integer("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val date = date("date")
    val daypart = text("daypart").check { it inList Daypart.entries.map { daypart -> daypart.name } }
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("reservations_car_id_date_daypart_key", carId, date, daypart)
    }
}

private fun ResultRow.toReservation() =
    Reservation(this[Reservations.id], this[Reservations.carId], this[Reservations.userId], this[Reservations.date].toString(), Daypart.valueOf(this[Reservations.daypart]))

// Also used by the trips, which reference reservations.
internal fun Database.createReservations() {
    createCars() // reservations reference cars and users
    transaction(this) { SchemaUtils.create(Reservations) }
}

class ReservationRepository(dataSource: DataSource) {
    private val db = Database.connect(dataSource).also { it.createReservations() }

    fun find(id: Int): Reservation? = transaction(db) { Reservations.selectAll().where { Reservations.id eq id }.singleOrNull()?.toReservation() }

    // null when the daypart is taken: the unique index decides, so two requests at once can't both win.
    // shortcut: a car or user deleted just before the insert also gives null, so the route answers 409 instead of 404 or 401.
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

    fun reservedDayparts(carId: Int, date: LocalDate): Set<Daypart> = transaction(db) {
        Reservations.selectAll().where { (Reservations.carId eq carId) and (Reservations.date eq date) }
            .map { Daypart.valueOf(it[Reservations.daypart]) }.toSet()
    }
}
