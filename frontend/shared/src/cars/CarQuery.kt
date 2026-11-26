package com.avans.rentacar.cars

import kotlinx.datetime.LocalDate

enum class Daypart(val label: String, val hours: String) {
    MORNING("morning", "06:00 to 12:00"),
    AFTERNOON("afternoon", "12:00 to 18:00"),
    EVENING("evening", "18:00 to 24:00"),
}

/** The three kinds of car, with the API's name for each. */
enum class CarKind(val api: String, val label: String) {
    Electric("BEV", "electric"),
    Combustion("ICE", "combustion"),
    Hydrogen("FCEV", "hydrogen"),
}

enum class Sort(val api: String, val label: String) {
    Cheapest("rate", "cheapest"),
    Priciest("-rate", "most expensive"),
    Range("-range", "longest range"),
    CostPerKm("costPerKm", "cheapest per km"),
}

// The API's names; the screen shows them in lower case.
val BodyStyles = listOf("Hatchback", "SUV", "Sedan", "Stationwagon", "Cabrio", "Coupe", "MPV", "Other")
val Transmissions = listOf("Manual", "Automatic")
val FuelTypes = listOf("Petrol", "Diesel", "LPG")

/**
 * What the person is looking for. Without a [date] every car shows; with one only the cars that are
 * free on that date and [daypart] (the API needs both).
 */
data class CarQuery(
    val date: LocalDate? = null,
    val daypart: Daypart = Daypart.MORNING,
    val kind: CarKind? = null,
    val bodyStyle: String? = null,
    val transmission: String? = null,
    val fuelType: String? = null,
    val sort: Sort = Sort.Cheapest,
) {
    val filterCount: Int get() = listOfNotNull(kind, bodyStyle, transmission, fuelType).size

    /** The same search without filters or date; the sort stays. */
    fun cleared() = CarQuery(sort = sort)

    /** Only combustion cars have a fuel type, so another kind drops it. */
    fun withKind(kind: CarKind?) = copy(kind = kind, fuelType = if (kind == CarKind.Combustion) fuelType else null)

    /** The query parameters of GET /cars. */
    fun params(offset: Int, limit: Int): List<Pair<String, String>> = buildList {
        if (date != null) {
            add("date" to date.toString())
            add("daypart" to daypart.name)
        }
        kind?.let { add("type" to it.api) }
        bodyStyle?.let { add("bodyStyle" to it) }
        transmission?.let { add("transmission" to it) }
        fuelType?.let { add("fuelType" to it) }
        add("sort" to sort.api)
        add("limit" to limit.toString())
        add("offset" to offset.toString())
    }
}
