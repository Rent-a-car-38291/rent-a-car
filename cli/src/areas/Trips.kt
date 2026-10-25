package com.example.cli.areas

import com.example.cli.*
import kotlinx.serialization.json.*

private fun tripBody(io: Console) = buildJsonObject {
    put("distanceKm", io.int("Distance in km", 100, 1, 10000))
    put("hardAccelerations", io.int("Hard accelerations", 0, min = 0))
    put("hardBrakes", io.int("Hard brakes", 0, min = 0))
}

private fun showTrip(ctx: Context, response: JsonElement?) = response?.jsonObject?.let {
    ctx.io.show(it, listOf("trip.distanceKm", "trip.score", "trip.durationMinutes", "pointsAwarded", "bonusPoints"))
}

private val tripHints = mapOf(
    403 to "That is not your reservation.",
    409 to "Not possible now: the reservation has to be on today or earlier, and a trip can be started and ended once.",
)

// A trip can only be started on the day of the reservation itself, unlike recording a finished trip.
private val startHints = tripHints + (409 to "A trip can only be started on the day of the reservation (Dutch date), and only once.")

val tripsMenu = Menu("Trips", items = listOf(
    MenuItem("Start my trip") { ctx ->
        val id = ctx.pickReservation()
        ctx.call("POST", "/reservations/$id/trip/start", hints = startHints)?.jsonObject?.let { ctx.io.say("Trip started at ${localTime(it.str("startedAt"))}. Drive safely, then end it here.") }
    },
    MenuItem("End my trip") { ctx ->
        val id = ctx.pickReservation()
        showTrip(ctx, ctx.call("POST", "/reservations/$id/trip/end", body = tripBody(ctx.io), hints = tripHints + (409 to "Start the trip first, and a trip can only be ended once.")))
    },
    MenuItem("Record a finished trip in one go") { ctx ->
        val id = ctx.pickReservation()
        showTrip(ctx, ctx.call("POST", "/reservations/$id/trip", body = tripBody(ctx.io), hints = tripHints))
    },
    MenuItem("My trip history") { ctx ->
        val io = ctx.io
        io.say("Leave a filter empty to skip it.")
        val from = io.optionalDate("From date")
        val to = io.optionalDate("To date")
        val limit = io.optionalInt("Trips per page (1-100)", 1, 100)
        ctx.callPages("/trips", mapOf("from" to from?.toString(), "to" to to?.toString(), "limit" to limit?.toString()), hints = mapOf(400 to "The dates must be valid and 'from' cannot be after 'to'.")) {
            io.table(it, listOf("date", "daypart", "carId", "distanceKm", "score", "durationMinutes"))
        }
    },
    MenuItem("Trips with a car I manage") { ctx ->
        val id = ctx.pickCar() ?: return@MenuItem
        ctx.callPages("/cars/$id/trips", hints = mapOf(403 to "Only the owner of a car (or an organization member) can see its trips.")) {
            ctx.io.table(it, listOf("date", "daypart", "distanceKm", "score", "durationMinutes"))
        }
    },
    MenuItem("Rate the car after a finished trip") { ctx ->
        val id = ctx.pickReservation()
        val body = buildJsonObject {
            put("stars", ctx.io.int("Stars (1-5)", 5, 1, 5))
            ctx.io.optionalText("Comment")?.let { put("comment", it) }
        }
        ctx.call("POST", "/reservations/$id/rating", body = body, hints = mapOf(
            403 to "That is not your reservation.",
            409 to "Rating is only possible once, after a finished trip.",
        ))?.let { ctx.io.say("Thanks for rating!") }
    },
))
