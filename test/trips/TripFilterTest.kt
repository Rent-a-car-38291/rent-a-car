package com.example.trips

import com.example.DEFAULT_LIMIT
import com.example.MAX_LIMIT
import io.ktor.http.parametersOf
import java.time.LocalDate
import kotlin.test.*

class TripFilterTest {
    private fun parse(vararg pairs: Pair<String, String>) = parseTripFilter(parametersOf(*pairs.map { it.first to listOf(it.second) }.toTypedArray()))

    @Test
    fun NoParametersGiveTheDefaults() {
        val filter = parse().getOrThrow()
        assertEquals(TripFilter(null, null, DEFAULT_LIMIT, 0), filter)
    }

    @Test
    fun AllParametersAreParsed() {
        val filter = parse("from" to "2026-01-01", "to" to "2026-02-01", "limit" to "5", "offset" to "10").getOrThrow()
        assertEquals(TripFilter(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), 5, 10), filter)
    }

    @Test
    fun TheLimitStaysBetween1AndTheMaximum() {
        assertTrue(parse("limit" to "1").isSuccess)
        assertTrue(parse("limit" to MAX_LIMIT.toString()).isSuccess)
        assertTrue(parse("limit" to "0").isFailure)
        assertTrue(parse("limit" to (MAX_LIMIT + 1).toString()).isFailure)
    }

    @Test
    fun ANegativeOffsetFails() {
        assertTrue(parse("offset" to "0").isSuccess)
        assertTrue(parse("offset" to "-1").isFailure)
    }

    @Test
    fun NonNumbersAndInvalidDatesFail() {
        assertTrue(parse("limit" to "many").isFailure)
        assertTrue(parse("offset" to "1.5").isFailure)
        assertTrue(parse("from" to "12-10-2026").isFailure)
        assertTrue(parse("to" to "2026-02-30").isFailure)
    }

    @Test
    fun FromAfterToFailsButTheSameDayIsFine() {
        assertTrue(parse("from" to "2026-02-02", "to" to "2026-02-01").isFailure)
        assertTrue(parse("from" to "2026-02-01", "to" to "2026-02-01").isSuccess)
    }
}
