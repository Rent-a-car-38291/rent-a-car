package com.example.domain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("ICE")
class ICECar (
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
    val fuelType: FuelType,
    val tankCapacityLitres: Double,
    val consumptionLitresPer100Km: Double
) : Car()