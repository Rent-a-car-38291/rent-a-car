package com.example.trips

import com.example.reservations.Daypart
import kotlin.test.*

class DrivingStatsTest {
    @Test
    fun WithoutTripsTheCountsAreZeroAndTheAverageAndBestDaypartAreNull() {
        assertEquals(DrivingStats(0, 0, null, null, 7), calculateStats(emptyList(), 7))
    }

    @Test
    fun TheTripsOfAllDaypartsAreAddedUp() {
        val stats = calculateStats(listOf(DaypartTotals(Daypart.MORNING, 2, 30, 180), DaypartTotals(Daypart.EVENING, 1, 70, 100)), 55)

        assertEquals(3, stats.tripCount)
        assertEquals(100, stats.totalKm)
        assertEquals(93.3, stats.averageScore)
        assertEquals(55, stats.bonusPoints)
    }

    @Test
    fun TheBestDaypartHasTheHighestAverageScoreNotTheHighestSum() {
        val stats = calculateStats(listOf(DaypartTotals(Daypart.MORNING, 3, 30, 240), DaypartTotals(Daypart.EVENING, 1, 10, 90)), 0)

        assertEquals(Daypart.EVENING, stats.bestDaypart) // 90 beats 80 on average, although the morning has more points in total
    }

    @Test
    fun ATieOnTheAverageGoesToTheEarliestDaypartWhateverTheOrderOfTheRows() {
        val morning = DaypartTotals(Daypart.MORNING, 1, 10, 80)
        val afternoon = DaypartTotals(Daypart.AFTERNOON, 2, 10, 160)

        assertEquals(Daypart.MORNING, calculateStats(listOf(afternoon, morning), 0).bestDaypart)
        assertEquals(Daypart.MORNING, calculateStats(listOf(morning, afternoon), 0).bestDaypart)
    }

    @Test
    fun TheAverageIsRoundedToOneDecimal() {
        assertEquals(66.7, calculateStats(listOf(DaypartTotals(Daypart.MORNING, 3, 3, 200)), 0).averageScore)
        assertEquals(82.5, calculateStats(listOf(DaypartTotals(Daypart.MORNING, 2, 2, 165)), 0).averageScore)
        assertEquals(12.5, calculateStats(listOf(DaypartTotals(Daypart.MORNING, 8, 8, 100)), 0).averageScore)
        assertEquals(0.1, calculateStats(listOf(DaypartTotals(Daypart.MORNING, 20, 20, 1)), 0).averageScore) // 0.05 exactly, rounds half up
        assertEquals(0.0, calculateStats(listOf(DaypartTotals(Daypart.MORNING, 1, 1, 0)), 0).averageScore)
    }
}
