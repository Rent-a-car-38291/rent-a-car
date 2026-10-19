package com.example.cars

import com.example.accounts.User
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureCars(repository: CarRepository) {
    routing {
        get("/cars") {
            call.respond(repository.allCars())
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
        }
    }
}
