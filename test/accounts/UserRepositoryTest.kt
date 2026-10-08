package com.example.accounts

import kotlin.test.*

class UserRepositoryTest {
    @BeforeTest
    fun reset() = UserRepository.clear()

    @Test
    fun RegisterDuplicateEmailReturnsNull() {
        UserRepository.register("Jan", "jan@example.com", "supersecret")

        assertNull(UserRepository.register("Piet", "jan@example.com", "anothersecret"))
    }

    @Test
    fun RegisterComparesEmailIgnoringCase() {
        UserRepository.register("Jan", "jan@example.com", "supersecret")

        assertNull(UserRepository.register("Jan", "JAN@Example.COM", "supersecret"))
    }

    @Test
    fun RegisterStoresHashedPassword() {
        val user = assertNotNull(UserRepository.register("Jan", "jan@example.com", "supersecret"))

        assertNotEquals("supersecret", user.passwordHash)
        assertFalse(user.passwordHash.contains("supersecret"))
    }

    @Test
    fun AuthenticateUnknownEmailTakesAsLongAsKnownEmail() {
        UserRepository.register("Jan", "jan@example.com", "supersecret")
        fun millis(email: String): Long {
            val start = System.nanoTime()
            UserRepository.authenticate(email, "wrongpassword")
            return (System.nanoTime() - start) / 1_000_000
        }
        millis("nobody@example.com") // warm up, also computes the dummy hash

        val known = List(3) { millis("jan@example.com") }.sorted()[1]
        val unknown = List(3) { millis("nobody@example.com") }.sorted()[1]

        assertTrue(unknown * 2 >= known, "unknown email took ${unknown}ms, known email ${known}ms")
    }
}
