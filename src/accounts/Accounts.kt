package com.example.accounts

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*

private const val MIN_PASSWORD_LENGTH = 8
private val EMAIL_PATTERN = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") // something@domain.tld, nothing stricter

fun Application.configureAccounts() {
    routing {
        post("/accounts") {
            val request = call.receive<RegisterRequest>() // Ktor answers unreadable bodies with 400 or 415
            val name = request.name.trim()
            val email = request.email.trim()
            if (name.isEmpty() || !EMAIL_PATTERN.matches(email) || request.password.length < MIN_PASSWORD_LENGTH) {
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

        post("/login") {
            val request = call.receive<LoginRequest>()
            val user = UserRepository.authenticate(request.email.trim(), request.password)
            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }
            call.respond(mapOf("token" to TokenRepository.issue(user.id)))
        }

        authenticate {
            delete("/accounts/{id}") {
                val id = call.parameters.getOrFail<Int>("id")
                val status = when {
                    UserRepository.find(id) == null -> HttpStatusCode.NotFound
                    id != call.principal<User>()?.id -> HttpStatusCode.Forbidden
                    else -> {
                        UserRepository.delete(id)
                        HttpStatusCode.NoContent
                    }
                }
                call.respond(status)
            }
        }
    }
}
