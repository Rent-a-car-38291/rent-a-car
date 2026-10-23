package com.example.cars

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode

// kg CO2 per litre of fuel, tank to wheel (burning it in the engine). Typical rounded values for the EU:
// petrol 2.3, diesel 2.7 and LPG 1.5. shortcut: not an official table, swap in one (e.g. the Dutch CO2emissiefactoren list) when exact figures matter.
const val CO2_KG_PER_LITRE_PETROL = 2.3
const val CO2_KG_PER_LITRE_DIESEL = 2.7
const val CO2_KG_PER_LITRE_LPG = 1.5

// Electric and hydrogen cars have no tailpipe emissions. shortcut: well-to-wheel (the power plant, the hydrogen production) is left out.
private const val CO2_KG_PER_KM_TAILPIPE_FREE = 0.0

// kg CO2 for one km: the litres per km times the factor of its fuel.
fun Car.co2KgPerKm(): Double = when (this) {
    is ICECar -> consumptionLitresPer100Km / 100 * when (fuelType) {
        FuelType.Petrol -> CO2_KG_PER_LITRE_PETROL
        FuelType.Diesel -> CO2_KG_PER_LITRE_DIESEL
        FuelType.LPG -> CO2_KG_PER_LITRE_LPG
    }
    is BEVCar, is FCEVCar -> CO2_KG_PER_KM_TAILPIPE_FREE
}

@Serializable
data class TypeComparison(val type: CarType, val cars: Int, val averageCost: Double, val co2Kg: Double)

@Serializable
data class CompareResponse(val distanceKm: Double, val types: List<TypeComparison>, val currency: String)

// Per type the average over its cars of the energy cost and the CO2 for the distance; a type without cars is left out.
// The rental rate is not part of it: this compares what driving the distance costs.
fun compareTypes(cars: List<Car>, distanceKm: Double, prices: EnergyPrices): CompareResponse {
    val types = cars.groupBy { it.type }.map { (type, group) ->
        TypeComparison(
            type, group.size,
            forDistance(group.map { it.costPerKm(prices) }.average(), distanceKm),
            forDistance(group.map { it.co2KgPerKm() }.average(), distanceKm),
        )
    }.sortedBy { it.type }
    return CompareResponse(distanceKm, types, "EUR")
}

// Cut the binary noise of the per-km value before rounding half up to 2 decimals, as the quote does.
private fun forDistance(perKm: Double, distanceKm: Double) =
    BigDecimal.valueOf(perKm).setScale(10, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(distanceKm)).setScale(2, RoundingMode.HALF_UP).toDouble()
