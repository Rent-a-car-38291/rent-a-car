package com.avans.rentacar.cars

/** A car as the API's car list returns it, only the fields the app shows so far. */
data class Car(
    val id: Int,
    val brand: String,
    val rate: Double,
    val averageRating: Double?,
    val photoUrl: String?,
)

// shortcut: fake cars until the API client exists, load them from GET /cars then
val FakeCars = listOf(
    Car(1, "tesla model 3", 35.0, 4.3, null),
    Car(2, "volkswagen id.3", 29.0, 4.1, null),
    Car(3, "toyota yaris", 22.0, 4.6, null),
    Car(4, "kia ev6", 42.0, 4.8, null),
    Car(5, "renault clio", 19.0, 3.9, null),
    Car(6, "hyundai nexo", 48.0, 4.4, null),
    Car(7, "peugeot 208", 21.0, 4.0, null),
    Car(8, "polestar 2", 45.0, 4.7, null),
    Car(9, "skoda octavia", 27.0, 4.2, null),
    Car(10, "fiat 500e", 24.0, 4.5, null),
    Car(11, "bmw i4", 55.0, 4.6, null),
    Car(12, "opel corsa", 20.0, null, null),
    Car(13, "toyota mirai", 50.0, 4.1, null),
    Car(14, "mini cooper se", 33.0, 4.4, null),
)
