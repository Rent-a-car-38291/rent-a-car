package com.example.accounts

import com.example.database.TestDatabase
import kotlin.test.*

class UserRepositoryTest {
    private val users = UserRepository(TestDatabase.dataSource)

    @BeforeTest
    fun reset() = users.clear()

    @Test
    fun RegisterDuplicateEmailReturnsNull() {
        users.register("Jan", "jan@example.com", "supersecret")

        assertNull(users.register("Piet", "jan@example.com", "anothersecret"))
    }

    @Test
    fun RegisterComparesEmailIgnoringCase() {
        users.register("Jan", "jan@example.com", "supersecret")

        assertNull(users.register("Jan", "JAN@Example.COM", "supersecret"))
    }

    @Test
    fun RegisterStoresHashedPassword() {
        val user = assertNotNull(users.register("Jan", "jan@example.com", "supersecret"))

        assertNotEquals("supersecret", user.passwordHash)
        assertFalse(user.passwordHash.contains("supersecret"))
    }

    @Test
    fun UserIsStillThereForANewRepositoryOnTheSameDatabase() {
        val user = assertNotNull(users.register("Jan", "jan@example.com", "supersecret"))

        val afterRestart = UserRepository(TestDatabase.dataSource)

        assertEquals(user.id, afterRestart.find(user.id)?.id)
        assertEquals(user.id, afterRestart.authenticate("JAN@example.com", "supersecret")?.id)
        assertNull(afterRestart.authenticate("jan@example.com", "wrongpassword"))
    }

    @Test
    fun DeletedUserIdIsNotHandedOutAgain() {
        val first = assertNotNull(users.register("Jan", "jan@example.com", "supersecret"))
        assertTrue(users.delete(first.id))

        val second = assertNotNull(users.register("Piet", "piet@example.com", "supersecret"))

        assertNull(users.find(first.id))
        assertNotEquals(first.id, second.id)
        assertFalse(users.delete(first.id))
    }
}
