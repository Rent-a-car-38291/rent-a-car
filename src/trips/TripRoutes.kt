package com.example.trips

import com.example.accounts.User
import com.example.cars.CarRepository
import com.example.cars.canManage
import com.example.organizations.OrganizationRepository
import com.example.reservations.ReservationRepository
import com.example.reservations.ZONE
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDate

fun Application.configureTrips(reservations: ReservationRepository, trips: TripRepository, cars: CarRepository, organizations: OrganizationRepository) {
    routing {
        authenticate {
            get("/trips") {
                val filter = parseTripFilter(call.request.queryParameters).getOrElse {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                // Always the logged-in user's own trips, never taken from the request.
                call.respond(trips.history(call.principal<User>()!!.id, null, filter))
            }

            get("/cars/{id}/trips") {
                val id = call.parameters["id"]?.toIntOrNull()
                val car = id?.let(cars::findCar)
                when {
                    id == null -> call.respond(HttpStatusCode.BadRequest)
                    car == null -> call.respond(HttpStatusCode.NotFound)
                    !canManage(call.principal<User>()!!, car, organizations) -> call.respond(HttpStatusCode.Forbidden)
                    else -> {
                        val filter = parseTripFilter(call.request.queryParameters).getOrElse {
                            call.respond(HttpStatusCode.BadRequest)
                            return@get
                        }
                        call.respond(trips.history(null, id, filter))
                    }
                }
            }

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
                        if (request.distanceKm !in 1..MAX_TRIP_KM || request.hardAccelerations < 0 || request.hardBrakes < 0) {
                            call.respond(HttpStatusCode.BadRequest)
                            return@post
                        }
                        // Nothing was driven yet: without this a renter could claim points for any reservation in the future.
                        if (LocalDate.parse(reservation.date).isAfter(LocalDate.now(ZONE))) {
                            call.respond(HttpStatusCode.Conflict)
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
