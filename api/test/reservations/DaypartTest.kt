package com.example.reservations

import java.time.LocalTime
import kotlin.test.*

class DaypartTest {
    @Test
    fun TheDaypartsFollowEachOtherWithoutGapsOrOverlap() {
        Daypart.entries.zipWithNext { a, b -> assertEquals(a.until, b.from, "${a.name} to ${b.name}") }
        Daypart.entries.forEach { assertTrue(it.from < it.until, it.name) }
    }

    @Test
    fun TheDaypartsOrderIsMorningAfternoonEvening() = assertEquals(listOf("MORNING", "AFTERNOON", "EVENING"), Daypart.entries.map { it.name })

    @Test
    fun TheEveningRunsToTheEndOfTheDay() = assertEquals(LocalTime.MAX, Daypart.EVENING.until)

    @Test
    fun FreeDaypartsAreTheOnesNotReserved() {
        // The same subtraction the availability route does.
        assertEquals(listOf(Daypart.MORNING, Daypart.EVENING), Daypart.entries - setOf(Daypart.AFTERNOON))
        assertEquals(Daypart.entries, Daypart.entries - emptySet())
        assertEquals(emptyList(), Daypart.entries - Daypart.entries.toSet())
    }
}
