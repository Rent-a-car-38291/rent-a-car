package com.example.cli.areas

import com.example.cli.*
import kotlinx.serialization.json.*

private val carColumns = listOf("id", "type", "brand", "licensePlate", "rate", "status", "averageRating", "location.latitude", "location.longitude")
private val daypartNames = listOf("MORNING", "AFTERNOON", "EVENING")
private val bodyStyles = listOf("Hatchback", "SUV", "Sedan", "Stationwagon", "Cabrio", "Coupe", "MPV", "Other")
private val transmissions = listOf("Manual", "Automatic")
private val fuelTypes = listOf("Petrol", "Diesel", "LPG")
private val sortFields = listOf("rate", "-rate", "range", "-range", "costPerKm", "-costPerKm")

fun Console.daypart(): String = choose("Daypart", daypartNames, default = 1)

val carsMenu = Menu("Cars", needsLogin = false, items = listOf(
    MenuItem("List and search cars", needsLogin = false) { ctx ->
        ctx.io.say("Leave a filter empty to skip it.")
        val io = ctx.io
        val type = io.optionalText("Type (BEV, ICE or FCEV)")?.uppercase()
        val maxRate = io.optionalDouble("Highest rate in EUR")
        val date = io.optionalDate("Free on date")
        val daypart = date?.let { io.daypart() }
        val lat = io.optionalDouble("Your latitude, to sort by distance")
        val lon = lat?.let { io.double("Your longitude") }
        val maxDistance = lat?.let { io.optionalDouble("Furthest distance in km") }
        val bodyStyle = io.optionalOneOf("Body style", bodyStyles)
        val transmission = io.optionalOneOf("Transmission", transmissions)
        val fuelType = io.optionalOneOf("Fuel of a petrol, diesel or LPG car", fuelTypes)
        val minRange = io.optionalDouble("Shortest range in km")
        val sort = io.optionalOneOf("Sort by (a leading - reverses)", sortFields)
        ctx.call("GET", "/cars", mapOf(
            "type" to type, "maxRate" to maxRate?.toString(), "date" to date?.toString(), "daypart" to daypart,
            "lat" to lat?.toString(), "lon" to lon?.toString(), "maxDistanceKm" to maxDistance?.toString(),
            "bodyStyle" to bodyStyle, "transmission" to transmission, "fuelType" to fuelType, "minRange" to minRange?.toString(), "sort" to sort,
        ), hints = mapOf(400 to "The server did not accept the filters. The type is BEV, ICE or FCEV, the date cannot be in the past and the shortest range cannot be negative."))
            ?.let { io.table(it.jsonArray, carColumns) }
    },
    MenuItem("Car details, photos and ratings") { ctx ->
        val id = ctx.pickCar() ?: return@MenuItem
        ctx.call("GET", "/cars/$id")?.jsonObject?.let { car ->
            ctx.io.show(JsonObject(car - "images"))
            ctx.io.say("  photos: " + ((car["images"] as? JsonArray)?.joinToString(", ") { it.jsonPrimitive.content }?.ifEmpty { null } ?: "none"))
        }
        ctx.call("GET", "/cars/$id/ratings")?.jsonObject?.let { r ->
            ctx.io.say("  rating: ${r.str("averageRating")} from ${r.str("ratingCount")} rating(s)")
            (r["ratings"] as? JsonArray)?.let { ctx.io.table(it, listOf("stars", "comment")) }
        }
    },
    MenuItem("Is a car free? (availability on a date)", needsLogin = false) { ctx ->
        val id = ctx.pickCar() ?: return@MenuItem
        val date = ctx.io.date("Date", today())
        ctx.call("GET", "/cars/$id/availability", mapOf("date" to date.toString()))?.jsonObject?.let {
            ctx.io.say("  Free dayparts on $date: " + it.str("freeDayparts"))
        }
    },
    MenuItem("Offer my car") { ctx -> addCar(ctx, organizationId = null) },
    MenuItem("Update a car I manage") { ctx ->
        val id = ctx.pickCar() ?: return@MenuItem
        val current = ctx.call("GET", "/cars/$id")?.jsonObject ?: return@MenuItem
        ctx.io.say("Press Enter to keep the current value.")
        val body = carForm(ctx.io, current)
        ctx.call("PUT", "/cars/$id", body = body, hints = mapOf(
            400 to "The server did not accept the values. The mileage cannot be lower than the mileage at the last service.",
            409 to "Another car already has this license plate.",
        ))?.let {
            ctx.io.say("Car #$id updated.")
        }
    },
    MenuItem("Delete a car I manage") { ctx ->
        val id = ctx.pickCar() ?: return@MenuItem
        if (!ctx.io.confirm("Delete car #$id for good?")) return@MenuItem
        ctx.call("DELETE", "/cars/$id")?.let { ctx.io.say("Car #$id deleted.") }
    },
    MenuItem("Mark a car as serviced (service done today)") { ctx ->
        val id = ctx.pickCar() ?: return@MenuItem
        ctx.call("PUT", "/cars/$id/service")?.let { ctx.io.say("Car #$id is marked as serviced at its current mileage.") }
    },
))

/** Creates a car; with an [organizationId] it belongs to that organization instead of to you. */
fun addCar(ctx: Context, organizationId: Int?) {
    val body = carForm(ctx.io, null)
    val request = if (organizationId == null) body else JsonObject(body + ("organizationId" to JsonPrimitive(organizationId)))
    ctx.call("POST", "/cars", body = request, hints = mapOf(
        409 to "Another car already has this license plate.",
        403 to "You are not a member of that organization.",
    ))?.jsonObject?.let { ctx.io.say("Car #${it.str("id")} added.") }
}

// The capacity and consumption keys differ per car type in the response; the request calls them capacity and consumption.
private val capacityKeys = listOf("batteryCapacityKwh", "tankCapacityLitres", "tankCapacityKg")
private val consumptionKeys = listOf("consumptionKwhPer100Km", "consumptionLitresPer100Km", "consumptionKgPer100Km")

/** Asks every field of a car; with a [current] car the answers default to its values (the type then stays as it is). */
private fun carForm(io: Console, current: JsonObject?): JsonObject {
    fun cur(key: String) = current?.get(key)?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
    fun curAny(keys: List<String>) = keys.firstNotNullOfOrNull { cur(it) }
    val location = current?.get("location") as? JsonObject
    fun pick(title: String, key: String, values: List<String>) =
        io.choose(title, values, default = values.indexOf(cur(key)).let { if (it < 0) 1 else it + 1 })

    val type = current?.str("type") ?: io.choose("Type", listOf("BEV", "ICE", "FCEV"), {
        mapOf("BEV" to "BEV (electric)", "ICE" to "ICE (petrol, diesel or LPG)", "FCEV" to "FCEV (hydrogen)").getValue(it)
    })
    val unit = mapOf("BEV" to "kWh", "ICE" to "litres", "FCEV" to "kg").getValue(type)
    return buildJsonObject {
        put("type", type)
        put("brand", io.text("Brand", cur("brand")))
        put("licensePlate", io.text("License plate", cur("licensePlate")))
        put("mileage", io.int("Mileage in km", cur("mileage")?.toInt() ?: 0, min = 0))
        put("rate", io.double("Rate in EUR", cur("rate")?.toDouble(), min = 0.0))
        putJsonObject("location") {
            put("latitude", io.double("Latitude", location?.get("latitude")?.jsonPrimitive?.double ?: 51.59))
            put("longitude", io.double("Longitude", location?.get("longitude")?.jsonPrimitive?.double ?: 4.78))
        }
        put("doors", io.int("Doors", cur("doors")?.toInt() ?: 5, 1, 9))
        put("bodyStyle", pick("Body style", "bodyStyle", bodyStyles))
        put("modelYear", io.int("Model year", cur("modelYear")?.toInt() ?: 2024, 1900, 2100))
        put("power", io.int("Power in kW", cur("power")?.toInt() ?: 100, min = 1))
        put("transmission", pick("Transmission", "transmission", transmissions))
        put("color", io.text("Color", cur("color")))
        if (type == "ICE") put("fuelType", pick("Fuel", "fuelType", fuelTypes))
        put("capacity", io.double("Tank or battery size in $unit", curAny(capacityKeys)?.toDouble(), min = 0.0))
        put("consumption", io.double("Consumption per 100 km in $unit", curAny(consumptionKeys)?.toDouble(), min = 0.0))
        // Costs are optional: left empty they are 0 for a new car and unchanged on an update.
        for ((key, label) in listOf("purchasePrice" to "Purchase price in EUR", "insurancePerYear" to "Insurance per year in EUR", "roadTaxPerYear" to "Road tax per year in EUR", "maintenancePerYear" to "Maintenance per year in EUR")) {
            if (current != null) put(key, io.double(label, cur(key)?.toDouble() ?: 0.0, min = 0.0))
            else io.optionalDouble(label)?.let { put(key, it) }
        }
    }
}
