package com.example.accounts

import io.ktor.server.application.*
import io.ktor.server.auth.*

// Routes inside `authenticate { }` need an `Authorization: Bearer <token>` header from POST /login.
// The logged-in user is then available as `call.principal<User>()`.
fun Application.configureSecurity() {
    install(Authentication) {
        bearer {
            authenticate { credential -> TokenRepository.userId(credential.token)?.let(UserRepository::find) }
        }
    }
}
