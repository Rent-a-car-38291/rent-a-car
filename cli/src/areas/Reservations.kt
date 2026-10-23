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
    MenuItem("My reservations") { ctx ->
        val io = ctx.io
        io.say("Leave the status empty to see all of them.")
        val status = io.optionalText("Status (RESERVED, COMPLETED or CANCELLED)")?.uppercase()
        ctx.call("GET", "/reservations", mapOf("status" to status), hints = mapOf(400 to "The status must be RESERVED, COMPLETED or CANCELLED."))
            ?.let { io.table(it.jsonArray, listOf("id", "carId", "date", "daypart", "status")) }
    },
    MenuItem("Cancel a reservation") { ctx ->
        val id = ctx.pickReservation()
        ctx.call("DELETE", "/reservations/$id", hints = mapOf(409 to "It cannot be cancelled: the daypart has started, there is a trip, or it is already completed or cancelled."))?.let {
            ctx.session.reservations.removeAll { r -> r.str("id") == id.toString() }
            ctx.io.say("Reservation #$id cancelled.")
        }
    },
))
