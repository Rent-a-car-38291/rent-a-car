package com.avans.rentacar.demo

/** Fake data for the theme demo. Replaced by `GET /cars` once the API client exists. */
data class FakeCar(
    val brand: String,
    val kind: String,
    val body: String,
    val rate: Int,
    val rangeKm: Int,
    val rating: Double,
    val distanceKm: Int,
)

val Kinds = listOf("electric", "combustion", "hydrogen")
val Dayparts = listOf("morning", "afternoon", "evening")

val FakeCars = listOf(
    FakeCar("tesla model 3", "electric", "sedan", 35, 510, 4.3, 2),
    FakeCar("toyota mirai", "hydrogen", "sedan", 42, 650, 4.7, 5),
    FakeCar("vw golf", "combustion", "hatchback", 24, 780, 4.1, 1),
    FakeCar("kia ev6", "electric", "suv", 39, 480, 4.5, 3),
    FakeCar("hyundai nexo", "hydrogen", "suv", 45, 610, 4.0, 9),
    FakeCar("skoda octavia", "combustion", "stationwagon", 27, 900, 4.4, 4),
    FakeCar("fiat 500e", "electric", "hatchback", 21, 250, 3.9, 2),
    FakeCar("mini cabrio", "combustion", "cabrio", 33, 600, 4.6, 7),
)
