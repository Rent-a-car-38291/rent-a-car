package com.example.cli.areas

import com.example.cli.*
import kotlinx.serialization.json.*

val costsMenu = Menu("Costs", needsLogin = false, items = listOf(
    MenuItem("Energy cost per km of a car", needsLogin = false) { ctx ->
        val id = ctx.pickCar() ?: return@MenuItem
        ctx.call("GET", "/cars/$id/cost-per-km")?.jsonObject?.let { ctx.io.say("  EUR ${it.str("costPerKm")} per km") }
    },
    MenuItem("Quote a trip before reserving", needsLogin = false) { ctx ->
        val id = ctx.pickCar() ?: return@MenuItem
        val km = ctx.io.double("Distance in km", 100.0, min = 1.0)
        ctx.call("GET", "/cars/$id/quote", mapOf("distanceKm" to km.toString()), hints = mapOf(400 to "The distance must be between 1 and 10000 km."))
            ?.let { ctx.io.show(it.jsonObject, listOf("distanceKm", "energyCost", "rentalRate", "total", "currency")) }
    },
    MenuItem("Compare car types: cost and CO2 for a distance", needsLogin = false) { ctx ->
        val km = ctx.io.double("Distance in km", 100.0, min = 1.0)
        ctx.call("GET", "/cars/compare", mapOf("distanceKm" to km.toString()), hints = mapOf(400 to "The distance must be between 1 and 10000 km."))
            ?.jsonObject?.get("types")?.let { ctx.io.table(it.jsonArray, listOf("type", "cars", "averageCost", "co2Kg")) }
    },
    MenuItem("Total cost of ownership per year (my car)") { ctx ->
        val id = ctx.pickCar() ?: return@MenuItem
        val km = ctx.io.int("Km per year", 15000, min = 0)
        ctx.call("GET", "/cars/$id/tco", mapOf("kmPerYear" to km.toString()), hints = mapOf(403 to "Only the owner (or an organization member) can see the TCO of a car."))
            ?.let { ctx.io.show(it.jsonObject, listOf("kmPerYear", "tcoPerYear", "currency")) }
    },
))
