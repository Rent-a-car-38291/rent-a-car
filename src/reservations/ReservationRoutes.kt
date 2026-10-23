package com.example.reservations

import com.example.accounts.User
import com.example.cars.CarRepository
import com.example.cars.CarStatus
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Clock
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// "Today" for the past-date check; shortcut: one fixed zone, make it a setting when cars outside the Netherlands are rented.
internal val ZONE = ZoneId.of("Europe/Amsterdam")

// The year bound keeps out dates (+999999999-01-01) that parse but that Postgres refuses.
private fun parseDate(text: String?): LocalDate? = try {
    text?.let(LocalDate::parse)?.takeIf { it.year in 2000..2200 }
} catch (e: DateTimeException) {
    null
}

fun Application.configureReservations(cars: CarRepository, reservations: ReservationRepository, clock: Clock = Clock.system(ZONE)) {
    routing {
        // The free dayparts of a date; none when the car is in maintenance.
        get("/cars/{id}/availability") {
            val id = call.parameters["id"]?.toIntOrNull()
            val date = parseDate(call.request.queryParameters["date"])
            val car = id?.let(cars::findCar)
            when {
                id == null || date == null -> call.respond(HttpStatusCode.BadRequest)
                car == null -> call.respond(HttpStatusCode.NotFound)
                else -> {
                    val free = if (car.status == CarStatus.Maintenance) emptyList() else Daypart.entries - reservations.reservedDayparts(id, date)
                    call.respond(AvailabilityResponse(id, date.toString(), free))
                }
            }
        }

        authenticate {
            post("/cars/{id}/reservations") {
                val user = call.principal<User>()!!
                val id = call.parameters["id"]?.toIntOrNull()
                val request = call.receive<ReserveRequest>()
                val date = parseDate(request.date)
                val daypart = Daypart.entries.find { it.name == request.daypart }
                val car = id?.let(cars::findCar)
                when {
                    id == null || date == null || daypart == null || date.isBefore(LocalDate.now(ZONE)) -> call.respond(HttpStatusCode.BadRequest)
                    car == null -> call.respond(HttpStatusCode.NotFound)
                    car.status == CarStatus.Maintenance -> call.respond(HttpStatusCode.Conflict)
                    else -> {
                        val reservation = reservations.reserve(id, user.id, date, daypart)
                        if (reservation == null) call.respond(HttpStatusCode.Conflict) else call.respond(HttpStatusCode.Created, reservation)
                    }
                }
            }

            delete("/reservations/{id}") {
                val user = call.principal<User>()!!
                val id = call.parameters["id"]?.toIntOrNull()
                val reservation = id?.let(reservations::find)
                when {
                    id == null -> call.respond(HttpStatusCode.BadRequest)
                    reservation == null -> call.respond(HttpStatusCode.NotFound)
                    reservation.userId != user.id -> call.respond(HttpStatusCode.Forbidden)
                    hasStarted(LocalDate.parse(reservation.date), reservation.daypart, LocalDateTime.now(clock)) -> call.respond(HttpStatusCode.Conflict)
                    else -> call.respond(when (reservations.cancel(id)) {
                        CancelResult.CANCELLED -> HttpStatusCode.NoContent
                        CancelResult.GONE -> HttpStatusCode.NotFound
                        CancelResult.HAS_TRIP -> HttpStatusCode.Conflict
                    })
                }
            }
        }
    }
}
