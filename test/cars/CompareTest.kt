package com.example.cars

import kotlin.test.*

class CompareTest {
    private val prices = EnergyPrices(petrolPerLitre = 2.0, dieselPerLitre = 1.5, lpgPerLitre = 1.0, electricityPerKwh = 0.4, hydrogenPerKg = 10.0)

    private fun ice(fuel: FuelType, consumption: Double) = ICECar(1, 1, "I", "P", 0, CarStatus.Available, 30.0, Location(0.0, 0.0), 5, BodyStyle.Hatchback, 2020, 90, Transmission.Manual, "Red", fuel, 45.0, consumption)
    private fun bev(consumption: Double) = BEVCar(2, 1, "B", "P", 0, CarStatus.Available, 55.0, Location(0.0, 0.0), 4, BodyStyle.Sedan, 2021, 150, Transmission.Automatic, "White", 60.0, consumption)
    private fun fcev(consumption: Double) = FCEVCar(3, 1, "F", "P", 0, CarStatus.Available, 70.0, Location(0.0, 0.0), 4, BodyStyle.Sedan, 2022, 130, Transmission.Automatic, "Blue", 5.6, consumption)

    @Test
    fun CostAndCo2PerFuelForTheDistance() {
        val petrol = compareTypes(listOf(ice(FuelType.Petrol, 5.0)), 100.0, prices).types.single() // 0.05 l/km
        assertEquals(10.0, petrol.averageCost)
        assertEquals(11.5, petrol.co2Kg)

        assertEquals(13.5, compareTypes(listOf(ice(FuelType.Diesel, 5.0)), 100.0, prices).types.single().co2Kg)
        assertEquals(7.5, compareTypes(listOf(ice(FuelType.LPG, 5.0)), 100.0, prices).types.single().co2Kg)
    }

    @Test
    fun ElectricAndHydrogenCostEnergyButEmitNothing() {
        val result = compareTypes(listOf(bev(15.0), fcev(0.8)), 100.0, prices).types

        assertEquals(listOf(CarType.BEV, CarType.FCEV), result.map { it.type })
        assertEquals(6.0, result[0].averageCost) // 0.15 kWh * 0.4 * 100
        assertEquals(8.0, result[1].averageCost) // 0.008 kg * 10 * 100
        assertEquals(listOf(0.0, 0.0), result.map { it.co2Kg })
    }

    @Test
    fun AveragesOverTheCarsOfOneType() {
        val petrol = compareTypes(listOf(ice(FuelType.Petrol, 4.0), ice(FuelType.Petrol, 8.0)), 100.0, prices).types.single()

        assertEquals(2, petrol.cars)
        assertEquals(12.0, petrol.averageCost) // (8 + 16) / 2
    }

    @Test
    fun ATypeWithoutCarsIsLeftOutAndTheOrderIsFixed() {
        assertEquals(emptyList(), compareTypes(emptyList(), 50.0, prices).types)
        assertEquals(listOf(CarType.BEV, CarType.ICE, CarType.FCEV), compareTypes(listOf(fcev(1.0), ice(FuelType.Petrol, 5.0), bev(15.0)), 50.0, prices).types.map { it.type })
    }

    @Test
    fun RoundsHalfUpToTwoDecimals() {
        assertEquals(0.01, compareTypes(listOf(bev(1.0)), 1.25, prices).types.single().averageCost) // 0.004 * 1.25 = 0.005
    }
}
