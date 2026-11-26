package com.example.cars

import io.ktor.http.Parameters
import io.ktor.http.parametersOf
import kotlin.test.*

// Unit tests of filterCars itself; FilterCarsRoutesTest covers it through the route.
class CarFilterTest {
    private fun car(
        type: CarType, plate: String, rate: Double, lat: Double = 0.0, lon: Double = 0.0,
        bodyStyle: BodyStyle = BodyStyle.Sedan, transmission: Transmission = Transmission.Manual,
        fuelType: FuelType = FuelType.Petrol, capacity: Double = 50.0, consumption: Double = 5.0,
    ) = AddCarRequest(
        type, "Test", plate, 100, rate, Location(lat, lon), 4, bodyStyle, 2020, 100, transmission, "Red",
        fuelType = fuelType.takeIf { type == CarType.ICE }, capacity = capacity, consumption = consumption,
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

    // Range is capacity / consumption * 100: BEV 400 km, petrol 800 km, LPG 125 km, FCEV 500 km, and a car that uses nothing has no limit.
    private val varied = listOf(
        car(CarType.BEV, "E", 30.0, bodyStyle = BodyStyle.SUV, transmission = Transmission.Automatic, capacity = 60.0, consumption = 15.0),
        car(CarType.ICE, "P", 20.0, bodyStyle = BodyStyle.Hatchback, fuelType = FuelType.Petrol, capacity = 40.0, consumption = 5.0),
        car(CarType.ICE, "L", 20.0, bodyStyle = BodyStyle.Hatchback, fuelType = FuelType.LPG, capacity = 10.0, consumption = 8.0),
        car(CarType.FCEV, "H", 50.0, bodyStyle = BodyStyle.SUV, transmission = Transmission.Automatic, capacity = 5.0, consumption = 1.0),
        car(CarType.BEV, "Z", 40.0, capacity = 50.0, consumption = 0.0),
    )

    private fun variedPlates(vararg query: Pair<String, String>) =
        filterCars(varied, Parameters.build { query.forEach { (k, v) -> append(k, v) } })?.map { it.licensePlate }

    @Test
    fun RangeIsCapacityPerConsumptionAndUnlimitedWithoutConsumption() {
        assertEquals(listOf(400.0, 800.0, 125.0, 500.0), varied.take(4).map { it.rangeKm })
        assertEquals(Double.POSITIVE_INFINITY, varied.last().rangeKm)
    }

    @Test
    fun RangeDoesNotLoseAKilometreToRounding() {
        val exact = car(CarType.BEV, "X", 10.0, capacity = 57.0, consumption = 100.0)
        assertEquals(57.0, exact.rangeKm)
        assertEquals(listOf("X"), filterCars(listOf(exact), parametersOf("minRange", "57"))?.map { it.licensePlate })
    }

    @Test
    fun BodyStyleAndTransmissionMustMatchExactly() {
        assertEquals(listOf("E", "H"), variedPlates("bodyStyle" to "SUV"))
        assertEquals(listOf("E", "H"), variedPlates("transmission" to "Automatic"))
        listOf("suv", "", "Truck").forEach { assertNull(variedPlates("bodyStyle" to it), it) }
        listOf("automatic", "", "CVT").forEach { assertNull(variedPlates("transmission" to it), it) }
    }

    @Test
    fun FuelTypeOnlyMatchesCombustionCars() {
        assertEquals(listOf("P"), variedPlates("fuelType" to "Petrol"))
        assertEquals(listOf("L"), variedPlates("fuelType" to "LPG"))
        assertEquals(emptyList(), variedPlates("fuelType" to "Diesel"))
        assertNull(variedPlates("fuelType" to "petrol"))
    }

    @Test
    fun MinRangeIncludesTheRangeItselfAndKeepsUnlimitedCars() {
        assertEquals(listOf("E", "P", "H", "Z"), variedPlates("minRange" to "400"))
        assertEquals(listOf("P", "Z"), variedPlates("minRange" to "500.1"))
        assertEquals(listOf("E", "P", "L", "H", "Z"), variedPlates("minRange" to "0"))
        listOf("-1", "abc", "", "NaN", "Infinity").forEach { assertNull(variedPlates("minRange" to it), it) }
    }

    @Test
    fun SortsByRateAscendingAndDescendingKeepingTheFleetOrderForTies() {
        assertEquals(listOf("P", "L", "E", "Z", "H"), variedPlates("sort" to "rate"))
        assertEquals(listOf("H", "Z", "E", "P", "L"), variedPlates("sort" to "-rate"))
    }

    @Test
    fun SortsByRange() {
        assertEquals(listOf("L", "E", "H", "P", "Z"), variedPlates("sort" to "range"))
        assertEquals(listOf("Z", "P", "H", "E", "L"), variedPlates("sort" to "-range"))
    }

    @Test
    fun SortsByCostPerKmWithTheCheapestPerKmFirst() {
        // per km at the standard prices: Z 0 (no consumption), E 0.0525, L 0.08, H 0.12, P 0.105
        assertEquals(listOf("Z", "E", "L", "P", "H"), variedPlates("sort" to "costPerKm"))
        assertEquals(listOf("H", "P", "L", "E", "Z"), variedPlates("sort" to "-costPerKm"))
    }

    @Test
    fun AnUnknownSortFieldIsRejected() {
        listOf("price", "", "-", "--rate", "Rate", "rate,range", "+rate").forEach { assertNull(variedPlates("sort" to it), it) }
    }

    @Test
    fun NewFiltersAndSortCombineWithTheExistingFilters() {
        assertEquals(listOf("E", "H"), variedPlates("transmission" to "Automatic", "sort" to "rate"))
        assertEquals(listOf("H"), variedPlates("transmission" to "Automatic", "maxRate" to "60", "minRange" to "450"))
        assertEquals(listOf("H", "E"), variedPlates("bodyStyle" to "SUV", "sort" to "-rate"))
        assertNull(variedPlates("bodyStyle" to "SUV", "sort" to "nope"))
    }

    @Test
    fun ALocationStillSortsNearestFirstAndSortIsOnlyValidated() {
        val near = car(CarType.BEV, "N", 90.0, 0.0, 0.1)
        val far = car(CarType.BEV, "F", 10.0, 0.0, 1.0)
        val query = parametersOf(mapOf("lat" to listOf("0"), "lon" to listOf("0"), "sort" to listOf("rate")))
        assertEquals(listOf("N", "F"), filterCars(listOf(far, near), query)?.map { it.licensePlate })
        assertNull(filterCars(listOf(far, near), parametersOf(mapOf("lat" to listOf("0"), "lon" to listOf("0"), "sort" to listOf("nope")))))
    }
}
