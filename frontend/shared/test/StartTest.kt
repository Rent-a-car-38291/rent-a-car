package com.avans.rentacar

import com.avans.rentacar.start.Dayparts
import com.avans.rentacar.start.nextDaypart
import kotlin.test.Test
import kotlin.test.assertEquals

class StartTest {
    @Test
    fun `intro goes to welcome only for a visitor on a phone`() {
        assertEquals(Screen.Welcome, afterIntro(loggedIn = false, web = false))
        assertEquals(Screen.CarSearch, afterIntro(loggedIn = true, web = false))
        assertEquals(Screen.CarSearch, afterIntro(loggedIn = false, web = true))
    }

    @Test
    fun `headline rotates morning, afternoon, evening and back`() {
        val order = generateSequence(0, ::nextDaypart).take(4).map { Dayparts[it].first }.toList()
        assertEquals(listOf("morning", "afternoon", "evening", "morning"), order)
    }
}
