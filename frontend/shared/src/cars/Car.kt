package com.avans.rentacar.cars

import kotlinx.serialization.Serializable

/** A car as GET /cars returns it: only the fields the app shows, the API sends more. */
@Serializable
data class Car(
    val id: Int,
    val type: String,
    val brand: String,
    val rate: Double,
    val bodyStyle: String,
    val transmission: String,
    val averageRating: Double? = null,
    val fuelType: String? = null,
    // shortcut: the car list has no photos yet (GET /cars/{id} has them as "images"); show them once the list sends one
    val photoUrl: String? = null,
)

/** "electric", "combustion" or "hydrogen". */
val Car.kindLabel: String get() = CarKind.entries.find { it.api == type }?.label ?: type.lowercase()

/** "electric · sedan · automatic", the line under a car's name. */
val Car.summary: String get() = listOf(kindLabel, bodyStyle.lowercase(), transmission.lowercase()).joinToString(" · ")
