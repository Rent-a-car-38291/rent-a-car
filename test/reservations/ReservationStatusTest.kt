package com.example.reservations

import io.ktor.http.parametersOf
import kotlin.test.*

class ReservationStatusTest {
    @Test
    fun OnlyAReservedReservationCanMoveAndNeverBackToReserved() {
        assertTrue(ReservationStatus.RESERVED.canMoveTo(ReservationStatus.COMPLETED))
        assertTrue(ReservationStatus.RESERVED.canMoveTo(ReservationStatus.CANCELLED))
        assertFalse(ReservationStatus.RESERVED.canMoveTo(ReservationStatus.RESERVED))
        for (from in listOf(ReservationStatus.COMPLETED, ReservationStatus.CANCELLED)) {
            ReservationStatus.entries.forEach { to -> assertFalse(from.canMoveTo(to), "$from -> $to") }
        }
    }

    @Test
    fun TheFilterHasDefaultsAndReadsTheStatus() {
        val none = parseReservationFilter(parametersOf()).getOrThrow()
        assertEquals(ReservationFilter(null, DEFAULT_RESERVATION_LIMIT, 0), none)

        val cancelled = parseReservationFilter(parametersOf("status" to listOf("CANCELLED"), "limit" to listOf("5"), "offset" to listOf("10"))).getOrThrow()
        assertEquals(ReservationFilter(ReservationStatus.CANCELLED, 5, 10), cancelled)
    }

    @Test
    fun InvalidFilterValuesFail() {
        listOf("status" to "DONE", "status" to "cancelled", "limit" to "0", "limit" to "${MAX_RESERVATION_LIMIT + 1}", "limit" to "x", "offset" to "-1", "offset" to "1.5").forEach { (name, value) ->
            assertTrue(parseReservationFilter(parametersOf(name, value)).isFailure, "$name=$value")
        }
    }
}
