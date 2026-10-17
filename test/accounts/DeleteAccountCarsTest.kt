package com.example.accounts

import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.amper.ktor.module
import kotlin.test.*

class DeleteAccountCarsTest {
    @BeforeTest
    fun reset() = UserRepository(TestDatabase.dataSource).clear()

    private suspend fun HttpClient.postJson(path: String, body: String, token: String? = null) =
        post(path) {
            token?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    // Registers and logs in a user, returning its id and token.
    private suspend fun HttpClient.signUp(email: String): Pair<Int, String> {
        val registration = postJson("/accounts", """{"name":"Jan","email":"$email","password":"supersecret"}""")
        val id = Json.parseToJsonElement(registration.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.int
        val login = postJson("/login", """{"email":"$email","password":"supersecret"}""")
        return id to Json.parseToJsonElement(login.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
    }

    private suspend fun HttpClient.addCar(licensePlate: String, token: String) = postJson("/cars", """{
        "type":"BEV","brand":"Test Motors","licensePlate":"$licensePlate","mileage":1,"rate":45.5,
        "location":{"latitude":52.3,"longitude":4.9},"doors":4,"bodyStyle":"Sedan","modelYear":2024,
        "power":150,"transmission":"Automatic","color":"Blue","capacity":75.0,"consumption":16.2}""", token)

    private suspend fun HttpClient.licensePlates() =
        Json.parseToJsonElement(get("/cars").bodyAsText()).jsonArray.map { it.jsonObject["licensePlate"]!!.jsonPrimitive.content }

    @Test
    fun DeletingAnAccountRemovesItsCarsAndKeepsOtherCars() = testApplication {
        useTestDatabase()
        application { module() }
        val (_, jan) = client.signUp("jan@example.com")
        val (pietId, piet) = client.signUp("piet@example.com")
        assertEquals(HttpStatusCode.Created, client.addCar("DEL-1", jan).status)
        assertEquals(HttpStatusCode.Created, client.addCar("DEL-2", jan).status)
        assertEquals(HttpStatusCode.Created, client.addCar("KEEP-1", piet).status)
        val janId = Json.parseToJsonElement(client.get("/cars").bodyAsText()).jsonArray
            .first { it.jsonObject["licensePlate"]!!.jsonPrimitive.content == "DEL-1" }.jsonObject["ownerId"]!!.jsonPrimitive.int

        try {
            assertEquals(HttpStatusCode.NoContent, client.delete("/accounts/$janId") { bearerAuth(jan) }.status)

            val plates = client.licensePlates()
            assertFalse("DEL-1" in plates || "DEL-2" in plates)
            assertTrue("KEEP-1" in plates)
            assertTrue(plates.size >= 10) // the sample fleet stays too
        } finally {
            client.delete("/accounts/$pietId") { bearerAuth(piet) } // leaves no cars behind for other tests
        }
        assertFalse("KEEP-1" in client.licensePlates())
    }
}
