package com.example.accounts

import io.ktor.server.application.*
import io.ktor.server.auth.*

// Routes inside `authenticate { }` need an `Authorization: Bearer <token>` header from POST /login.
// The logged-in user is then available as `call.principal<User>()`.
fun Application.configureSecurity(users: UserRepository, tokens: TokenRepository) {
    install(Authentication) {
        bearer {
            authenticate { credential -> tokens.userId(credential.token)?.let(users::find) }
        }
    }
}
