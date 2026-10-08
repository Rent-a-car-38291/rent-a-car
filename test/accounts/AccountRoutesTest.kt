package com.example.accounts

import com.example.database.useTestDatabase
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.jetbrains.amper.ktor.module
import kotlin.test.*

class AccountRoutesTest {
    @BeforeTest
    fun reset() = UserRepository.clear()

    private fun valid(email: String = "jan@example.com") =
        """{"name":"Jan","email":"$email","password":"supersecret"}"""

    private suspend fun io.ktor.client.HttpClient.register(body: String) =
        post("/accounts") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    @Test
    fun RegisterReturnsCreatedWithoutPassword() = testApplication {
        useTestDatabase()
        application { module() }

        val response = client.register(valid())

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("jan@example.com"))
        assertFalse(body.contains("password"))
        assertFalse(body.contains("supersecret"))
    }

    @Test
    fun RegisterWithInvalidFieldsReturnsBadRequest() = testApplication {
        useTestDatabase()
        application { module() }

        listOf(
            """{"name":"","email":"jan@example.com","password":"supersecret"}""",
            """{"name":"Jan","email":"not-an-email","password":"supersecret"}""",
            """{"name":"Jan","email":"jan@","password":"supersecret"}""",
            """{"name":"Jan","email":"@example.com","password":"supersecret"}""",
            """{"name":"Jan","email":"jan@example","password":"supersecret"}""",
            """{"name":"Jan","email":"jan@example.com","password":"short"}""",
            """{}""",
            "not json"
        ).forEach { assertEquals(HttpStatusCode.BadRequest, client.register(it).status, it) }
    }

    @Test
    fun RegisterDuplicateEmailReturnsConflict() = testApplication {
        useTestDatabase()
        application { module() }
        client.register(valid())

        val response = client.register(valid("JAN@example.com"))

        assertEquals(HttpStatusCode.Conflict, response.status)
    }
}
