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

// Filters by the type, maxRate, lat, lon and maxDistanceKm query parameters; null when one is invalid.
// With a distance filter the result is nearest first.
fun filterCars(cars: List<Car>, query: Parameters): List<Car>? {
    val type = query["type"]?.let { value -> CarType.entries.find { it.name == value } ?: return null }
    val maxRate = query["maxRate"]?.let { it.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null }
    val lat = query["lat"]?.let { it.toDoubleOrNull()?.takeIf { v -> v in -90.0..90.0 } ?: return null }
    val lon = query["lon"]?.let { it.toDoubleOrNull()?.takeIf { v -> v in -180.0..180.0 } ?: return null }
    val maxDistance = query["maxDistanceKm"]?.let { it.toDoubleOrNull()?.takeIf { v -> v >= 0 } ?: return null }
    if ((lat == null) != (lon == null) || (maxDistance != null && lat == null)) return null

    val matching = cars.filter { (type == null || it.carType == type) && (maxRate == null || it.rate <= maxRate) }
    if (lat == null || lon == null) return matching
    return matching.map { it to distanceKm(it.location, lat, lon) }
        .filter { maxDistance == null || it.second <= maxDistance }
        .sortedBy { it.second }
        .map { it.first }
}
