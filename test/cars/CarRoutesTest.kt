package com.example.cars

import com.example.database.useTestDatabase
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import org.jetbrains.amper.ktor.module
import kotlin.test.*

class CarRoutesTest {
    @Test
    fun GetCarsReturnsAllThreeCarTypes() = testApplication {
        // Arrange
        useTestDatabase()
        application {
            module()
        }

        // Act
        val response = client.get("/cars").bodyAsText()
        val cars = Json.parseToJsonElement(response).jsonArray
        val types = cars.map { it.jsonObject["type"]?.jsonPrimitive?.content }

        // Assert
        assertTrue(cars.size in 10..15)
        assertEquals(setOf("BEV", "ICE", "FCEV"), types.toSet())
    }

    @Test
    fun GetCarsReturnsOkWithJsonList() = testApplication {
        // Arrange
        useTestDatabase()
        application {
            module()
        }

        // Act
        val response = client.get("/cars")

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(true, response.contentType()?.match(ContentType.Application.Json))
    }
}