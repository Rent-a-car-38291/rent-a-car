package com.example.cars

import io.ktor.http.Parameters
import io.ktor.http.parametersOf
import kotlin.test.*

// Unit tests of filterCars itself; FilterCarsRoutesTest covers it through the route.
class CarFilterTest {
    private fun car(type: CarType, plate: String, rate: Double, lat: Double = 0.0, lon: Double = 0.0) = AddCarRequest(
        type, "Test", plate, 100, rate, Location(lat, lon), 4, BodyStyle.Sedan, 2020, 100, Transmission.Manual, "Red",
        fuelType = FuelType.Petrol.takeIf { type == CarType.ICE }, capacity = 50.0, consumption = 5.0,
    ).toCar(1)

    private val cars = listOf(
        car(CarType.BEV, "A", 30.0, 0.0, 1.0),
        car(CarType.ICE, "B", 40.0, 0.0, 0.1),
        car(CarType.FCEV, "C", 50.0, 0.0, 0.5),
    )

    private fun plates(vararg query: Pair<String, String>) =
        filterCars(cars, Parameters.build { query.forEach { (k, v) -> append(k, v) } })?.map { it.licensePlate }

    @Test
    fun NoParametersKeepsEveryCarInOrder() = assertEquals(listOf("A", "B", "C"), plates())

    @Test
    fun NoCarsGivesNoCars() = assertEquals(emptyList(), filterCars(emptyList(), parametersOf("type", "BEV")))

    @Test
    fun TypeMustMatchExactly() {
        assertEquals(listOf("C"), plates("type" to "FCEV"))
        assertNull(plates("type" to "bev"))
        assertNull(plates("type" to ""))
    }

    @Test
    fun MaxRateIncludesTheRateItself() {
        assertEquals(listOf("A", "B"), plates("maxRate" to "40"))
        assertEquals(emptyList(), plates("maxRate" to "29.99"))
        assertEquals(emptyList(), plates("maxRate" to "-1"))
    }

    @Test
    fun MaxRateMustBeAFiniteNumber() {
        listOf("abc", "", "NaN", "Infinity", "-Infinity").forEach { assertNull(plates("maxRate" to it), it) }
    }

    @Test
    fun CoordinatesMustBeInRangeAndComeAsAPair() {
        assertNull(plates("lat" to "91", "lon" to "0"))
        assertNull(plates("lat" to "-90.1", "lon" to "0"))
        assertNull(plates("lat" to "0", "lon" to "180.1"))
        assertNull(plates("lat" to "NaN", "lon" to "0"))
        assertNull(plates("lat" to "0"))
        assertNull(plates("lon" to "0"))
        assertEquals(3, plates("lat" to "90", "lon" to "-180")?.size) // the edges are valid
    }

    @Test
    fun MaxDistanceNeedsACoordinateAndCannotBeNegative() {
        assertNull(plates("maxDistanceKm" to "5"))
        assertNull(plates("lat" to "0", "lon" to "0", "maxDistanceKm" to "-1"))
        assertNull(plates("lat" to "0", "lon" to "0", "maxDistanceKm" to "NaN"))
    }

    @Test
    fun ACoordinateSortsNearestFirst() = assertEquals(listOf("B", "C", "A"), plates("lat" to "0", "lon" to "0"))

    @Test
    fun MaxDistanceCutsOffAndZeroOnlyKeepsCarsAtThePoint() {
        // 0.5 degrees of longitude on the equator is about 55.6 km
        assertEquals(listOf("B"), plates("lat" to "0", "lon" to "0", "maxDistanceKm" to "50"))
        assertEquals(listOf("B", "C"), plates("lat" to "0", "lon" to "0", "maxDistanceKm" to "60"))
        assertEquals(emptyList(), plates("lat" to "0", "lon" to "0", "maxDistanceKm" to "0"))
        assertEquals(listOf("B"), plates("lat" to "0", "lon" to "0.1", "maxDistanceKm" to "0"))
    }

    @Test
    fun DistanceIsMeasuredAcrossTheDateLine() {
        val near = car(CarType.BEV, "W", 10.0, 0.0, -179.9)
        assertEquals(listOf("W"), filterCars(listOf(near), parametersOf(mapOf("lat" to listOf("0"), "lon" to listOf("179.9"), "maxDistanceKm" to listOf("50"))))?.map { it.licensePlate })
    }

    @Test
    fun FiltersCombineAndAnyInvalidOneRejectsTheRequest() {
        assertEquals(listOf("A"), plates("type" to "BEV", "maxRate" to "35"))
        assertEquals(emptyList(), plates("type" to "ICE", "maxRate" to "35"))
        assertNull(plates("type" to "BEV", "maxRate" to "abc"))
    }
}
