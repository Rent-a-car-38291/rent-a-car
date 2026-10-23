package com.example.cars

import kotlin.test.*

class QuoteTest {
    private val prices = EnergyPrices(petrolPerLitre = 2.0, dieselPerLitre = 1.5, lpgPerLitre = 1.0, electricityPerKwh = 0.4, hydrogenPerKg = 10.0)

    private fun bev(rate: Double, consumption: Double) = BEVCar(3, 1, "B", "P", 0, CarStatus.Available, rate, Location(0.0, 0.0), 4, BodyStyle.Sedan, 2020, 1, Transmission.Manual, "Red", 60.0, consumption)

    @Test
    fun TotalIsDistanceTimesCostPerKmPlusRate() {
        val quote = bev(rate = 55.0, consumption = 15.0).quote(120.0, prices) // 0.06 EUR per km

        assertEquals(3, quote.carId)
        assertEquals(120.0, quote.distanceKm)
        assertEquals(7.2, quote.energyCost)
        assertEquals(55.0, quote.rentalRate)
        assertEquals(62.2, quote.total)
        assertEquals("EUR", quote.currency)
    }

    @Test
    fun EnergyCostRoundsHalfUpToTwoDecimals() {
        assertEquals(0.03, bev(1.0, 5.0).quote(1.5, prices).energyCost) // 0.02 * 1.5 = 0.03
        assertEquals(0.09, bev(1.0, 15.0).quote(1.5, prices).energyCost) // 0.06 * 1.5 = 0.09
        assertEquals(0.01, bev(1.0, 1.0).quote(1.25, prices).energyCost) // 0.005 rounds up
    }

    @Test
    fun ExactTiesRoundUpDespiteBinaryNoiseInCostPerKm() {
        val standard = EnergyPrices.STANDARD
        assertEquals(0.04, bev(1.0, 0.5).quote(20.0, standard).energyCost) // 0.5 kWh * 0.35 / 100 * 20 = 0.035
        assertEquals(0.11, bev(1.0, 0.5).quote(60.0, standard).energyCost) // 0.105
        assertEquals(0.18, bev(1.0, 0.5).quote(100.0, standard).energyCost) // 0.175
    }

    @Test
    fun TotalIsTheSumOfTheRoundedParts() {
        val quote = bev(rate = 10.005, consumption = 15.0).quote(1.5, prices) // 0.09 + 10.01

        assertEquals(10.01, quote.rentalRate)
        assertEquals(10.1, quote.total)
    }

    @Test
    fun ZeroConsumptionLeavesOnlyTheRate() = assertEquals(40.0, bev(40.0, 0.0).quote(500.0, prices).total)

    @Test
    fun DistanceMustBeANumberFromOneToTenThousand() {
        assertEquals(1.0, parseQuoteDistance("1"))
        assertEquals(10_000.0, parseQuoteDistance("10000"))
        assertEquals(120.5, parseQuoteDistance("120.5"))
        listOf(null, "", "abc", "0", "0.99", "10000.01", "-5", "NaN", "Infinity", "5d", "0x1p3", " 5", "5 ", "1e3", "+5", ".5", "5.").forEach { assertNull(parseQuoteDistance(it), "$it") }
    }
}
