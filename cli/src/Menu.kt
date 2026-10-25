package com.example.cli

import kotlinx.serialization.json.*

/** What the client remembers during one run. The token itself lives in [Api]. */
class Session {
    var loggedIn = false
    // The server has no "who am I", so the id comes from registering (or is asked once).
    var accountId: Int? = null
    // The server cannot list your reservations or organizations, so the ones made in this run are kept for the pickers.
    val reservations = mutableListOf<JsonObject>()
    val organizations = mutableListOf<JsonObject>()
}

/** Everything an action needs: the API, the console and the session. */
class Context(val api: Api, val io: Console, val session: Session = Session()) {
    /**
     * Calls the API and returns the JSON answer (an empty object for "204 No Content"), or null after
     * showing a friendly message when the call failed. [hints] replace the standard text for a status code.
     */
    fun call(
        method: String,
        path: String,
        query: Map<String, String?> = emptyMap(),
        body: JsonElement? = null,
        hints: Map<Int, String> = emptyMap(),
    ): JsonElement? {
        val response = try {
            api.call(method, path, query, body)
        } catch (e: ServerUnreachable) {
            io.say("! ${e.message}")
            return null
        }
        if (!response.ok) {
            // A 403 on an account route usually means a mistyped account id: ask again next time.
            if (response.status == 403 && path.startsWith("/accounts/")) session.accountId = null
            io.say("! ${errorMessage(response.status, hints)}")
            return null
        }
        return response.body ?: JsonObject(emptyMap())
    }

    /** The account id, asked once when this run did not register the account. */
    fun accountId(): Int = session.accountId ?: io.int("Your account id (shown when you registered)", min = 1).also { session.accountId = it }

    fun logout() {
        api.token = null
        session.loggedIn = false
        session.accountId = null
        session.reservations.clear()
        session.organizations.clear()
    }

    /** Lists the cars from the server and lets the user pick one by number. Returns the car's id. */
    fun pickCar(query: Map<String, String?> = emptyMap()): Int? {
        val cars = call("GET", "/cars", query)?.jsonArray ?: return null
        if (cars.isEmpty()) {
            io.say("  No cars found.")
            return null
        }
        return io.choose("Which car?", cars.map { it.jsonObject }, { c ->
            "#${c.str("id")} ${c.str("brand")} (${c.str("type")}, ${c.str("licensePlate")}, EUR ${c.str("rate")})"
        }).str("id").toInt()
    }

    /** Picks one of the known items (made in this run) by number, or lets the user type an id. */
    fun pickKnownId(what: String, known: List<JsonObject>, describe: (JsonObject) -> String): Int {
        if (known.isEmpty()) return io.int("Id of the $what", min = 1)
        val typed = "Another $what (type its id)"
        val options = known.map { describe(it) } + typed
        val choice = io.choose("Which $what?", options)
        return if (choice == typed) io.int("Id of the $what", min = 1) else known[options.indexOf(choice)].str("id").toInt()
    }

    fun pickReservation(): Int = pickKnownId("reservation", session.reservations) {
        "#${it.str("id")} car ${it.str("carId")} on ${it.str("date")} ${it.str("daypart")}"
    }

    fun pickOrganization(): Int = pickKnownId("organization", session.organizations) { "#${it.str("id")} ${it.str("name")}" }
}

fun JsonObject.str(key: String): String = this[key]?.let { display(it) } ?: "-"

/** One line in a menu. Items that need a login are hidden for visitors. */
class MenuItem(val label: String, val needsLogin: Boolean = true, val action: (Context) -> Unit)

/** A numbered list of items; a menu is itself an item of its parent, see [asItem]. */
class Menu(val title: String, val items: List<MenuItem>, val needsLogin: Boolean = true) {
    fun asItem() = MenuItem(title, needsLogin) { run(it) }

    /** Shows the menu until the user picks 0 (back, or the [exitLabel]) or the input ends. */
    fun run(ctx: Context, exitLabel: String = "Back") {
        val wasLoggedIn = ctx.session.loggedIn
        while (true) {
            val visible = items.filter { !it.needsLogin || ctx.session.loggedIn }
            ctx.io.say()
            ctx.io.say("== $title ==")
            visible.forEachIndexed { i, item -> ctx.io.say("  ${i + 1}) ${item.label}") }
            ctx.io.say("  0) $exitLabel")
            val answer = try {
                ctx.io.readLine("Choose:")
            } catch (_: BackException) {
                return
            }
            val index = answer.toIntOrNull()
            when {
                index == 0 -> return
                index != null && index in 1..visible.size -> try {
                    visible[index - 1].action(ctx)
                } catch (_: BackException) {
                    ctx.io.say("  (cancelled)")
                }
                else -> ctx.io.say("  Please choose a number from the list.")
            }
            // Logging out (deleting the account) inside a sub menu closes every menu that was opened while logged in.
            if (wasLoggedIn && !ctx.session.loggedIn) return
        }
    }
}
