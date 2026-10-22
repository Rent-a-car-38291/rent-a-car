package com.example.reservations

import com.example.accounts.UserRepository
import com.example.cars.CarStatus
import com.example.cars.PostgresCarRepository
import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*
import org.jetbrains.amper.ktor.module
import java.time.LocalDate
import kotlin.test.*

class ReservationRoutesTest {
    private val cars = PostgresCarRepository(TestDatabase.dataSource)
    private val availableCar = cars.allCars().first { it.status == CarStatus.Available }.id
    private val maintenanceCar = cars.allCars().first { it.status == CarStatus.Maintenance }.id

    @BeforeTest
    fun reset() = UserRepository(TestDatabase.dataSource).clear()

    private fun withApp(block: suspend ApplicationTestBuilder.(token: String) -> Unit) = testApplication {
        useTestDatabase()
        application { module() }
        val register = client.post("/accounts") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Jan","email":"jan@example.com","password":"supersecret"}""")
        }
        assertEquals(HttpStatusCode.Created, register.status)
        val login = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"jan@example.com","password":"supersecret"}""")
        }
        block(Json.parseToJsonElement(login.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content)
    }

    private suspend fun HttpClient.reserve(carId: Int, date: String, daypart: String, token: String? = null): HttpResponse =
        post("/cars/$carId/reservations") {
            token?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            setBody("""{"date":"$date","daypart":"$daypart"}""")
        }

    private suspend fun HttpClient.freeDayparts(carId: Int, date: String): List<String> =
        Json.parseToJsonElement(get("/cars/$carId/availability?date=$date").bodyAsText()).jsonObject["freeDayparts"]!!.jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun ReservingAFreeDaypartGives201WithTheReservation() = withApp { token ->
        val response = client.reserve(availableCar, "2030-03-01", "MORNING", token)

        assertEquals(HttpStatusCode.Created, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(availableCar, json["carId"]!!.jsonPrimitive.int)
        assertEquals("2030-03-01", json["date"]!!.jsonPrimitive.content)
        assertEquals("MORNING", json["daypart"]!!.jsonPrimitive.content)
        assertNotNull(json["userId"])
    }

    @Test
    fun TodayCanBeReserved() = withApp { token ->
        assertEquals(HttpStatusCode.Created, client.reserve(availableCar, LocalDate.now(ZONE).toString(), "EVENING", token).status)
    }

    @Test
    fun ATakenDaypartGives409ButAnotherDaypartAndAnotherDateWork() = withApp { token ->
        assertEquals(HttpStatusCode.Created, client.reserve(availableCar, "2030-03-02", "AFTERNOON", token).status)

        assertEquals(HttpStatusCode.Conflict, client.reserve(availableCar, "2030-03-02", "AFTERNOON", token).status)
        assertEquals(HttpStatusCode.Created, client.reserve(availableCar, "2030-03-02", "EVENING", token).status)
        assertEquals(HttpStatusCode.Created, client.reserve(availableCar, "2030-03-03", "AFTERNOON", token).status)
    }

    @Test
    fun ACarInMaintenanceGives409() = withApp { token ->
        assertEquals(HttpStatusCode.Conflict, client.reserve(maintenanceCar, "2030-03-04", "MORNING", token).status)
    }

    @Test
    fun APastDateUnknownDaypartOrBadDateGives400() = withApp { token ->
        assertEquals(HttpStatusCode.BadRequest, client.reserve(availableCar, LocalDate.now(ZONE).minusDays(1).toString(), "MORNING", token).status)
        assertEquals(HttpStatusCode.BadRequest, client.reserve(availableCar, "2030-03-05", "NIGHT", token).status)
        assertEquals(HttpStatusCode.BadRequest, client.reserve(availableCar, "tomorrow", "MORNING", token).status)
        assertEquals(HttpStatusCode.BadRequest, client.reserve(availableCar, "2030-02-30", "MORNING", token).status)
    }

    @Test
    fun AnUnknownCarGives404() = withApp { token ->
        assertEquals(HttpStatusCode.NotFound, client.reserve(-42, "2030-03-06", "MORNING", token).status)
    }

    @Test
    fun WithoutATokenGives401() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.reserve(availableCar, "2030-03-07", "MORNING").status)
    }

    @Test
    fun AvailabilityLeavesOutReservedDayparts() = withApp { token ->
        assertEquals(listOf("MORNING", "AFTERNOON", "EVENING"), client.freeDayparts(availableCar, "2030-03-08"))

        client.reserve(availableCar, "2030-03-08", "AFTERNOON", token)

        assertEquals(listOf("MORNING", "EVENING"), client.freeDayparts(availableCar, "2030-03-08"))
        assertEquals(listOf("MORNING", "AFTERNOON", "EVENING"), client.freeDayparts(availableCar, "2030-03-09"))
    }

    @Test
    fun AvailabilityOfACarInMaintenanceIsEmpty() = withApp {
        assertEquals(emptyList(), client.freeDayparts(maintenanceCar, "2030-03-10"))
    }

    @Test
    fun AvailabilityNeedsAKnownCarAndAValidDate() = withApp {
        assertEquals(HttpStatusCode.NotFound, client.get("/cars/-42/availability?date=2030-03-11").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/cars/$availableCar/availability").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/cars/$availableCar/availability?date=soon").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/cars/abc/availability?date=2030-03-11").status)
    }

    @Test
    fun TwoRequestsForTheSameDaypartAtOnceGiveExactlyOne201() = withApp { token ->
        val statuses = coroutineScope { (1..8).map { async { client.reserve(availableCar, "2030-03-12", "MORNING", token).status } }.awaitAll() }

        assertEquals(1, statuses.count { it == HttpStatusCode.Created })
        assertEquals(7, statuses.count { it == HttpStatusCode.Conflict })
    }
}
