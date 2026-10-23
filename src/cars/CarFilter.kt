package com.example.cars

import io.ktor.http.Parameters
import kotlin.math.*

private const val EARTH_RADIUS_KM = 6371.0

private val Car.carType get() = when (this) {
    is BEVCar -> CarType.BEV
    is ICECar -> CarType.ICE
    is FCEVCar -> CarType.FCEV
}

private fun distanceKm(a: Location, lat: Double, lon: Double): Double {
    val dLat = Math.toRadians(lat - a.latitude)
    val dLon = Math.toRadians(lon - a.longitude)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(lat)) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_KM * asin(sqrt(h))
}

// Km you can drive on a full tank or battery; a car that uses nothing per 100 km has no limit.
val Car.rangeKm: Double get() {
    val (capacity, consumption) = when (this) {
        is BEVCar -> batteryCapacityKwh to consumptionKwhPer100Km
        is ICECar -> tankCapacityLitres to consumptionLitresPer100Km
        is FCEVCar -> tankCapacityKg to consumptionKgPer100Km
    }
    return if (consumption == 0.0) Double.POSITIVE_INFINITY else capacity / consumption * 100
}

private val sortKeys: Map<String, (Car) -> Double> = mapOf(
    "rate" to { it.rate },
    "range" to { it.rangeKm },
    "costPerKm" to { it.costPerKm(EnergyPrices.STANDARD) },
)

private inline fun <reified E : Enum<E>> exact(value: String?): E? = enumValues<E>().find { it.name == value }

// Filters by the type, bodyStyle, transmission, fuelType, minRange, maxRate, lat, lon and maxDistanceKm query parameters
// and sorts by `sort` (rate, range or costPerKm, a leading - for descending); null when one is invalid.
// With a location the result is nearest first and `sort` is only validated.
fun filterCars(cars: List<Car>, query: Parameters): List<Car>? {
    val type = query["type"]?.let { exact<CarType>(it) ?: return null }
    val bodyStyle = query["bodyStyle"]?.let { exact<BodyStyle>(it) ?: return null }
    val transmission = query["transmission"]?.let { exact<Transmission>(it) ?: return null }
    val fuelType = query["fuelType"]?.let { exact<FuelType>(it) ?: return null }
    val minRange = query["minRange"]?.let { it.toDoubleOrNull()?.takeIf { v -> v.isFinite() && v >= 0 } ?: return null }
    val sort = query["sort"]?.let { value -> sortKeys[value.removePrefix("-")]?.let { it to value.startsWith("-") } ?: return null }
    val maxRate = query["maxRate"]?.let { it.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null }
    val lat = query["lat"]?.let { it.toDoubleOrNull()?.takeIf { v -> v in -90.0..90.0 } ?: return null }
    val lon = query["lon"]?.let { it.toDoubleOrNull()?.takeIf { v -> v in -180.0..180.0 } ?: return null }
    val maxDistance = query["maxDistanceKm"]?.let { it.toDoubleOrNull()?.takeIf { v -> v >= 0 } ?: return null }
    if ((lat == null) != (lon == null) || (maxDistance != null && lat == null)) return null

    val matching = cars.filter {
        (type == null || it.carType == type) && (maxRate == null || it.rate <= maxRate) &&
            (bodyStyle == null || it.bodyStyle == bodyStyle) && (transmission == null || it.transmission == transmission) &&
            (fuelType == null || (it is ICECar && it.fuelType == fuelType)) && (minRange == null || it.rangeKm >= minRange)
    }
    if (lat == null || lon == null) {
        if (sort == null) return matching
        val (key, descending) = sort
        return if (descending) matching.sortedByDescending(key) else matching.sortedBy(key) // stable: equal values keep the fleet order
    }
    return matching.map { it to distanceKm(it.location, lat, lon) }
        .filter { maxDistance == null || it.second <= maxDistance }
        .sortedBy { it.second }
        .map { it.first }
}
