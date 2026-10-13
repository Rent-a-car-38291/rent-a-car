package com.example.cars
import kotlinx.serialization.Serializable

@Serializable
sealed class Car {
    abstract val id: Int
    abstract val ownerId: Int
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

enum class CarType { BEV, ICE, FCEV }

@Serializable
data class AddCarRequest(
    val type: CarType,
    val brand: String,
    val licensePlate: String,
    val mileage: Int,
    val rate: Double,
    val location: Location,
    val doors: Int,
    val bodyStyle: BodyStyle,
    val modelYear: Int,
    val power: Int,
    val transmission: Transmission,
    val color: String,
    // type-specific, nullable because not every type needs every field
    val fuelType: FuelType? = null,      // ICE only
    val capacity: Double? = null,
    val consumption: Double? = null,
) {
    fun toCar(ownerId: Int): Car {
        require(mileage >= 0) { "mileage must be >= 0" }
        val capacity = requireNotNull(capacity) { "capacity is required" }
        val consumption = requireNotNull(consumption) { "consumption is required" }
        return when (type) {
            CarType.BEV -> BEVCar(
                id = 0,
                ownerId = ownerId,
                brand = brand,
                licensePlate = licensePlate,
                mileage = mileage,
                status = CarStatus.Available,
                rate = rate,
                location = location,
                doors = doors,
                bodyStyle = bodyStyle,
                modelYear = modelYear,
                power = power,
                transmission = transmission,
                color = color,
                batteryCapacityKwh = capacity,
                consumptionKwhPer100Km = consumption,
            )
            CarType.ICE -> {
                val fuel = requireNotNull(fuelType) { "fuelType is required for ICE" }
                ICECar(
                    id = 0,
                    ownerId = ownerId,
                    brand = brand,
                    licensePlate = licensePlate,
                    mileage = mileage,
                    status = CarStatus.Available,
                    rate = rate,
                    location = location,
                    doors = doors,
                    bodyStyle = bodyStyle,
                    modelYear = modelYear,
                    power = power,
                    transmission = transmission,
                    color = color,
                    fuelType = fuel,
                    tankCapacityLitres = capacity,
                    consumptionLitresPer100Km =  consumption
                )
            }
            CarType.FCEV -> FCEVCar(
                id = 0,
                ownerId = ownerId,
                brand = brand,
                licensePlate = licensePlate,
                mileage = mileage,
                status = CarStatus.Available,
                rate = rate,
                location = location,
                doors = doors,
                bodyStyle = bodyStyle,
                modelYear = modelYear,
                power = power,
                transmission = transmission,
                color = color,
                tankCapacityKg = capacity,
                consumptionKgPer100Km =  consumption
            )
        }
    }
}