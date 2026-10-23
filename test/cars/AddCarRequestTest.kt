package com.example.cars

import kotlin.test.*

// Unit tests of the validation in AddCarRequest.toCar; the Post/Put route tests check the status codes.
class AddCarRequestTest {
    private fun request(type: CarType = CarType.BEV) = AddCarRequest(
        type, "Test Motors", "EV-1", 1200, 45.5, Location(52.3, 4.9), 4, BodyStyle.Sedan, 2024, 150, Transmission.Automatic, "Blue",
        fuelType = FuelType.Diesel.takeIf { type == CarType.ICE }, capacity = 75.0, consumption = 16.2,
    )

    @Test
    fun EachTypeBecomesItsOwnClassWithItsOwnFields() {
        val bev = request().toCar(1) as BEVCar
        assertEquals(75.0, bev.batteryCapacityKwh)
        assertEquals(16.2, bev.consumptionKwhPer100Km)

        val ice = request(CarType.ICE).toCar(1) as ICECar
        assertEquals(FuelType.Diesel, ice.fuelType)
        assertEquals(75.0, ice.tankCapacityLitres)
        assertEquals(16.2, ice.consumptionLitresPer100Km)

        val fcev = request(CarType.FCEV).toCar(1) as FCEVCar
        assertEquals(75.0, fcev.tankCapacityKg)
        assertEquals(16.2, fcev.consumptionKgPer100Km)
    }

    @Test
    fun ANewCarIsAvailableAndGetsTheGivenOwnerIdAndOrganization() {
        val car = request().toCar(ownerId = 7, id = 3, organizationId = 9)
        assertEquals(CarStatus.Available, car.status)
        assertEquals(7, car.ownerId)
        assertEquals(3, car.id)
        assertEquals(9, car.organizationId)
        assertNull(request().toCar(null).ownerId)
    }

    @Test
    fun ICERequiresAFuelTypeAndTheOtherTypesIgnoreIt() {
        assertFailsWith<IllegalArgumentException> { request(CarType.ICE).copy(fuelType = null).toCar(1) }
        assertIs<BEVCar>(request().copy(fuelType = FuelType.LPG).toCar(1))
    }

    @Test
    fun CapacityAndConsumptionAreRequired() {
        assertFailsWith<IllegalArgumentException> { request().copy(capacity = null).toCar(1) }
        assertFailsWith<IllegalArgumentException> { request().copy(consumption = null).toCar(1) }
    }

    @Test
    fun CapacityAndConsumptionCannotBeNegative() {
        assertFailsWith<IllegalArgumentException> { request().copy(capacity = -1.0).toCar(1) }
        assertFailsWith<IllegalArgumentException> { request().copy(consumption = -0.1).toCar(1) }
        assertEquals(0.0, (request().copy(capacity = 0.0, consumption = 0.0).toCar(1) as BEVCar).consumptionKwhPer100Km)
    }

    @Test
    fun MileageAndRateHaveBounds() {
        assertEquals(0, request().copy(mileage = 0).toCar(1).mileage)
        assertFailsWith<IllegalArgumentException> { request().copy(mileage = -1).toCar(1) }
        assertEquals(0.0, request().copy(rate = 0.0).toCar(1).rate)
        assertEquals(999_999.99, request().copy(rate = 999_999.99).toCar(1).rate)
        assertFailsWith<IllegalArgumentException> { request().copy(rate = 1_000_000.0).toCar(1) }
        assertFailsWith<IllegalArgumentException> { request().copy(rate = -0.01).toCar(1) }
        assertFailsWith<IllegalArgumentException> { request().copy(rate = Double.NaN).toCar(1) }
    }

    @Test
    fun TheYearlyAmountsDefaultToZeroAndHaveBounds() {
        val car = request().toCar(1)
        assertEquals(listOf(0.0, 0.0, 0.0, 0.0), listOf(car.purchasePrice, car.insurancePerYear, car.roadTaxPerYear, car.maintenancePerYear))
        assertEquals(MAX_AMOUNT, request().copy(purchasePrice = MAX_AMOUNT).toCar(1).purchasePrice)
        assertFailsWith<IllegalArgumentException> { request().copy(insurancePerYear = MAX_AMOUNT + 1).toCar(1) }
        assertFailsWith<IllegalArgumentException> { request().copy(roadTaxPerYear = Double.NaN).toCar(1) }
        assertFailsWith<IllegalArgumentException> { request().copy(maintenancePerYear = -1.0).toCar(1) }
    }

    @Test
    fun AnUpdateKeepsTheAmountsItLeavesOut() {
        val existing = request().copy(purchasePrice = 20_000.0, insurancePerYear = 600.0, roadTaxPerYear = 300.0, maintenancePerYear = 400.0).toCar(1)

        val updated = request().copy(insurancePerYear = 700.0).toCar(1, existing = existing)

        assertEquals(listOf(20_000.0, 700.0, 300.0, 400.0), listOf(updated.purchasePrice, updated.insurancePerYear, updated.roadTaxPerYear, updated.maintenancePerYear))
        assertEquals(0.0, request().copy(insurancePerYear = 0.0).toCar(1, existing = existing).insurancePerYear) // an explicit 0 is not "left out"
    }
}
