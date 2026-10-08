package com.example

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        staticResources("static", "static")

        if (developmentMode) {
            swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
        }
    }
}
