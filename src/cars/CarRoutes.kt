package com.example.cars

import com.example.accounts.User
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.getOrFail
import java.math.BigDecimal
import java.math.RoundingMode

private fun Double.roundTo(decimals: Int) = BigDecimal.valueOf(this).setScale(decimals, RoundingMode.HALF_UP).toDouble()

fun Application.configureCars(repository: CarRepository) {
    routing {
        get("/cars") {
            val cars = filterCars(repository.allCars(), call.request.queryParameters)
            if (cars == null) call.respond(HttpStatusCode.BadRequest) else call.respond(cars)
        }

        get("/cars/{id}/cost-per-km") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            val car = repository.findCar(id)
            if (car == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(CostPerKmResponse(id, car.costPerKm(EnergyPrices.STANDARD).roundTo(4), "EUR"))
        }

        authenticate {
            post("/cars") {
                val user = call.principal<User>()!!
                val request = call.receive<AddCarRequest>()
                // toCar rejects invalid or missing fields with an IllegalArgumentException.
                val car = runCatching { request.toCar(ownerId = user.id) }.getOrNull()
                if (car == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                try {
                    call.respond(HttpStatusCode.Created, message = repository.addCar(car))
                } catch (e: LicensePlateTakenException) {
                    call.respond(HttpStatusCode.Conflict)
                }
            }

            put("/cars/{id}") {
                val id = call.parameters.getOrFail<Int>("id")
                val existing = repository.findCar(id)
                when {
                    existing == null -> call.respond(HttpStatusCode.NotFound)
                    existing.ownerId != call.principal<User>()!!.id -> call.respond(HttpStatusCode.Forbidden)
                    else -> {
                        val request = call.receive<AddCarRequest>()
                        // toCar rejects invalid fields with an IllegalArgumentException.
                        val car = runCatching { request.toCar(existing.ownerId, id) }.getOrNull()
                        if (car == null || request.type != existing.type) {
                            call.respond(HttpStatusCode.BadRequest)
                            return@put
                        }
                        car.status = existing.status // the request has no status
                        // null when the car was deleted meanwhile
                        try {
                            repository.updateCar(car)?.let { call.respond(it) } ?: call.respond(HttpStatusCode.NotFound)
                        } catch (e: LicensePlateTakenException) {
                            call.respond(HttpStatusCode.Conflict)
                        }
                    }
                }
            }

            get("/cars/{id}") {
                val id = call.parameters["id"]

                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, "ID is missing")
                    return@get
                }

                // Check if ID is numeric value
                val carId = id.toIntOrNull()
                if (carId == null) {
                    call.respond(HttpStatusCode.BadRequest, "ID must be of numeric value")
                    return@get
                }

                // Get car from database
                val car = repository.findCar(carId)
                if (car == null) {
                    call.respond(HttpStatusCode.NotFound, "Car with ID $id not found")
                    return@get
                }

                // Return JSON response
                call.respond(car)
            }

            delete("/cars/{id}") {
                val user = call.principal<User>()!!
                val id = call.parameters["id"]

                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, "ID is missing")
                    return@delete
                }

                val carId = id.toIntOrNull()
                if (carId == null) {
                    call.respond(HttpStatusCode.BadRequest, "ID must be of numeric value")
                    return@delete
                }

                if (repository.findCar(carId) != null) {
                    val delete = repository.deleteCarById(carId, user.id)

                    if (delete) {
                        call.respond(HttpStatusCode.NoContent)
                    }
                    else {
                        call.respond(HttpStatusCode.Forbidden)
                    }
                }
                else {
                    call.respond(HttpStatusCode.NotFound, "Car with ID $id not found")
                }
            }
        }
    }
}
