package com.example

import com.example.domain.*
import com.example.repository.CarRepository
import com.example.repository.InMemoryCarRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(
    repository: CarRepository = InMemoryCarRepository()
) {
    routing {
        staticResources("static", "static")

        if (developmentMode) {
            swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
        }

        get("/cars") {
            call.respond(repository.allCars())
        }
    }
}