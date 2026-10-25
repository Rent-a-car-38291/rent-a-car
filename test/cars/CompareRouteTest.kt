package com.example.cars

import com.example.accounts.UserRepository
import com.example.accounts.configureSecurity
import com.example.accounts.TokenRepository
import com.example.configureSerialization
import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import com.example.organizations.OrganizationRepository
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import kotlin.test.*

class CompareRouteTest {
    // No hydrogen car: that type must be left out.
    private val repository = InMemoryCarRepository().apply {
        cars += BEVCar(7, 1, "Tesla", "EV-1", 0, CarStatus.Available, 55.0, Location(0.0, 0.0), 4, BodyStyle.Sedan, 2021, 208, Transmission.Automatic, "White", 60.0, 15.0)
        cars += ICECar(8, 1, "Opel", "IC-1", 0, CarStatus.Available, 30.0, Location(0.0, 0.0), 5, BodyStyle.Hatchback, 2019, 90, Transmission.Manual, "Grey", FuelType.Petrol, 45.0, 6.0)
    }

    private fun withApp(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = testApplication {
        useTestDatabase()
        application {
            configureSerialization()
            configureSecurity(UserRepository(TestDatabase.dataSource), TokenRepository(TestDatabase.dataSource))
            configureCars(repository, OrganizationRepository(TestDatabase.dataSource))
        }
        block()
    }

    @Test
    fun ComparesTheTypesInTheFleetWithoutLogin() = withApp {
        val response = client.get("/cars/compare?distanceKm=100") // GET /cars/{id} needs a login, so this also shows the route is not read as an id

        assertEquals(HttpStatusCode.OK, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(100.0, json["distanceKm"]!!.jsonPrimitive.double)
        assertEquals("EUR", json["currency"]!!.jsonPrimitive.content)
        val types = json["types"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("BEV", "ICE"), types.map { it["type"]!!.jsonPrimitive.content })
        assertEquals(5.25, types[0]["averageCost"]!!.jsonPrimitive.double, 1e-9) // 0.15 kWh * 0.35 EUR * 100
        assertEquals(0.0, types[0]["co2Kg"]!!.jsonPrimitive.double)
        assertEquals(12.6, types[1]["averageCost"]!!.jsonPrimitive.double, 1e-9) // 0.06 l * 2.10 EUR * 100
        assertEquals(13.8, types[1]["co2Kg"]!!.jsonPrimitive.double, 1e-9) // 0.06 l * 2.3 kg * 100
        assertEquals(1, types[1]["cars"]!!.jsonPrimitive.int)
    }

    @Test
    fun MissingOrOutOfRangeDistanceReturns400() = withApp {
        listOf("/cars/compare", "/cars/compare?distanceKm=abc", "/cars/compare?distanceKm=0", "/cars/compare?distanceKm=10001")
            .forEach { assertEquals(HttpStatusCode.BadRequest, client.get(it).status, it) }
    }

    @Test
    fun AnEmptyFleetGivesNoTypes() = withApp {
        repository.cars.clear()

        val json = Json.parseToJsonElement(client.get("/cars/compare?distanceKm=10").bodyAsText()).jsonObject

        assertEquals(0, json["types"]!!.jsonArray.size)
    }
}
