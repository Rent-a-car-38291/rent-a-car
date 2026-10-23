package com.example.cars

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode

const val MIN_QUOTE_KM = 1.0
const val MAX_QUOTE_KM = 10_000.0

@Serializable
data class QuoteResponse(val carId: Int, val distanceKm: Double, val energyCost: Double, val rentalRate: Double, val total: Double, val currency: String)

// null when the distance is missing, not a number or outside the range.
fun parseQuoteDistance(value: String?): Double? = value?.toDoubleOrNull()?.takeIf { it in MIN_QUOTE_KM..MAX_QUOTE_KM }

// The parts are rounded first and added after, so energyCost + rentalRate is exactly total.
// rentalRate is the car's stored rate as is: the quote does not scale it by distance or duration.
fun Car.quote(distanceKm: Double, prices: EnergyPrices): QuoteResponse {
    val energyCost = BigDecimal.valueOf(costPerKm(prices)).multiply(BigDecimal.valueOf(distanceKm)).setScale(2, RoundingMode.HALF_UP)
    val rentalRate = BigDecimal.valueOf(rate).setScale(2, RoundingMode.HALF_UP)
    return QuoteResponse(id, distanceKm, energyCost.toDouble(), rentalRate.toDouble(), (energyCost + rentalRate).toDouble(), "EUR")
}
