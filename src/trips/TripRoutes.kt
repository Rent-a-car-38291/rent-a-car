package com.example.trips

import com.example.accounts.User
import com.example.cars.CarRepository
import com.example.cars.canManage
import com.example.organizations.OrganizationRepository
import com.example.reservations.Reservation
import com.example.reservations.ReservationRepository
import com.example.reservations.ZONE
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

fun Application.configureTrips(reservations: ReservationRepository, trips: TripRepository, cars: CarRepository, organizations: OrganizationRepository, clock: Clock = Clock.system(ZONE)) {
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

            get("/accounts/{id}/stats") {
                val id = call.parameters["id"]?.toIntOrNull()
                val stats = id?.let(trips::stats)
                when {
                    id == null -> call.respond(HttpStatusCode.BadRequest)
                    stats == null -> call.respond(HttpStatusCode.NotFound)
                    id != call.principal<User>()!!.id -> call.respond(HttpStatusCode.Forbidden)
                    else -> call.respond(stats)
                }
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
                val reservation = call.ownReservation(reservations) ?: return@post
                // Nothing was driven yet: without this a renter could claim points for any reservation in the future.
                call.recordTrip(trips, reservation.id, endedAt = null, notYet = LocalDate.parse(reservation.date).isAfter(LocalDate.now(clock)))
            }

            post("/reservations/{id}/trip/start") {
                val reservation = call.ownReservation(reservations) ?: return@post
                // A trip is driven on the day of the reservation. Postgres keeps microseconds, so the start is cut to that.
                val startedAt = if (LocalDate.parse(reservation.date) == LocalDate.now(clock)) trips.start(reservation.id, OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS)) else null
                if (startedAt == null) call.respond(HttpStatusCode.Conflict)
                else call.respond(StartTripResponse(reservation.id, startedAt.toInstant().toString()))
            }

            post("/reservations/{id}/trip/end") {
                val reservation = call.ownReservation(reservations) ?: return@post
                call.recordTrip(trips, reservation.id, endedAt = OffsetDateTime.now(clock), notYet = false)
            }
        }
    }
}

// The reservation of the path when it belongs to the logged-in user; otherwise answers 400, 404 or 403 and returns null.
internal suspend fun ApplicationCall.ownReservation(reservations: ReservationRepository): Reservation? {
    val id = parameters["id"]?.toIntOrNull()
    val reservation = id?.let(reservations::find)
    val status = when {
        id == null -> HttpStatusCode.BadRequest
        reservation == null -> HttpStatusCode.NotFound
        reservation.userId != principal<User>()!!.id -> HttpStatusCode.Forbidden
        else -> return reservation
    }
    respond(status)
    return null
}

// Reads the measured numbers and saves the trip with its score and points. 409 when `notYet`, when the reservation
// already has a trip, or (with endedAt) when the trip was never started.
private suspend fun ApplicationCall.recordTrip(trips: TripRepository, reservationId: Int, endedAt: OffsetDateTime?, notYet: Boolean) {
    val request = receive<TripRequest>() // Ktor answers a missing field or a non-integer with 400
    if (request.distanceKm !in 1..MAX_TRIP_KM || request.hardAccelerations < 0 || request.hardBrakes < 0) {
        respond(HttpStatusCode.BadRequest)
        return
    }
    if (notYet) {
        respond(HttpStatusCode.Conflict)
        return
    }
    val score = calculateScore(request.distanceKm, request.hardAccelerations, request.hardBrakes)
    val points = bonusPoints(request.distanceKm, score)
    val saved = trips.record(reservationId, principal<User>()!!.id, request.distanceKm, score, points, endedAt)
    if (saved == null) respond(HttpStatusCode.Conflict)
    else respond(HttpStatusCode.Created, TripResponse(saved.first, points, saved.second))
}
