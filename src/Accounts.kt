package com.example

import com.example.domain.RegisterRequest
import com.example.domain.UserRepository
import com.example.domain.toResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private const val MIN_PASSWORD_LENGTH = 8

fun Application.configureAccounts() {
    routing {
        post("/accounts") {
            val request = try {
                call.receive<RegisterRequest>()
            } catch (e: UnsupportedMediaTypeException) {
                call.respond(HttpStatusCode.UnsupportedMediaType)
                return@post
            } catch (e: BadRequestException) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            val name = request.name.trim()
            val email = request.email.trim()
            if (name.isEmpty() || !email.contains('@') || request.password.length < MIN_PASSWORD_LENGTH) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            val user = UserRepository.register(name, email, request.password)
            if (user == null) {
                call.respond(HttpStatusCode.Conflict)
                return@post
            }
            call.respond(HttpStatusCode.Created, user.toResponse())
        }
    }
}
