package com.example.trips

import com.example.accounts.User
import com.example.reservations.ReservationRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureTrips(reservations: ReservationRepository, trips: TripRepository) {
    routing {
        authenticate {
            post("/reservations/{id}/trip") {
                val user = call.principal<User>()!!
                val id = call.parameters["id"]?.toIntOrNull()
                val reservation = id?.let(reservations::find)
                when {
                    id == null -> call.respond(HttpStatusCode.BadRequest)
                    reservation == null -> call.respond(HttpStatusCode.NotFound)
                    reservation.userId != user.id -> call.respond(HttpStatusCode.Forbidden)
                    else -> {
                        val request = call.receive<TripRequest>() // Ktor answers a missing field or a non-integer with 400
                        if (request.distanceKm !in 0..MAX_TRIP_KM || request.hardAccelerations < 0 || request.hardBrakes < 0) {
                            call.respond(HttpStatusCode.BadRequest)
                            return@post
                        }
                        val score = calculateScore(request.distanceKm, request.hardAccelerations, request.hardBrakes)
                        val points = bonusPoints(request.distanceKm, score)
                        val saved = trips.record(id, user.id, request.distanceKm, score, points)
                        if (saved == null) call.respond(HttpStatusCode.Conflict)
                        else call.respond(HttpStatusCode.Created, TripResponse(saved.first, points, saved.second))
                    }
                }
            }
        }
    }
}
