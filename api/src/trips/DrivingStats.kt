package com.example.trips

import com.example.reservations.Daypart
import kotlinx.serialization.Serializable

// averageScore (one decimal) and bestDaypart are null without trips; bestDaypart is the daypart with the highest average score.
@Serializable
data class DrivingStats(val tripCount: Int, val totalKm: Long, val averageScore: Double?, val bestDaypart: Daypart?, val bonusPoints: Int)

// The trips of one daypart added up by the database.
data class DaypartTotals(val daypart: Daypart, val trips: Int, val km: Long, val scoreSum: Long)

// A tie on the average goes to the earliest daypart, so the answer never depends on the order of the rows.
fun calculateStats(totals: List<DaypartTotals>, bonusPoints: Int): DrivingStats {
    val trips = totals.sumOf { it.trips }
    if (trips == 0) return DrivingStats(0, 0, null, null, bonusPoints)
    val best = totals.filter { it.trips > 0 }.sortedBy { it.daypart }.maxBy { it.scoreSum.toDouble() / it.trips }
    return DrivingStats(trips, totals.sumOf { it.km }, averageScore(totals.sumOf { it.scoreSum }, trips), best.daypart, bonusPoints)
}

// Whole tenths, rounded half up in integers: a double can land just under an exact .x5. Needs at least one trip.
internal fun averageScore(scoreSum: Long, trips: Int): Double = (scoreSum * 20 + trips) / (2 * trips) / 10.0
