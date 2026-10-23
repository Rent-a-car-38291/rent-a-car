package com.example.organizations

import com.example.accounts.UserRepository
import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import com.example.reservations.CancelResult
import com.example.reservations.Daypart
import com.example.reservations.ReservationRepository
import com.example.trips.TripRepository
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
import kotlinx.serialization.json.*
import org.jetbrains.amper.ktor.module
import java.time.LocalDate
import kotlin.test.*

class OrganizationReportRoutesTest {
    private val reservations = ReservationRepository(TestDatabase.dataSource)
    private val trips = TripRepository(TestDatabase.dataSource)

    @BeforeTest
    fun reset() {
        OrganizationRepository(TestDatabase.dataSource).clear() // also removes the organization cars
        UserRepository(TestDatabase.dataSource).clear() // and with the users their reservations and trips
    }

    private class Account(val id: Int, val token: String)

    // Organization 1 has an admin, a member and two cars; an outsider has an organization and car of their own.
    private class Setup(val client: HttpClient, val organizationId: Int, val admin: Account, val member: Account, val outsider: Account, val cars: List<Int>, val otherOrganizationId: Int)

    private suspend fun HttpClient.postJson(path: String, body: String = "", token: String? = null) =
        post(path) {
            token?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun HttpResponse.json() = Json.parseToJsonElement(bodyAsText())

    private suspend fun HttpClient.signUp(email: String): Account {
        val id = postJson("/accounts", """{"name":"Jan","email":"$email","password":"supersecret"}""").json().jsonObject["id"]!!.jsonPrimitive.int
        return Account(id, postJson("/login", """{"email":"$email","password":"supersecret"}""").json().jsonObject["token"]!!.jsonPrimitive.content)
    }

    // A BEV of 16.2 kWh per 100 km without costs: 0.0567 per km, 850.50 per year at 15000 km.
    private fun carRequest(licensePlate: String, organizationId: Int) = """{
        "type":"BEV","brand":"Test Motors","licensePlate":"$licensePlate","mileage":1,"rate":45.5,
        "location":{"latitude":52.3,"longitude":4.9},"doors":4,"bodyStyle":"Sedan","modelYear":2024,
        "power":150,"transmission":"Automatic","color":"Blue","capacity":75.0,"consumption":16.2,"organizationId":$organizationId}"""

    private suspend fun HttpClient.addCar(licensePlate: String, organizationId: Int, token: String): Int {
        val response = postJson("/cars", carRequest(licensePlate, organizationId), token)
        assertEquals(HttpStatusCode.Created, response.status)
        return response.json().jsonObject["id"]!!.jsonPrimitive.int
    }

    private suspend fun HttpClient.organization(name: String, admin: Account) =
        postJson("/organizations", """{"name":"$name"}""", admin.token).json().jsonObject["id"]!!.jsonPrimitive.int

    private suspend fun ApplicationTestBuilder.setup(): Setup {
        useTestDatabase()
        application { module() }
        val admin = client.signUp("admin@example.com")
        val member = client.signUp("member@example.com")
        val outsider = client.signUp("outsider@example.com")
        val organizationId = client.organization("Rent Co", admin)
        val code = client.postJson("/organizations/$organizationId/invites", token = admin.token).json().jsonObject["code"]!!.jsonPrimitive.content
        assertEquals(HttpStatusCode.NoContent, client.postJson("/invites/$code/accept", token = member.token).status)
        val otherOrganizationId = client.organization("Other Co", outsider)
        client.addCar("OTHER-1", otherOrganizationId, outsider.token)
        val cars = listOf(client.addCar("ORG-1", organizationId, member.token), client.addCar("ORG-2", organizationId, admin.token))
        return Setup(client, organizationId, admin, member, outsider, cars, otherOrganizationId)
    }

    // The API only takes a trip for a reservation that has started, but a test needs trips on chosen dates, so it saves them directly.
    private fun trip(carId: Int, user: Account, date: String, daypart: Daypart, km: Int, score: Int) {
        val reservation = reservations.reserve(carId, user.id, LocalDate.parse(date), daypart)!!
        trips.record(reservation.id, user.id, km, score, 0)
    }

    // Car 1: two finished trips (40 km, scores 100 and 61), one reservation without a trip and one cancelled. Car 2 is unused.
    private fun usage(s: Setup) {
        trip(s.cars[0], s.member, "2026-01-01", Daypart.MORNING, 10, 100)
        trip(s.cars[0], s.member, "2026-01-02", Daypart.AFTERNOON, 30, 61)
        reservations.reserve(s.cars[0], s.member.id, LocalDate.parse("2030-01-03"), Daypart.EVENING)!!
        val cancelled = reservations.reserve(s.cars[0], s.member.id, LocalDate.parse("2030-01-04"), Daypart.MORNING)!!
        assertEquals(CancelResult.CANCELLED, reservations.cancel(cancelled.id))
    }

    private suspend fun Setup.report(token: String?, path: String = "/organizations/$organizationId/report") =
        client.get(path) { token?.let { bearerAuth(it) } }

    @Test
    fun AMemberGetsOneRowPerCarOfTheOrganization() = testApplication {
        val s = setup()
        usage(s)

        val response = s.report(s.member.token)

        assertEquals(HttpStatusCode.OK, response.status)
        val rows = response.json().jsonArray.map { it.jsonObject }
        assertEquals(s.cars, rows.map { it["carId"]!!.jsonPrimitive.int })
        val used = rows[0]
        assertEquals(listOf(3, 2, 40), listOf("reservations", "trips", "totalKm").map { used[it]!!.jsonPrimitive.int })
        assertEquals(80.5, used["averageScore"]!!.jsonPrimitive.double)
        assertEquals(850.5, used["tcoPerYear"]!!.jsonPrimitive.double)
        assertEquals("ORG-1", used["licensePlate"]!!.jsonPrimitive.content)
        val unused = rows[1]
        assertEquals(listOf(0, 0, 0), listOf("reservations", "trips", "totalKm").map { unused[it]!!.jsonPrimitive.int })
        assertEquals(JsonNull, unused["averageScore"])
        assertEquals(850.5, unused["tcoPerYear"]!!.jsonPrimitive.double)
    }

    @Test
    fun AnAdminGetsTheSameReport() = testApplication {
        val s = setup()
        usage(s)

        assertEquals(s.report(s.member.token).bodyAsText(), s.report(s.admin.token).bodyAsText())
    }

    @Test
    fun AnOrganizationWithoutCarsGetsAnEmptyReport() = testApplication {
        val s = setup()
        val empty = s.client.organization("Empty Co", s.admin)

        val response = s.report(s.admin.token, "/organizations/$empty/report")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(0, response.json().jsonArray.size)
    }

    @Test
    fun CsvReturnsTheSameRowsWithAHeader() = testApplication {
        val s = setup()
        usage(s)

        val response = s.report(s.member.token, "/organizations/${s.organizationId}/report?format=csv")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.CSV, response.contentType()?.withoutParameters())
        assertEquals(
            "carId,brand,licensePlate,reservations,trips,totalKm,averageScore,tcoPerYear\r\n" +
                "${s.cars[0]},Test Motors,ORG-1,3,2,40,80.5,850.50\r\n" +
                "${s.cars[1]},Test Motors,ORG-2,0,0,0,,850.50\r\n",
            response.bodyAsText(),
        )
    }

    @Test
    fun JsonCanBeAskedForExplicitly() = testApplication {
        val s = setup()

        assertEquals(HttpStatusCode.OK, s.report(s.member.token, "/organizations/${s.organizationId}/report?format=json").status)
    }

    @Test
    fun AnUnknownFormatGivesBadRequest() = testApplication {
        val s = setup()

        assertEquals(HttpStatusCode.BadRequest, s.report(s.member.token, "/organizations/${s.organizationId}/report?format=xml").status)
    }

    @Test
    fun WithoutLoginTheReportGivesUnauthorized() = testApplication {
        val s = setup()

        assertEquals(HttpStatusCode.Unauthorized, s.report(null).status)
    }

    @Test
    fun ANonMemberGetsForbiddenEvenWithAnAdminOfAnotherOrganization() = testApplication {
        val s = setup()

        assertEquals(HttpStatusCode.Forbidden, s.report(s.outsider.token).status)
    }

    @Test
    fun AnUnknownOrganizationGivesNotFound() = testApplication {
        val s = setup()

        assertEquals(HttpStatusCode.NotFound, s.report(s.member.token, "/organizations/999999/report").status)
    }

    @Test
    fun TheReportOfAnotherOrganizationNeverShowsUpInTheOwnReport() = testApplication {
        val s = setup()

        val plates = s.report(s.member.token).json().jsonArray.map { it.jsonObject["licensePlate"]!!.jsonPrimitive.content }

        assertEquals(listOf("ORG-1", "ORG-2"), plates)
    }
}
