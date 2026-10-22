package com.example.cars
import kotlinx.serialization.Serializable

const val DEPRECIATION_YEARS = 10 // ponytail: one fixed period for every car, derive it from modelYear when that matters
const val STANDARD_KM_PER_YEAR = 15_000
const val MAX_AMOUNT = 99_999_999.99 // the money columns are numeric(10, 2)

@Serializable
sealed class Car {
    abstract val id: Int
    // The owner of a private car; null for an organization car, which no single user owns.
    abstract val ownerId: Int?
    // The organization that owns the car, null for a private car.
    abstract val organizationId: Int?
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

    abstract val purchasePrice: Double
    abstract val insurancePerYear: Double
    abstract val roadTaxPerYear: Double
    abstract val maintenancePerYear: Double

    // Total cost of ownership per year in EUR: depreciation (the purchase price spread over DEPRECIATION_YEARS),
    // insurance, road tax, maintenance and the energy for kmPerYear.
    fun tcoPerYear(kmPerYear: Int, prices: EnergyPrices) =
        purchasePrice / DEPRECIATION_YEARS + insurancePerYear + roadTaxPerYear + maintenancePerYear + costPerKm(prices) * kmPerYear

    // Energy cost of one km in EUR; consumption is per 100 km.
    abstract fun costPerKm(prices: EnergyPrices): Double
}

@Serializable
data class CostPerKmResponse(val carId: Int, val costPerKm: Double, val currency: String)

@Serializable
data class TcoResponse(val carId: Int, val kmPerYear: Int, val tcoPerYear: Double, val currency: String)

enum class CarType { BEV, ICE, FCEV }

val Car.type: CarType get() = when (this) {
    is BEVCar -> CarType.BEV
    is ICECar -> CarType.ICE
    is FCEVCar -> CarType.FCEV
}

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
    // optional: 0 for a new car, and an update that leaves one out keeps the car's current value
    val purchasePrice: Double? = null,
    val insurancePerYear: Double? = null,
    val roadTaxPerYear: Double? = null,
    val maintenancePerYear: Double? = null,
    // POST /cars only: the car then belongs to that organization instead of to you
    val organizationId: Int? = null,
) {
    fun toCar(ownerId: Int?, id: Int = 0, existing: Car? = null, organizationId: Int? = null): Car {
        val purchasePrice = purchasePrice ?: existing?.purchasePrice ?: 0.0
        val insurancePerYear = insurancePerYear ?: existing?.insurancePerYear ?: 0.0
        val roadTaxPerYear = roadTaxPerYear ?: existing?.roadTaxPerYear ?: 0.0
        val maintenancePerYear = maintenancePerYear ?: existing?.maintenancePerYear ?: 0.0
        require(mileage >= 0) { "mileage must be >= 0" }
        require(listOf(purchasePrice, insurancePerYear, roadTaxPerYear, maintenancePerYear).all { it in 0.0..MAX_AMOUNT }) { "amounts must be between 0 and $MAX_AMOUNT" }
        require(rate in 0.0..999_999.99) { "rate must be between 0 and 999999.99" } // the column is numeric(8, 2)
        val capacity = requireNotNull(capacity) { "capacity is required" }
        val consumption = requireNotNull(consumption) { "consumption is required" }
        return when (type) {
            CarType.BEV -> BEVCar(
                id = id,
                ownerId = ownerId,
                organizationId = organizationId,
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
                purchasePrice = purchasePrice,
                insurancePerYear = insurancePerYear,
                roadTaxPerYear = roadTaxPerYear,
                maintenancePerYear = maintenancePerYear,
            )
            CarType.ICE -> {
                val fuel = requireNotNull(fuelType) { "fuelType is required for ICE" }
                ICECar(
                    id = id,
                    ownerId = ownerId,
                    organizationId = organizationId,
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
                    consumptionLitresPer100Km = consumption,
                    purchasePrice = purchasePrice,
                    insurancePerYear = insurancePerYear,
                    roadTaxPerYear = roadTaxPerYear,
                    maintenancePerYear = maintenancePerYear,
                )
            }
            CarType.FCEV -> FCEVCar(
                id = id,
                ownerId = ownerId,
                organizationId = organizationId,
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
                consumptionKgPer100Km = consumption,
                purchasePrice = purchasePrice,
                insurancePerYear = insurancePerYear,
                roadTaxPerYear = roadTaxPerYear,
                maintenancePerYear = maintenancePerYear,
            )
        }
    }
}