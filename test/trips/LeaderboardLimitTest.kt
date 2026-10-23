package com.example.trips

import io.ktor.http.parametersOf
import kotlin.test.*

class LeaderboardLimitTest {
    private fun limit(value: String?) = parseLeaderboardLimit(if (value == null) parametersOf() else parametersOf("limit", value))

    @Test
    fun WithoutALimitTheDefaultIsUsed() {
        assertEquals(DEFAULT_LEADERBOARD_LIMIT, limit(null))
    }

    @Test
    fun TheLimitStaysBetween1AndTheMaximum() {
        assertEquals(1, limit("1"))
        assertEquals(MAX_LEADERBOARD_LIMIT, limit(MAX_LEADERBOARD_LIMIT.toString()))
        assertNull(limit("0"))
        assertNull(limit((MAX_LEADERBOARD_LIMIT + 1).toString()))
        assertNull(limit("-5"))
    }

    @Test
    fun ALimitThatIsNotAWholeNumberIsRejected() {
        assertNull(limit("abc"))
        assertNull(limit("2.5"))
        assertNull(limit(""))
    }
}
