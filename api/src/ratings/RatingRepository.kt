package com.example.ratings

import com.example.reservations.Reservation
import com.example.reservations.Reservations
import com.example.trips.Trips
import com.example.trips.createTrips
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.castTo
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import javax.sql.DataSource

private const val FOREIGN_KEY_VIOLATION = "23503"

// A reservation is rated at most once, the unique index decides. The car comes from the reservation, so deleting the car,
// the renter or the reservation takes the rating along.
internal object Ratings : Table("ratings") {
    val id = integer("id").autoIncrement()
    val reservationId = integer("reservation_id").references(Reservations.id, onDelete = ReferenceOption.CASCADE).uniqueIndex("ratings_reservation_id_key")
    val stars = integer("stars").check { (it greaterEq 1) and (it lessEq 5) }
    val comment = text("comment").nullable()
    override val primaryKey = PrimaryKey(id)
}

class RatingRepository(dataSource: DataSource) {
    private val db = Database.connect(dataSource)

    init {
        db.createTrips() // ratings reference reservations, and a rating needs a trip
        transaction(db) { SchemaUtils.create(Ratings) }
    }

    // The check for a finished trip and the insert share a transaction. The unique index decides a second rating, so two requests at once can't both win.
    // shortcut: a reservation deleted just before the insert also gives NoTrip, so the route answers 409 instead of 404.
    fun rate(reservation: Reservation, stars: Int, comment: String?): RateResult = try {
        transaction(db) {
            if (Trips.selectAll().where { Trips.reservationId eq reservation.id }.empty()) return@transaction RateResult.NoTrip
            val inserted = Ratings.insertIgnore {
                it[reservationId] = reservation.id
                it[Ratings.stars] = stars
                it[Ratings.comment] = comment
            }
            if (inserted.insertedCount == 0) RateResult.AlreadyRated
            else RateResult.Rated(Rating(inserted[Ratings.id], reservation.id, reservation.carId, stars, comment))
        }
    } catch (e: ExposedSQLException) {
        if (e.sqlState == FOREIGN_KEY_VIOLATION) RateResult.NoTrip else throw e
    }

    // Newest first. shortcut: all ratings of a car in one list, add paging (like GET /trips) when a car has hundreds.
    fun forCar(carId: Int): List<Rating> = transaction(db) {
        (Ratings innerJoin Reservations).selectAll()
            .where { Reservations.carId eq carId }
            .orderBy(Ratings.id to SortOrder.DESC)
            .map { Rating(it[Ratings.id], it[Ratings.reservationId], carId, it[Ratings.stars], it[Ratings.comment]) }
    }

    // The average rating per car in one query, for the cars that have ratings. With a carId only that car.
    fun averages(carId: Int? = null): Map<Int, Double> = transaction(db) {
        val count = Ratings.id.count()
        val starsSum = Ratings.stars.castTo(LongColumnType()).sum()
        (Ratings innerJoin Reservations).select(Reservations.carId, count, starsSum)
            .where { carId?.let { Reservations.carId eq it } ?: Op.TRUE }
            .groupBy(Reservations.carId)
            .associate { it[Reservations.carId] to averageRating(it[starsSum]!!.toLong(), it[count].toInt())!! }
    }
}
