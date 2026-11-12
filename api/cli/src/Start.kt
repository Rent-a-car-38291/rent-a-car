package com.example.cli

import kotlinx.serialization.json.*

private fun logIn(ctx: Context, email: String, password: String): Boolean {
    val body = buildJsonObject { put("email", email); put("password", password) }
    val token = ctx.call("POST", "/login", body = body, hints = mapOf(401 to "Wrong email or password."))
        ?.jsonObject?.get("token")?.jsonPrimitive?.content ?: return false
    ctx.api.token = token
    ctx.session.loggedIn = true
    return true
}

/** Runs the main menu for a logged-in user and logs out afterwards. */
private fun useAccount(ctx: Context) {
    ctx.io.say("Welcome! You are logged in.")
    mainMenu.run(ctx, exitLabel = "Log out")
    if (ctx.session.loggedIn) {
        ctx.call("POST", "/logout")
        ctx.logout()
        ctx.io.say("Logged out.")
    }
}

val startMenu = Menu("Rent-a-car", needsLogin = false, items = listOf(
    MenuItem("Register", needsLogin = false) { ctx ->
        val name = ctx.io.text("Name")
        val email = ctx.io.text("Email")
        val password = ctx.io.text("Password (8-128 characters)")
        val body = buildJsonObject { put("name", name); put("email", email); put("password", password) }
        val account = ctx.call("POST", "/accounts", body = body, hints = mapOf(
            400 to "Enter a name, a valid email address and a password of 8 to 128 characters.",
            409 to "That email address is already registered. Log in instead.",
        ))?.jsonObject ?: return@MenuItem
        ctx.io.say("Registered! Your account id is ${account.str("id")}.")
        if (logIn(ctx, email, password)) {
            ctx.session.accountId = account.str("id").toInt()
            ctx.session.accountIdVerified = true
            useAccount(ctx)
        }
    },
    MenuItem("Log in", needsLogin = false) { ctx ->
        val email = ctx.io.text("Email")
        val password = ctx.io.text("Password")
        if (logIn(ctx, email, password)) useAccount(ctx)
    },
    MenuItem("Browse as a visitor (no login)", needsLogin = false) { ctx ->
        mainMenu.run(ctx)
    },
))

fun runClient(ctx: Context) {
    ctx.io.say("Rent-a-car console client. Type 'back' at any prompt to cancel the current action.")
    try {
        startMenu.run(ctx, exitLabel = "Quit")
    } catch (_: QuitException) {
        // The input ended: leave like Quit does.
    }
    ctx.io.say("Goodbye!")
}
