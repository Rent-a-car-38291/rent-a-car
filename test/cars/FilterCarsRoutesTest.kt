package com.example.cars

import com.example.accounts.UserRepository
import com.example.accounts.configureSecurity
import com.example.configureSerialization
import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class FilterCarsRoutesTest {
    private class FixedCarRepository(private val cars: List<Car>) : CarRepository {
        override fun allCars() = cars
        override fun addCar(car: Car) = car
    }

    private fun car(type: CarType, plate: String, rate: Double, lat: Double, lon: Double) = AddCarRequest(
        type, "Test", plate, 100, rate, Location(lat, lon), 4, BodyStyle.Sedan, 2020, 100, Transmission.Manual, "Red",
        fuelType = FuelType.Petrol.takeIf { type == CarType.ICE }, capacity = 50.0, consumption = 5.0,
    ).toCar(1)

    // Breda centre, about 2 km from Breda centre, and Amsterdam (far away).
    private val repository = FixedCarRepository(listOf(
        car(CarType.BEV, "FAR", 30.0, 52.37, 4.90),
        car(CarType.ICE, "NEAR", 50.0, 51.60, 4.80),
        car(CarType.BEV, "NEARER", 40.0, 51.59, 4.78),
    ))

    private suspend fun plates(query: String, block: suspend (HttpStatusCode, List<String>) -> Unit) = testApplication {
        useTestDatabase()
        application {
            configureSerialization()
            configureSecurity(UserRepository(TestDatabase.dataSource))
            configureCars(repository)
        }
        val response = client.get("/cars$query")
        val plates = if (response.status == HttpStatusCode.OK) {
            Json.parseToJsonElement(response.bodyAsText()).jsonArray.map { it.jsonObject["licensePlate"]!!.jsonPrimitive.content }
        } else emptyList()
        block(response.status, plates)
    }

    private fun check(query: String, expected: List<String>) = kotlinx.coroutines.runBlocking {
        plates(query) { status, plates ->
            assertEquals(HttpStatusCode.OK, status, query)
            assertEquals(expected, plates, query)
        }
    }

    private fun checkBadRequest(query: String) = kotlinx.coroutines.runBlocking {
        plates(query) { status, _ -> assertEquals(HttpStatusCode.BadRequest, status, query) }
    }

    @Test
    fun WithoutFilterReturnsAllCars() = check("", listOf("FAR", "NEAR", "NEARER"))

    @Test
    fun FiltersOnType() = check("?type=BEV", listOf("FAR", "NEARER"))

    @Test
    fun FiltersOnMaxRate() = check("?maxRate=40", listOf("FAR", "NEARER"))

    @Test
    fun FiltersOnDistanceAndSortsNearestFirst() = check("?lat=51.59&lon=4.78&maxDistanceKm=5", listOf("NEARER", "NEAR"))

    @Test
    fun SortsNearestFirstWithoutMaxDistance() = check("?lat=51.59&lon=4.78", listOf("NEARER", "NEAR", "FAR"))

    @Test
    fun CombinesFilters() = check("?type=BEV&maxRate=35&lat=51.59&lon=4.78&maxDistanceKm=500", listOf("FAR"))

    @Test
    fun InvalidFiltersReturnBadRequest() {
        checkBadRequest("?type=SUV")
        checkBadRequest("?type=bev")
        checkBadRequest("?maxRate=cheap")
        checkBadRequest("?lat=abc&lon=4.78&maxDistanceKm=5")
        checkBadRequest("?maxDistanceKm=5")
        checkBadRequest("?lat=51.59&maxDistanceKm=5")
        checkBadRequest("?lat=51.59&lon=4.78&maxDistanceKm=far")
        checkBadRequest("?lat=95&lon=4.78")
    }
}
