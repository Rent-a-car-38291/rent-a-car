package com.example

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.example.domain.Vehicle
import io.ktor.http.HttpStatusCode

fun Application.configureRouting() {
    routing {

        val cars = listOf(
            Vehicle("BMW", "KL-281-S", 103459),
            Vehicle("Audi", "S-298-DH", 93847),
            Vehicle("Peugeot", "85-GHB-2", 216729)
        )

        cars[2].updateMileage(1000)

        get("/") {
            call.respondText("OK")
        }

        get("/vehicles") {
            call.respond(cars)
        }

        get("/vehicles/{id}") {
            val id = call.parameters["id"]

            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val idInt = id.toInt()

            call.respond(cars[idInt])
        }
    }
}