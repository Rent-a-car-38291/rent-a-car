package com.example.organizations

import com.example.accounts.UserRepository
import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.amper.ktor.module
import kotlin.test.*

class OrganizationRoutesTest {
    @BeforeTest
    fun reset() {
        OrganizationRepository(TestDatabase.dataSource).clear()
        UserRepository(TestDatabase.dataSource).clear()
    }

    private suspend fun HttpClient.postJson(path: String, body: String, token: String? = null) =
        post(path) {
            token?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun HttpResponse.json() = Json.parseToJsonElement(bodyAsText()).jsonObject

    // Registers and logs in a user, returning its id and token.
    private suspend fun HttpClient.signUp(email: String = "jan@example.com"): Pair<Int, String> {
        val id = postJson("/accounts", """{"name":"Jan","email":"$email","password":"supersecret"}""").json()["id"]!!.jsonPrimitive.int
        val token = postJson("/login", """{"email":"$email","password":"supersecret"}""").json()["token"]!!.jsonPrimitive.content
        return id to token
    }

    @Test
    fun CreateReturnsCreatedAndMakesTheCreatorAdmin() = testApplication {
        useTestDatabase()
        application { module() }
        val (userId, token) = client.signUp()

        val response = client.postJson("/organizations", """{"name":"  Rent Co  "}""", token)

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.json()
        assertEquals("Rent Co", body["name"]!!.jsonPrimitive.content)
        val membership = OrganizationRepository(TestDatabase.dataSource).findMembership(body["id"]!!.jsonPrimitive.int, userId)
        assertEquals(Role.ADMIN, membership?.role)
    }

    @Test
    fun CreateWithEmptyNameReturnsBadRequest() = testApplication {
        useTestDatabase()
        application { module() }
        val (_, token) = client.signUp()

        assertEquals(HttpStatusCode.BadRequest, client.postJson("/organizations", """{"name":"   "}""", token).status)
    }

    @Test
    fun CreateWithoutValidTokenReturnsUnauthorized() = testApplication {
        useTestDatabase()
        application { module() }

        assertEquals(HttpStatusCode.Unauthorized, client.postJson("/organizations", """{"name":"Rent Co"}""").status)
        assertEquals(HttpStatusCode.Unauthorized, client.postJson("/organizations", """{"name":"Rent Co"}""", "unknown").status)
    }
}
