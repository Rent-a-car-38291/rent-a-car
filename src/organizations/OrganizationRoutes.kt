package com.example.organizations

import com.example.accounts.User
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*

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

            post("/organizations/{id}/invites") {
                val id = call.parameters.getOrFail<Int>("id")
                when {
                    !organizations.exists(id) -> call.respond(HttpStatusCode.NotFound)
                    organizations.findMembership(id, call.principal<User>()!!.id)?.role != Role.ADMIN -> call.respond(HttpStatusCode.Forbidden)
                    else -> call.respond(HttpStatusCode.Created, InviteResponse(organizations.invite(id)))
                }
            }

            post("/invites/{code}/accept") {
                val result = organizations.accept(call.parameters.getOrFail("code"), call.principal<User>()!!.id)
                call.respond(when (result) {
                    AcceptResult.JOINED -> HttpStatusCode.NoContent
                    AcceptResult.UNKNOWN_CODE -> HttpStatusCode.NotFound
                    AcceptResult.ALREADY_MEMBER -> HttpStatusCode.Conflict
                })
            }
        }
    }
}
