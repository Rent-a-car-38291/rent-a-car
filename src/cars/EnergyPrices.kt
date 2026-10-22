package com.example.cars

// Prices in EUR. ponytail: fixed values, load them from config or a price feed when they have to follow the market.
data class EnergyPrices(
    val petrolPerLitre: Double,
    val dieselPerLitre: Double,
    val lpgPerLitre: Double,
    val electricityPerKwh: Double,
    val hydrogenPerKg: Double,
) {
    fun perLitre(fuelType: FuelType) = when (fuelType) {
        FuelType.Petrol -> petrolPerLitre
        FuelType.Diesel -> dieselPerLitre
        FuelType.LPG -> lpgPerLitre
    }

    companion object {
        val STANDARD = EnergyPrices(petrolPerLitre = 2.10, dieselPerLitre = 1.90, lpgPerLitre = 1.00, electricityPerKwh = 0.35, hydrogenPerKg = 12.00)
    }
}
