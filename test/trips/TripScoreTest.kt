package com.example.trips

import kotlin.test.*

class TripScoreTest {
    @Test
    fun ACalmTripScores100AndEarnsOnePointPerKm() {
        assertEquals(100, calculateScore(50, 0, 0))
        assertEquals(50, bonusPoints(50, 100))
    }

    @Test
    fun EachHardAccelerationOrBrakePer100KmCostsFivePoints() {
        assertEquals(75, calculateScore(100, 2, 3))
        assertEquals(75, calculateScore(200, 5, 5))
        assertEquals(75, bonusPoints(100, 75))
    }

    @Test
    fun TheScoreNeverGoesBelowZero() {
        assertEquals(0, calculateScore(10, 50, 50))
        assertEquals(0, calculateScore(1, Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test
    fun ATripOfZeroKmScoresZeroAndEarnsNothing() {
        assertEquals(0, calculateScore(0, 0, 0))
        assertEquals(0, calculateScore(0, 3, 3))
        assertEquals(0, bonusPoints(0, 0))
    }

    @Test
    fun PointsAreRoundedDown() {
        assertEquals(0, bonusPoints(1, 99))
        assertEquals(9, bonusPoints(10, 99))
    }

    @Test
    fun TheLongestTripCannotOverflow() {
        assertEquals(MAX_TRIP_KM, bonusPoints(MAX_TRIP_KM, calculateScore(MAX_TRIP_KM, 0, 0)))
    }
}
