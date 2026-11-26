package com.example.organizations

import com.example.cars.*
import com.example.trips.CarUsage
import kotlin.test.*

class ReportTest {
    // TCO at 15000 km: 10000 / 10 + 500 + 200 + 300 = 2000 fixed, plus 15 kWh per 100 km at EUR 0.35 = 787.5.
    private fun bev(id: Int, brand: String = "Tesla", plate: String = "P-$id") =
        BEVCar(id, null, brand, plate, 0, CarStatus.Available, 1.0, Location(0.0, 0.0), 4, BodyStyle.Sedan, 2020, 1, Transmission.Manual, "Red", 60.0, 15.0, 10_000.0, 500.0, 200.0, 300.0, organizationId = 1)

    @Test
    fun ACarWithTripsGetsItsTotalsAndAnAverageInOneDecimal() {
        val row = buildReport(listOf(bev(1)), mapOf(1 to CarUsage(reservations = 4, trips = 3, km = 100, scoreSum = 250))).single()

        assertEquals(CarReportRow(1, "Tesla", "P-1", 4, 3, 100, 83.3, 2787.5), row)
    }

    @Test
    fun AnAverageHalfWayBetweenTwoTenthsRoundsUp() =
        assertEquals(80.5, buildReport(listOf(bev(1)), mapOf(1 to CarUsage(2, 2, 40, 161))).single().averageScore)

    @Test
    fun ACarWithoutReservationsGetsZerosAndNoAverage() {
        val row = buildReport(listOf(bev(1)), emptyMap()).single()

        assertEquals(listOf<Long>(0, 0, 0), listOf(row.reservations.toLong(), row.trips.toLong(), row.totalKm))
        assertNull(row.averageScore)
    }

    @Test
    fun AReservationWithoutATripCountsButGivesNoAverage() {
        val row = buildReport(listOf(bev(1)), mapOf(1 to CarUsage(reservations = 2, trips = 0, km = 0, scoreSum = 0))).single()

        assertEquals(2, row.reservations)
        assertNull(row.averageScore)
    }

    @Test
    fun TheRowsKeepTheOrderOfTheCarsAndEachGetsItsOwnUsage() {
        val rows = buildReport(listOf(bev(3), bev(1)), mapOf(1 to CarUsage(1, 1, 5, 90), 3 to CarUsage(2, 0, 0, 0)))

        assertEquals(listOf(3, 1), rows.map { it.carId })
        assertEquals(listOf(2, 1), rows.map { it.reservations })
    }

    @Test
    fun TheCsvHasAHeaderAndOneLinePerCarWithAnEmptyAverage() {
        val csv = reportCsv(buildReport(listOf(bev(1), bev(2)), mapOf(1 to CarUsage(3, 2, 40, 161))))

        assertEquals(
            "carId,brand,licensePlate,reservations,trips,totalKm,averageScore,tcoPerYear\r\n" +
                "1,Tesla,P-1,3,2,40,80.5,2787.50\r\n" +
                "2,Tesla,P-2,0,0,0,,2787.50\r\n",
            csv,
        )
    }

    @Test
    fun ACsvCellWithACommaQuoteOrLineBreakIsQuoted() {
        val csv = reportCsv(buildReport(listOf(bev(1, brand = "Say \"hi\", Inc", plate = "A\nB")), emptyMap()))

        assertTrue(csv.endsWith("\r\n1,\"Say \"\"hi\"\", Inc\",\"A\nB\",0,0,0,,2787.50\r\n"), csv)
    }

    @Test
    fun ACsvCellThatLooksLikeAFormulaGetsALeadingQuote() {
        val line = reportCsv(buildReport(listOf(bev(1, brand = "=HYPERLINK(\"x\")", plate = "+1")), emptyMap())).lines().drop(1).first()

        assertTrue(line.startsWith("1,\"'=HYPERLINK(\"\"x\"\")\",'+1,"), line)
    }
}
