package com.example.accounts

import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*

private const val MIN_PASSWORD_LENGTH = 8
private val EMAIL_PATTERN = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") // something@domain.tld, nothing stricter

private fun isValid(name: String, email: String) = name.isNotEmpty() && EMAIL_PATTERN.matches(email)

fun Application.configureAccounts(users: UserRepository) {
    routing {
        post("/accounts") {
            val request = call.receive<RegisterRequest>() // Ktor answers unreadable bodies with 400 or 415
            val name = request.name.trim()
            val email = request.email.trim()
            if (!isValid(name, email) || request.password.length < MIN_PASSWORD_LENGTH) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            val user = users.register(name, email, request.password)
            if (user == null) {
                call.respond(HttpStatusCode.Conflict)
                return@post
            }
            call.respond(HttpStatusCode.Created, user.toResponse())
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            val user = users.authenticate(request.email.trim(), request.password)
            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }
            call.respond(mapOf("token" to TokenRepository.issue(user.id)))
        }

        authenticate {
            post("/logout") {
                // Only the token of this request: the user's other logins keep working.
                (call.request.parseAuthorizationHeader() as? HttpAuthHeader.Single)?.let { TokenRepository.revoke(it.blob) }
                call.respond(HttpStatusCode.NoContent)
            }

            put("/accounts/{id}") {
                val id = call.parameters.getOrFail<Int>("id")
                when {
                    users.find(id) == null -> call.respond(HttpStatusCode.NotFound)
                    id != call.principal<User>()?.id -> call.respond(HttpStatusCode.Forbidden)
                    else -> {
                        val request = call.receive<UpdateAccountRequest>()
                        val name = request.name.trim()
                        val email = request.email.trim()
                        if (!isValid(name, email)) {
                            call.respond(HttpStatusCode.BadRequest)
                            return@put
                        }
                        val user = users.update(id, name, email)
                        if (user == null) call.respond(HttpStatusCode.Conflict) else call.respond(user.toResponse())
                    }
                }
            }

            delete("/accounts/{id}") {
                val id = call.parameters.getOrFail<Int>("id")
                val status = when {
                    users.find(id) == null -> HttpStatusCode.NotFound
                    id != call.principal<User>()?.id -> HttpStatusCode.Forbidden
                    else -> {
                        users.delete(id)
                        TokenRepository.revokeAll(id)
                        HttpStatusCode.NoContent
                    }
                }
                call.respond(status)
            }
        }
    }
}
