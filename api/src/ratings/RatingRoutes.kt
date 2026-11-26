package com.example.ratings

import com.example.cars.CarRepository
import com.example.reservations.ReservationRepository
import com.example.trips.ownReservation
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRatings(reservations: ReservationRepository, ratings: RatingRepository, cars: CarRepository) {
    routing {
        get("/cars/{id}/ratings") {
            val id = call.parameters["id"]?.toIntOrNull()
            when {
                id == null -> call.respond(HttpStatusCode.BadRequest)
                cars.findCar(id) == null -> call.respond(HttpStatusCode.NotFound)
                else -> {
                    val list = ratings.forCar(id)
                    call.respond(CarRatings(id, averageRating(list.sumOf { it.stars.toLong() }, list.size), list.size, list))
                }
            }
        }

        authenticate {
            post("/reservations/{id}/rating") {
                val reservation = call.ownReservation(reservations) ?: return@post
                val request = call.receive<RatingRequest>().cleaned() // Ktor answers a missing field or a non-integer with 400
                if (request == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                when (val result = ratings.rate(reservation, request.stars, request.comment)) {
                    is RateResult.Rated -> call.respond(HttpStatusCode.Created, result.rating)
                    RateResult.NoTrip, RateResult.AlreadyRated -> call.respond(HttpStatusCode.Conflict)
                }
            }
        }
    }
}
