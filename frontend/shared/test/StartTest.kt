package com.avans.rentacar

import com.avans.rentacar.start.Dayparts
import com.avans.rentacar.start.nextDaypart
import kotlin.test.Test
import kotlin.test.assertEquals

class StartTest {
    @Test
    fun `intro goes to welcome for a visitor and to car search when logged in`() {
        assertEquals(Screen.Welcome, afterIntro(loggedIn = false))
        assertEquals(Screen.CarSearch, afterIntro(loggedIn = true))
    }

    @Test
    fun `headline rotates morning, afternoon, evening and back`() {
        val order = generateSequence(0, ::nextDaypart).take(4).map { Dayparts[it].first }.toList()
        assertEquals(listOf("morning", "afternoon", "evening", "morning"), order)
    }
}
