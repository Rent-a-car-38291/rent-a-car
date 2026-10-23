package com.example.cli.areas

import com.example.cli.*
import kotlinx.serialization.json.*
import java.time.LocalDate

val reservationsMenu = Menu("Reservations", items = listOf(
    MenuItem("Reserve a car") { ctx ->
        val id = ctx.pickCar() ?: return@MenuItem
        val date = ctx.io.date("Date", today())
        val daypart = ctx.io.daypart()
        val body = buildJsonObject { put("date", date.toString()); put("daypart", daypart) }
        ctx.call("POST", "/cars/$id/reservations", body = body, hints = mapOf(
            400 to "The date cannot be in the past.",
            409 to "That car is not free then (already reserved, or in maintenance). Check the availability.",
        ))?.jsonObject?.let {
            ctx.session.reservations += it
            ctx.io.say("Reserved: reservation #${it.str("id")} for car $id on $date, $daypart.")
        }
    },
    MenuItem("My reservations of this session") { ctx ->
        // shortcut: the API cannot list your reservations, so only the ones made in this run are shown.
        ctx.io.table(JsonArray(ctx.session.reservations), listOf("id", "carId", "date", "daypart"))
    },
    MenuItem("Cancel a reservation") { ctx ->
        val id = ctx.pickReservation()
        ctx.call("DELETE", "/reservations/$id", hints = mapOf(409 to "It is too late to cancel: the daypart has started or there is a trip."))?.let {
            ctx.session.reservations.removeAll { r -> r.str("id") == id.toString() }
            ctx.io.say("Reservation #$id cancelled.")
        }
    },
))
