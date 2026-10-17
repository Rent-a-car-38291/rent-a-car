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

            put("/organizations/{id}/members/{userId}") {
                val id = call.parameters.getOrFail<Int>("id")
                val userId = call.parameters.getOrFail<Int>("userId")
                when {
                    !organizations.exists(id) -> call.respond(HttpStatusCode.NotFound)
                    organizations.findMembership(id, call.principal<User>()!!.id)?.role != Role.ADMIN -> call.respond(HttpStatusCode.Forbidden)
                    else -> {
                        val role = call.receive<UpdateRoleRequest>().role
                        when (organizations.setRole(id, userId, role)) {
                            ChangeResult.DONE -> call.respond(Membership(userId, id, role))
                            ChangeResult.NOT_A_MEMBER -> call.respond(HttpStatusCode.NotFound)
                            ChangeResult.LAST_ADMIN -> call.respond(HttpStatusCode.Conflict)
                        }
                    }
                }
            }

            delete("/organizations/{id}/members/{userId}") {
                val id = call.parameters.getOrFail<Int>("id")
                val userId = call.parameters.getOrFail<Int>("userId")
                val caller = call.principal<User>()!!.id
                when {
                    !organizations.exists(id) -> call.respond(HttpStatusCode.NotFound)
                    userId != caller && organizations.findMembership(id, caller)?.role != Role.ADMIN -> call.respond(HttpStatusCode.Forbidden)
                    else -> call.respond(when (organizations.remove(id, userId)) {
                        ChangeResult.DONE -> HttpStatusCode.NoContent
                        ChangeResult.NOT_A_MEMBER -> HttpStatusCode.NotFound
                        ChangeResult.LAST_ADMIN -> HttpStatusCode.Conflict
                    })
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
