package com.example.domain
import kotlinx.serialization.Serializable

@Serializable
class FCEVCar (
    override val id: Int,
    override val brand: String,
    override val licensePlate: String,
    override var mileage: Int,
    override var status: CarStatus,
    override var rate: Double,
    override var location: Location,
    override val doors: Int,
    override val bodyStyle: BodyStyle,
    override val modelYear: Int, // LocalDate???
    override val power: Int,
    override val transmission: Transmission,
    override val color: String,
    val tankCapacityKg: Double,
    val consumptionKgPer100Km: Double
) : Car()