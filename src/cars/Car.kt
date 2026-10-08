package com.example.cars
import kotlinx.serialization.Serializable

@Serializable
sealed class Car {
    abstract val id: Int
    abstract val brand: String
    abstract val licensePlate: String
    abstract var mileage: Int
    abstract var status: CarStatus
    abstract var rate: Double
    abstract var location: Location
    abstract val doors: Int
    abstract val bodyStyle: BodyStyle
    abstract val modelYear: Int // LocalDate???
    abstract val power: Int
    abstract val transmission: Transmission
    abstract val color: String
}