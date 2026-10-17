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

fun Application.configureCars(repository: CarRepository) {
    routing {
        get("/cars") {
            call.respond(repository.allCars())
        }

        authenticate {
            post("/cars") {
                val user = call.principal<User>()!!
                val request = call.receive<AddCarRequest>()
                val car = request.toCar(ownerId = user.id)
                val created: Car = repository.addCar(car)
                call.respond(HttpStatusCode.Created, message = created)
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
                        repository.updateCar(car)?.let { call.respond(it) } ?: call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }
}
