package com.example.reservations

import java.time.LocalDate
import kotlin.test.*

class FreeSlotTest {
    private val today = LocalDate.of(2030, 3, 10)

    @Test
    fun NeitherParameterMeansNoSlot() = assertNull(parseFreeSlot(null, null, today).getOrThrow())

    @Test
    fun ADateAndDaypartAreParsed() {
        val slot = parseFreeSlot("2030-03-12", "EVENING", today).getOrThrow()!!
        assertEquals(LocalDate.of(2030, 3, 12), slot.date)
        assertEquals(Daypart.EVENING, slot.daypart)
    }

    @Test
    fun TodayIsAllowedAndYesterdayIsNot() {
        assertTrue(parseFreeSlot("2030-03-10", "MORNING", today).isSuccess)
        assertTrue(parseFreeSlot("2030-03-09", "MORNING", today).isFailure)
    }

    @Test
    fun OnlyOneOfTheTwoFails() {
        assertTrue(parseFreeSlot("2030-03-12", null, today).isFailure)
        assertTrue(parseFreeSlot(null, "MORNING", today).isFailure)
    }

    @Test
    fun InvalidValuesFail() {
        listOf("12-03-2030", "2030-02-30", "", "9999999999-01-01", "+999999999-01-01").forEach {
            assertTrue(parseFreeSlot(it, "MORNING", today).isFailure, it)
        }
        listOf("morning", "NIGHT", "").forEach { assertTrue(parseFreeSlot("2030-03-12", it, today).isFailure, it) }
    }
}
