package com.example.organizations

import com.example.cars.Car
import com.example.cars.EnergyPrices
import com.example.cars.STANDARD_KM_PER_YEAR
import com.example.cars.roundTo
import com.example.trips.CarUsage
import com.example.trips.averageScore
import kotlinx.serialization.Serializable
import java.math.BigDecimal

// One row per car. reservations counts the ones that are not cancelled, trips and totalKm the finished trips; averageScore
// (one decimal) is null without trips. tcoPerYear is the TCO at the standard km per year, in EUR.
@Serializable
data class CarReportRow(
    val carId: Int,
    val brand: String,
    val licensePlate: String,
    val reservations: Int,
    val trips: Int,
    val totalKm: Long,
    val averageScore: Double?,
    val tcoPerYear: Double,
)

// A car without reservations gets zeros. The rows keep the order of the cars.
fun buildReport(cars: List<Car>, usage: Map<Int, CarUsage>): List<CarReportRow> = cars.map { car ->
    val used = usage[car.id] ?: CarUsage(0, 0, 0, 0)
    CarReportRow(
        car.id, car.brand, car.licensePlate, used.reservations, used.trips, used.km,
        if (used.trips == 0) null else averageScore(used.scoreSum, used.trips),
        car.tcoPerYear(STANDARD_KM_PER_YEAR, EnergyPrices.STANDARD).roundTo(2),
    )
}

private val CSV_HEADER = listOf("carId", "brand", "licensePlate", "reservations", "trips", "totalKm", "averageScore", "tcoPerYear")

// Brand and license plate are typed by users: a cell that a spreadsheet would read as a formula gets a leading quote,
// and a cell with a comma, quote or line break is quoted (RFC 4180).
private fun csvCell(value: String): String {
    val safe = if (value.firstOrNull()?.let { it in "=+-@\t\r" } == true) "'$value" else value
    return if (safe.any { it in ",\"\n\r" }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
}

fun reportCsv(rows: List<CarReportRow>): String =
    (listOf(CSV_HEADER) + rows.map {
        listOf(
            it.carId.toString(), it.brand, it.licensePlate, it.reservations.toString(), it.trips.toString(), it.totalKm.toString(),
            it.averageScore?.toString() ?: "", BigDecimal.valueOf(it.tcoPerYear).setScale(2).toPlainString(),
        )
    }).joinToString("") { line -> line.joinToString(",", transform = ::csvCell) + "\r\n" }
