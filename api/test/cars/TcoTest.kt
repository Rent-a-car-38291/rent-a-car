package com.example.cars

import kotlin.test.*

class TcoTest {
    private val prices = EnergyPrices(petrolPerLitre = 2.0, dieselPerLitre = 1.5, lpgPerLitre = 1.0, electricityPerKwh = 0.4, hydrogenPerKg = 10.0)

    // Depreciation 10000 / 10 = 1000, plus 500 + 200 + 300 = 2000 of fixed costs.
    private fun ice() = ICECar(1, 1, "B", "P", 0, CarStatus.Available, 1.0, Location(0.0, 0.0), 4, BodyStyle.Sedan, 2020, 1, Transmission.Manual, "Red", FuelType.Petrol, 50.0, 6.0, 10_000.0, 500.0, 200.0, 300.0)
    private fun bev() = BEVCar(1, 1, "B", "P", 0, CarStatus.Available, 1.0, Location(0.0, 0.0), 4, BodyStyle.Sedan, 2020, 1, Transmission.Manual, "Red", 60.0, 15.0, 10_000.0, 500.0, 200.0, 300.0)
    private fun fcev() = FCEVCar(1, 1, "B", "P", 0, CarStatus.Available, 1.0, Location(0.0, 0.0), 4, BodyStyle.Sedan, 2020, 1, Transmission.Manual, "Red", 5.0, 1.0, 10_000.0, 500.0, 200.0, 300.0)

    @Test
    fun CombustionTcoAddsFixedCostsAndEnergyForTheKm() = assertEquals(3440.0, ice().tcoPerYear(12_000, prices), 1e-9) // 2000 + 1000 + 0.12 * 12000

    @Test
    fun ElectricTcoUsesElectricityPrice() = assertEquals(2720.0, bev().tcoPerYear(12_000, prices), 1e-9) // 2000 + 0.06 * 12000

    @Test
    fun HydrogenTcoUsesHydrogenPrice() = assertEquals(3200.0, fcev().tcoPerYear(12_000, prices), 1e-9) // 2000 + 0.1 * 12000

    @Test
    fun ZeroKmLeavesOnlyTheFixedCosts() = assertEquals(2000.0, bev().tcoPerYear(0, prices), 1e-9)

    @Test
    fun CarWithoutCostsOnlyCostsEnergy() {
        val car = AddCarRequest(
            CarType.BEV, "B", "P", 0, 1.0, Location(0.0, 0.0), 4, BodyStyle.Sedan, 2020, 1, Transmission.Manual, "Red",
            capacity = 60.0, consumption = 15.0,
        ).toCar(1)

        assertEquals(720.0, car.tcoPerYear(12_000, prices), 1e-9)
    }

    @Test
    fun NegativeOrHugeAmountsAreRejected() {
        val request = AddCarRequest(
            CarType.BEV, "B", "P", 0, 1.0, Location(0.0, 0.0), 4, BodyStyle.Sedan, 2020, 1, Transmission.Manual, "Red",
            capacity = 60.0, consumption = 15.0,
        )

        assertFailsWith<IllegalArgumentException> { request.copy(purchasePrice = -1.0).toCar(1) }
        assertFailsWith<IllegalArgumentException> { request.copy(maintenancePerYear = 1e9).toCar(1) }
    }
}
