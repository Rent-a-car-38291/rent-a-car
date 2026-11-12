package com.example.cli.areas

import com.example.cli.*
import kotlinx.serialization.json.*

val organizationsMenu = Menu("Organizations", items = listOf(
    MenuItem("Create an organization (I become admin)") { ctx ->
        val body = buildJsonObject { put("name", ctx.io.text("Name")) }
        ctx.call("POST", "/organizations", body = body)?.jsonObject?.let {
            ctx.session.organizations += it
            ctx.io.say("Organization #${it.str("id")} created.")
        }
    },
    MenuItem("Invite someone (admin): create an invite code") { ctx ->
        val id = ctx.pickOrganization()
        ctx.call("POST", "/organizations/$id/invites", hints = mapOf(403 to "Only an admin of the organization can invite."))
            ?.jsonObject?.let { ctx.io.say("Invite code (works once, 7 days): ${it.str("code")}") }
    },
    MenuItem("Join with an invite code") { ctx ->
        val code = ctx.io.text("Invite code")
        ctx.call("POST", "/invites/${java.net.URLEncoder.encode(code, Charsets.UTF_8)}/accept", hints = mapOf(404 to "Unknown, used or expired code.", 409 to "You are already a member."))
            ?.let { ctx.io.say("You joined the organization as a member.") }
    },
    MenuItem("Change a member's role (admin)") { ctx ->
        val id = ctx.pickOrganization()
        val userId = ctx.io.int("Account id of the member", min = 1)
        val role = ctx.io.choose("New role", listOf("ADMIN", "MEMBER"), default = 2)
        ctx.call("PUT", "/organizations/$id/members/$userId", body = buildJsonObject { put("role", role) }, hints = mapOf(
            403 to "Only an admin of the organization can do that.",
            404 to "Unknown organization, or that account is not a member.",
            409 to "The last admin cannot be demoted.",
        ))?.let { ctx.io.say("Account $userId is now $role.") }
    },
    MenuItem("Remove a member, or leave") { ctx ->
        val id = ctx.pickOrganization()
        val userId = ctx.io.int("Account id to remove (yours to leave)", ctx.session.accountId, min = 1)
        ctx.call("DELETE", "/organizations/$id/members/$userId", hints = mapOf(
            403 to "Only an admin can remove other members.",
            404 to "Unknown organization, or that account is not a member.",
            409 to "The last admin cannot leave.",
        ))?.let { ctx.io.say("Account $userId is no longer a member.") }
    },
    MenuItem("Add an organization car") { ctx ->
        addCar(ctx, ctx.pickOrganization())
    },
    MenuItem("Fleet report: usage and TCO per car (members)") { ctx ->
        val id = ctx.pickOrganization()
        ctx.call("GET", "/organizations/$id/report", hints = mapOf(
            403 to "Only members of the organization can see its report.",
            404 to "Unknown organization.",
        ))?.let { ctx.io.table(it.jsonArray, listOf("carId", "brand", "licensePlate", "reservations", "trips", "totalKm", "averageScore", "tcoPerYear")) }
    },
))
