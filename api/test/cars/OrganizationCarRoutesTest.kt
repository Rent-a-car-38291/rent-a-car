package com.example.cars

import com.example.accounts.UserRepository
import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import com.example.organizations.OrganizationRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.amper.ktor.module
import kotlin.test.*

class OrganizationCarRoutesTest {
    @BeforeTest
    fun reset() {
        OrganizationRepository(TestDatabase.dataSource).clear() // also removes the organization cars
        UserRepository(TestDatabase.dataSource).clear()
    }

    private class Setup(val client: HttpClient, val organizationId: Int, val admin: Pair<Int, String>, val member: Pair<Int, String>, val outsider: String)

    private suspend fun HttpClient.postJson(path: String, body: String = "", token: String? = null) =
        post(path) {
            token?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun HttpResponse.json() = Json.parseToJsonElement(bodyAsText()).jsonObject

    private suspend fun HttpClient.signUp(email: String): Pair<Int, String> {
        val id = postJson("/accounts", """{"name":"Jan","email":"$email","password":"supersecret"}""").json()["id"]!!.jsonPrimitive.int
        return id to postJson("/login", """{"email":"$email","password":"supersecret"}""").json()["token"]!!.jsonPrimitive.content
    }

    private fun carRequest(licensePlate: String, organizationId: Int? = null, color: String = "Blue") = """{
        "type":"BEV","brand":"Test Motors","licensePlate":"$licensePlate","mileage":1,"rate":45.5,
        "location":{"latitude":52.3,"longitude":4.9},"doors":4,"bodyStyle":"Sedan","modelYear":2024,
        "power":150,"transmission":"Automatic","color":"$color","capacity":75.0,"consumption":16.2
        ${organizationId?.let { ""","organizationId":$it""" } ?: ""}}"""

    // An organization with an admin and a member, and a user outside it.
    private suspend fun ApplicationTestBuilder.setup(): Setup {
        useTestDatabase()
        application { module() }
        val admin = client.signUp("admin@example.com")
        val member = client.signUp("member@example.com")
        val outsider = client.signUp("outsider@example.com")
        val organizationId = client.postJson("/organizations", """{"name":"Rent Co"}""", admin.second).json()["id"]!!.jsonPrimitive.int
        val code = client.postJson("/organizations/$organizationId/invites", token = admin.second).json()["code"]!!.jsonPrimitive.content
        assertEquals(HttpStatusCode.NoContent, client.postJson("/invites/$code/accept", token = member.second).status)
        return Setup(client, organizationId, admin, member, outsider.second)
    }

    private suspend fun Setup.addCar(licensePlate: String): Int {
        val response = client.postJson("/cars", carRequest(licensePlate, organizationId), member.second)
        assertEquals(HttpStatusCode.Created, response.status)
        return response.json()["id"]!!.jsonPrimitive.int
    }

    @Test
    fun AMemberAddsAnOrganizationCarWithoutAnOwner() = testApplication {
        val s = setup()

        val response = s.client.postJson("/cars", carRequest("ORG-1", s.organizationId), s.member.second)

        assertEquals(HttpStatusCode.Created, response.status)
        val car = response.json()
        assertEquals(s.organizationId, car["organizationId"]!!.jsonPrimitive.int)
        assertEquals(JsonNull, car["ownerId"])
    }

    @Test
    fun ANonMemberOrAnUnknownOrganizationGivesForbidden() = testApplication {
        val s = setup()

        assertEquals(HttpStatusCode.Forbidden, s.client.postJson("/cars", carRequest("ORG-2", s.organizationId), s.outsider).status)
        assertEquals(HttpStatusCode.Forbidden, s.client.postJson("/cars", carRequest("ORG-2", 99999), s.member.second).status)
        assertEquals(HttpStatusCode.Unauthorized, s.client.postJson("/cars", carRequest("ORG-2", s.organizationId)).status)
        assertFalse(s.client.get("/cars").bodyAsText().contains("ORG-2"))
    }

    @Test
    fun AnInvalidOrganizationCarGivesBadRequestAndAPrivateCarHasNoOrganization() = testApplication {
        val s = setup()

        assertEquals(HttpStatusCode.BadRequest, s.client.postJson("/cars", carRequest("ORG-3", s.organizationId).replace("\"mileage\":1", "\"mileage\":-1"), s.member.second).status)
        val private = s.client.postJson("/cars", carRequest("PRIV-1"), s.member.second).json()
        assertEquals(JsonNull, private["organizationId"])
        assertNotEquals(JsonNull, private["ownerId"])
    }

    @Test
    fun AMemberUpdatesAnOrganizationCarAndANonMemberCannot() = testApplication {
        val s = setup()
        val id = s.addCar("ORG-4")

        val updated = s.client.put("/cars/$id") { bearerAuth(s.admin.second); contentType(ContentType.Application.Json); setBody(carRequest("ORG-4", color = "Red")) }
        assertEquals(HttpStatusCode.OK, updated.status)
        assertEquals("Red", updated.json()["color"]!!.jsonPrimitive.content)
        assertEquals(s.organizationId, updated.json()["organizationId"]!!.jsonPrimitive.int)

        val outsider = s.client.put("/cars/$id") { bearerAuth(s.outsider); contentType(ContentType.Application.Json); setBody(carRequest("ORG-4", color = "Green")) }
        assertEquals(HttpStatusCode.Forbidden, outsider.status)
    }

    @Test
    fun ACarCannotMoveToAnotherOrganization() = testApplication {
        val s = setup()
        val id = s.addCar("ORG-5")
        val private = s.client.postJson("/cars", carRequest("PRIV-5"), s.member.second).json()["id"]!!.jsonPrimitive.int

        val moved = s.client.put("/cars/$id") { bearerAuth(s.member.second); contentType(ContentType.Application.Json); setBody(carRequest("ORG-5", s.organizationId + 1)) }
        val intoOrganization = s.client.put("/cars/$private") { bearerAuth(s.member.second); contentType(ContentType.Application.Json); setBody(carRequest("PRIV-5", s.organizationId)) }

        assertEquals(HttpStatusCode.BadRequest, moved.status)
        assertEquals(HttpStatusCode.BadRequest, intoOrganization.status)
    }

    @Test
    fun OnlyAnAdminDeletesAnOrganizationCar() = testApplication {
        val s = setup()
        val id = s.addCar("ORG-6")

        assertEquals(HttpStatusCode.Forbidden, s.client.delete("/cars/$id") { bearerAuth(s.member.second) }.status)
        assertEquals(HttpStatusCode.Forbidden, s.client.delete("/cars/$id") { bearerAuth(s.outsider) }.status)
        assertEquals(HttpStatusCode.NoContent, s.client.delete("/cars/$id") { bearerAuth(s.admin.second) }.status)
        assertEquals(HttpStatusCode.NotFound, s.client.delete("/cars/$id") { bearerAuth(s.admin.second) }.status)
    }

    @Test
    fun AMemberSeesTheTcoOfAnOrganizationCarAndANonMemberDoesNot() = testApplication {
        val s = setup()
        val id = s.addCar("ORG-7")

        assertEquals(HttpStatusCode.OK, s.client.get("/cars/$id/tco") { bearerAuth(s.member.second) }.status)
        assertEquals(HttpStatusCode.Forbidden, s.client.get("/cars/$id/tco") { bearerAuth(s.outsider) }.status)
    }

    @Test
    fun DeletingTheAccountOfTheCreatorKeepsTheOrganizationCar() = testApplication {
        val s = setup()
        assertEquals(HttpStatusCode.Created, s.client.postJson("/cars", carRequest("ORG-8", s.organizationId), s.admin.second).status)
        s.client.postJson("/cars", carRequest("PRIV-8"), s.admin.second)
        // the member becomes admin as well, so the organization keeps one when the first admin leaves
        assertEquals(HttpStatusCode.OK, s.client.put("/organizations/${s.organizationId}/members/${s.member.first}") {
            bearerAuth(s.admin.second); contentType(ContentType.Application.Json); setBody("""{"role":"ADMIN"}""")
        }.status)

        assertEquals(HttpStatusCode.NoContent, s.client.delete("/accounts/${s.admin.first}") { bearerAuth(s.admin.second) }.status)

        val plates = Json.parseToJsonElement(s.client.get("/cars").bodyAsText()).jsonArray.map { it.jsonObject["licensePlate"]!!.jsonPrimitive.content }
        assertTrue("ORG-8" in plates)
        assertFalse("PRIV-8" in plates)
    }
}
