package com.example.cars

import kotlin.test.*

class CostPerKmTest {
    private val prices = EnergyPrices(petrolPerLitre = 2.0, dieselPerLitre = 1.5, lpgPerLitre = 1.0, electricityPerKwh = 0.4, hydrogenPerKg = 10.0)

    private fun ice(fuel: FuelType, consumption: Double) = ICECar(1, 1, "B", "P", 0, CarStatus.Available, 1.0, Location(0.0, 0.0), 4, BodyStyle.Sedan, 2020, 1, Transmission.Manual, "Red", fuel, 50.0, consumption)
    private fun bev(consumption: Double) = BEVCar(1, 1, "B", "P", 0, CarStatus.Available, 1.0, Location(0.0, 0.0), 4, BodyStyle.Sedan, 2020, 1, Transmission.Manual, "Red", 60.0, consumption)
    private fun fcev(consumption: Double) = FCEVCar(1, 1, "B", "P", 0, CarStatus.Available, 1.0, Location(0.0, 0.0), 4, BodyStyle.Sedan, 2020, 1, Transmission.Manual, "Red", 5.0, consumption)

    @Test
    fun PetrolCostsConsumptionTimesPetrolPrice() = assertEquals(0.12, ice(FuelType.Petrol, 6.0).costPerKm(prices), 1e-9)

    @Test
    fun DieselAndLpgUseTheirOwnPrice() {
        assertEquals(0.075, ice(FuelType.Diesel, 5.0).costPerKm(prices), 1e-9)
        assertEquals(0.07, ice(FuelType.LPG, 7.0).costPerKm(prices), 1e-9)
    }

    @Test
    fun ElectricCostsKwhTimesElectricityPrice() = assertEquals(0.06, bev(15.0).costPerKm(prices), 1e-9)

    @Test
    fun HydrogenCostsKgTimesHydrogenPrice() = assertEquals(0.1, fcev(1.0).costPerKm(prices), 1e-9)

    @Test
    fun ZeroConsumptionCostsNothing() {
        assertEquals(0.0, ice(FuelType.Petrol, 0.0).costPerKm(prices))
        assertEquals(0.0, bev(0.0).costPerKm(prices))
        assertEquals(0.0, fcev(0.0).costPerKm(prices))
    }
}
