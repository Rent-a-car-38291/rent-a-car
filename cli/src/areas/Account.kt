package com.example.cli.areas

import com.example.cli.*
import kotlinx.serialization.json.*

val accountMenu = Menu("Account", items = listOf(
    MenuItem("Update my name and email") { ctx ->
        val id = ctx.accountId() // asked first, before the other questions
        val body = buildJsonObject {
            put("name", ctx.io.text("New name"))
            put("email", ctx.io.text("New email"))
        }
        ctx.call("PUT", "/accounts/$id", body = body, hints = mapOf(
            400 to "Enter a name and a valid email address.",
            403 to "That is not your account id.",
            409 to "That email address is already in use.",
        ))?.let { ctx.io.say("Account updated.") }
    },
    MenuItem("Change my password") { ctx ->
        val id = ctx.accountId()
        val body = buildJsonObject {
            put("currentPassword", ctx.io.text("Current password"))
            put("newPassword", ctx.io.text("New password (8-128 characters)"))
        }
        ctx.call("PUT", "/accounts/$id/password", body = body, hints = mapOf(
            400 to "The new password needs 8 to 128 characters, and the current one must be right.",
            403 to "Wrong current password, or that is not your account id.",
        ))?.let { ctx.io.say("Password changed. Your other logins were ended.") }
    },
    MenuItem("Delete my account") { ctx ->
        if (!ctx.io.confirm("Delete your account, your cars and your reservations for good?")) return@MenuItem
        ctx.call("DELETE", "/accounts/${ctx.accountId()}", hints = mapOf(
            403 to "That is not your account id.",
            409 to "You are the last admin of an organization. Make someone else admin first.",
        ))?.let {
            ctx.logout()
            ctx.io.say("Account deleted.")
        }
    },
))
