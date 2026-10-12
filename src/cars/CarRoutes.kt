package com.example.cars

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureCars(repository: CarRepository) {
    routing {
        get("/cars") {
            call.respond(repository.allCars())
        }
    }
}
