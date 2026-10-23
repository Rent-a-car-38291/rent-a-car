package com.example.cars

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode

const val MIN_QUOTE_KM = 1.0
const val MAX_QUOTE_KM = 10_000.0

@Serializable
data class QuoteResponse(val carId: Int, val distanceKm: Double, val energyCost: Double, val rentalRate: Double, val total: Double, val currency: String)

// null when the distance is missing, not a number or outside the range.
// Plain decimals only: toDoubleOrNull alone also accepts "5d", "0x1p3" and surrounding spaces.
private val DISTANCE_PATTERN = Regex("\\d+(\\.\\d+)?")

fun parseQuoteDistance(value: String?): Double? =
    value?.takeIf(DISTANCE_PATTERN::matches)?.toDoubleOrNull()?.takeIf { it in MIN_QUOTE_KM..MAX_QUOTE_KM }

// The parts are rounded first and added after, so energyCost + rentalRate is exactly total.
// rentalRate is the car's stored rate as is: the quote does not scale it by distance or duration.
fun Car.quote(distanceKm: Double, prices: EnergyPrices): QuoteResponse {
    // costPerKm is a binary double (0.35 * 0.5 / 100 = 0.0017499999999999998): cut that noise before rounding to cents.
    val energyCost = BigDecimal.valueOf(costPerKm(prices)).setScale(10, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(distanceKm)).setScale(2, RoundingMode.HALF_UP)
    val rentalRate = BigDecimal.valueOf(rate).setScale(2, RoundingMode.HALF_UP)
    return QuoteResponse(id, distanceKm, energyCost.toDouble(), rentalRate.toDouble(), (energyCost + rentalRate).toDouble(), "EUR")
}
