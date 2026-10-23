package com.example.trips

import com.example.accounts.Users
import com.example.reservations.Reservations
import com.example.reservations.createReservations
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import javax.sql.DataSource

private const val FOREIGN_KEY_VIOLATION = "23503"

// A reservation has at most one trip, the unique index decides. Deleting the reservation takes its trip along.
internal object Trips : Table("trips") {
    val id = integer("id").autoIncrement()
    val reservationId = integer("reservation_id").references(Reservations.id, onDelete = ReferenceOption.CASCADE).uniqueIndex("trips_reservation_id_key")
    val distanceKm = integer("distance_km")
    val score = integer("score")
    override val primaryKey = PrimaryKey(id)
}

// Also used by the reservations, which look for a trip before they cancel.
internal fun Database.createTrips() {
    createReservations() // trips reference reservations
    transaction(this) { SchemaUtils.create(Trips) }
}

class TripRepository(dataSource: DataSource) {
    private val db = Database.connect(dataSource)

    init {
        db.createTrips()
    }

    // Saves the trip and adds the points to the renter in one transaction, so there are never points without a trip.
    // Returns the trip and the renter's new total, or null when the reservation already has a trip.
    // shortcut: a reservation deleted just before the insert also gives null, so the route answers 409 instead of 404.
    fun record(reservationId: Int, userId: Int, distanceKm: Int, score: Int, points: Int): Pair<Trip, Int>? = try {
        transaction(db) {
            val inserted = Trips.insertIgnore {
                it[Trips.reservationId] = reservationId
                it[Trips.distanceKm] = distanceKm
                it[Trips.score] = score
            }
            if (inserted.insertedCount == 0) {
                null
            } else {
                Users.update({ Users.id eq userId }) { it[bonusPoints] = bonusPoints + points }
                val total = Users.selectAll().where { Users.id eq userId }.single()[Users.bonusPoints]
                Trip(inserted[Trips.id], reservationId, distanceKm, score) to total
            }
        }
    } catch (e: ExposedSQLException) {
        if (e.sqlState == FOREIGN_KEY_VIOLATION) null else throw e
    }
}
