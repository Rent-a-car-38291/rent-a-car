package com.example.cars

import kotlin.test.*

// Unit tests of the service rule and of the lastServiceKm validation in AddCarRequest.toCar; ServiceRoutesTest checks the endpoints.
class ServiceDueTest {
    private fun request(mileage: Int = 50_000, lastServiceKm: Int? = null) = AddCarRequest(
        CarType.BEV, "Test Motors", "EV-1", mileage, 45.5, Location(52.3, 4.9), 4, BodyStyle.Sedan, 2024, 150, Transmission.Automatic, "Blue",
        capacity = 75.0, consumption = 16.2, lastServiceKm = lastServiceKm,
    )

    @Test
    fun ACarIsDueOnlyAfterMoreThan15000KmSinceTheLastService() {
        assertFalse(isServiceDue(mileage = 0, lastServiceKm = 0))
        assertFalse(isServiceDue(mileage = 14_999, lastServiceKm = 0))
        assertFalse(isServiceDue(mileage = 15_000, lastServiceKm = 0))
        assertTrue(isServiceDue(mileage = 15_001, lastServiceKm = 0))
        assertFalse(isServiceDue(mileage = 65_000, lastServiceKm = 50_000))
        assertTrue(isServiceDue(mileage = 65_001, lastServiceKm = 50_000))
    }

    @Test
    fun ANewCarCountsAsServicedAtItsMileage() {
        val car = request(mileage = 120_000).toCar(1)
        assertEquals(120_000, car.lastServiceKm)
        assertFalse(car.serviceDue)
    }

    @Test
    fun AGivenLastServiceIsUsedAndMakesTheCarDue() {
        val car = request(mileage = 50_000, lastServiceKm = 20_000).toCar(1)
        assertEquals(20_000, car.lastServiceKm)
        assertTrue(car.serviceDue)
    }

    @Test
    fun AnUpdateThatLeavesTheLastServiceOutKeepsTheCurrentOne() {
        val existing = request(mileage = 50_000, lastServiceKm = 20_000).toCar(1, id = 3)
        assertEquals(20_000, request(mileage = 60_000).toCar(1, id = 3, existing = existing).lastServiceKm)
    }

    @Test
    fun TheLastServiceCantBeNegativeOrBeyondTheMileage() {
        assertFailsWith<IllegalArgumentException> { request(lastServiceKm = -1).toCar(1) }
        assertFailsWith<IllegalArgumentException> { request(mileage = 50_000, lastServiceKm = 50_001).toCar(1) }
        assertEquals(50_000, request(mileage = 50_000, lastServiceKm = 50_000).toCar(1).lastServiceKm)
        assertEquals(0, request(lastServiceKm = 0).toCar(1).lastServiceKm)
    }

    @Test
    fun LoweringTheMileageBelowTheKeptLastServiceIsRejected() {
        val existing = request(mileage = 50_000, lastServiceKm = 40_000).toCar(1, id = 3)
        assertFailsWith<IllegalArgumentException> { request(mileage = 30_000).toCar(1, id = 3, existing = existing) }
    }

    @Test
    fun TheMileageHasAnUpperLimitSoTripsCantOverflowTheColumn() {
        assertEquals(MAX_MILEAGE, request(mileage = MAX_MILEAGE).toCar(1).mileage)
        assertFailsWith<IllegalArgumentException> { request(mileage = MAX_MILEAGE + 1).toCar(1) }
    }
}
