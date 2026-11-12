package com.example.accounts

import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.jetbrains.amper.ktor.module
import kotlin.test.*

class LoginRateLimitTest {
    @BeforeTest
    fun reset() = UserRepository(TestDatabase.dataSource).clear()

    private suspend fun HttpClient.postJson(path: String, body: String) = post(path) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpClient.register(email: String) =
        postJson("/accounts", """{"name":"Jan","email":"$email","password":"supersecret"}""")

    private suspend fun HttpClient.login(email: String, password: String = "supersecret") =
        postJson("/login", """{"email":"$email","password":"$password"}""")

    @Test
    fun TheSixthWrongLoginIsRefusedWithRetryAfter() = testApplication {
        useTestDatabase()
        application { module() }
        client.register("jan@example.com")

        repeat(5) { assertEquals(HttpStatusCode.Unauthorized, client.login("jan@example.com", "wrongpassword").status) }
        val response = client.login("jan@example.com", "wrongpassword")

        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        val retryAfter = response.headers[HttpHeaders.RetryAfter]!!.toLong()
        assertTrue(retryAfter in 1..900, "Retry-After was $retryAfter")
    }

    @Test
    fun TheCorrectPasswordIsRefusedWhileLocked() = testApplication {
        useTestDatabase()
        application { module() }
        client.register("jan@example.com")
        repeat(5) { client.login("jan@example.com", "wrongpassword") }

        assertEquals(HttpStatusCode.TooManyRequests, client.login("jan@example.com").status)
        assertEquals(HttpStatusCode.TooManyRequests, client.login(" JAN@example.com ").status)
    }

    @Test
    fun AnUnknownEmailIsLockedLikeAKnownOne() = testApplication {
        useTestDatabase()
        application { module() }

        repeat(5) { assertEquals(HttpStatusCode.Unauthorized, client.login("nobody@example.com").status) }

        assertEquals(HttpStatusCode.TooManyRequests, client.login("nobody@example.com").status)
    }

    @Test
    fun ASuccessfulLoginResetsTheCounter() = testApplication {
        useTestDatabase()
        application { module() }
        client.register("jan@example.com")

        repeat(4) { client.login("jan@example.com", "wrongpassword") }
        assertEquals(HttpStatusCode.OK, client.login("jan@example.com").status)
        repeat(4) { assertEquals(HttpStatusCode.Unauthorized, client.login("jan@example.com", "wrongpassword").status) }

        assertEquals(HttpStatusCode.OK, client.login("jan@example.com").status)
    }

    @Test
    fun AnotherEmailIsNotLocked() = testApplication {
        useTestDatabase()
        application { module() }
        client.register("jan@example.com")
        client.register("piet@example.com")
        repeat(5) { client.login("jan@example.com", "wrongpassword") }

        assertEquals(HttpStatusCode.OK, client.login("piet@example.com").status)
    }
}
