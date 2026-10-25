package com.example.cars
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A car with a combustion engine. Capacity and consumption are in litres of its `fuelType`, priced per litre. */
@Serializable
@SerialName("ICE")
class ICECar (
    override val id: Int,
    override val ownerId: Int?,
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
    val consumptionLitresPer100Km: Double,
    override val purchasePrice: Double = 0.0,
    override val insurancePerYear: Double = 0.0,
    override val roadTaxPerYear: Double = 0.0,
    override val maintenancePerYear: Double = 0.0,
    override val organizationId: Int? = null,
    // A car counts as serviced when it is added, until PUT /cars/{id}/service says otherwise.
    override var lastServiceKm: Int = mileage,
) : Car() {
    override fun costPerKm(prices: EnergyPrices) = consumptionLitresPer100Km / 100 * prices.perLitre(fuelType)
}