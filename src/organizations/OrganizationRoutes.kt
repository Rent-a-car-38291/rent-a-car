package com.example.organizations

import com.example.accounts.User
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureOrganizations(organizations: OrganizationRepository) {
    routing {
        authenticate {
            post("/organizations") {
                val name = call.receive<CreateOrganizationRequest>().name.trim()
                if (name.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                val user = call.principal<User>()!!
                call.respond(HttpStatusCode.Created, organizations.create(name, user.id))
            }
        }
    }
}
