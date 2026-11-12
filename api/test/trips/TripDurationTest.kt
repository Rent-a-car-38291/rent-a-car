package com.example.trips

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class TripDurationTest {
    private val start = Instant.parse("2026-10-12T08:00:00Z")

    @Test
    fun ATripThatEndsAtOnceTookOneMinute() = assertEquals(1, durationMinutes(start, start))

    @Test
    fun PartOfAMinuteRoundsUp() {
        assertEquals(1, durationMinutes(start, start.plusSeconds(1)))
        assertEquals(1, durationMinutes(start, start.plusSeconds(60)))
        assertEquals(2, durationMinutes(start, start.plusSeconds(61)))
    }

    @Test
    fun WholeMinutesStayAsTheyAre() = assertEquals(37, durationMinutes(start, start.plusSeconds(37 * 60)))

    @Test
    fun AnEndBeforeTheStartStillGivesOneMinute() = assertEquals(1, durationMinutes(start, start.minusSeconds(30)))
}
