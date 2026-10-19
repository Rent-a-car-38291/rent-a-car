package com.example.cars

import com.example.accounts.UserRepository
import com.example.accounts.configureAccounts
import com.example.accounts.configureSecurity
import com.example.configureSerialization
import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class DeleteCarRoutesTest {
    private val repository = InMemoryCarRepository()

    private suspend fun HttpClient.signUp(email: String): Pair<Int, String> {
        val registration = post("/accounts") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Jan","email":"$email","password":"supersecret"}""")
        }
        val userId = Json.parseToJsonElement(registration.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.int
        val login = post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"supersecret"}""")
        }
        return userId to Json.parseToJsonElement(login.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
    }

    // Starts the application with an in-memory car repository and a car (id 1) owned by a new user; returns that user's token.
    private suspend fun ApplicationTestBuilder.withOwnedCar(): Pair<HttpClient, String> {
        useTestDatabase()
        UserRepository(TestDatabase.dataSource).clear()
        application {
            configureSerialization()
            val users = UserRepository(TestDatabase.dataSource)
            configureSecurity(users)
            configureAccounts(users)
            configureCars(repository)
        }
        val (ownerId, token) = client.signUp("jan@example.com")
        repository.cars.clear()
        repository.cars += AddCarRequest(
            CarType.BEV, "Old Motors", "OLD-1", 100, 40.0, Location(1.0, 2.0), 4, BodyStyle.Sedan, 2020, 100,
            Transmission.Manual, "Red", capacity = 50.0, consumption = 15.0,
        ).toCar(ownerId, 1)
        return client to token
    }

    private suspend fun HttpClient.deleteCar(id: Int, token: String? = null) =
        delete("/cars/$id") { token?.let { bearerAuth(it) } }

    @Test
    fun DeleteRemovesTheCar() = testApplication {
        val (client, token) = withOwnedCar()

        assertEquals(HttpStatusCode.NoContent, client.deleteCar(1, token).status)
        assertTrue(repository.cars.isEmpty())
        assertEquals("[]", client.get("/cars").bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.deleteCar(1, token).status)
    }

    @Test
    fun DeleteUnknownCarReturnsNotFound() = testApplication {
        val (client, token) = withOwnedCar()

        assertEquals(HttpStatusCode.NotFound, client.deleteCar(99, token).status)
        assertEquals(1, repository.cars.size)
    }

    @Test
    fun DeleteWithoutValidTokenReturnsUnauthorized() = testApplication {
        val (client, _) = withOwnedCar()

        assertEquals(HttpStatusCode.Unauthorized, client.deleteCar(1).status)
        assertEquals(HttpStatusCode.Unauthorized, client.deleteCar(1, "unknown").status)
        assertEquals(1, repository.cars.size)
    }

    @Test
    fun DeleteSomeoneElsesCarReturnsForbidden() = testApplication {
        val (client, _) = withOwnedCar()
        val (_, other) = client.signUp("piet@example.com")

        assertEquals(HttpStatusCode.Forbidden, client.deleteCar(1, other).status)
        assertEquals(1, repository.cars.size)
    }

    @Test
    fun DeleteWithNonNumericIdReturnsBadRequest() = testApplication {
        val (client, token) = withOwnedCar()

        assertEquals(HttpStatusCode.BadRequest, client.delete("/cars/abc") { bearerAuth(token) }.status)
    }
}
