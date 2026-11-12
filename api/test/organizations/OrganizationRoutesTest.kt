package com.example.organizations

import com.example.accounts.UserRepository
import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.put
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

    private suspend fun HttpClient.createOrganization(token: String) =
        postJson("/organizations", """{"name":"Rent Co"}""", token).json()["id"]!!.jsonPrimitive.int

    private suspend fun HttpClient.invite(organizationId: Int, token: String) =
        postJson("/organizations/$organizationId/invites", "", token)

    private suspend fun HttpClient.accept(code: String, token: String? = null) =
        post("/invites/$code/accept") { token?.let { bearerAuth(it) } }

    private suspend fun HttpClient.inviteCode(organizationId: Int, token: String) =
        invite(organizationId, token).json()["code"]!!.jsonPrimitive.content

    @Test
    fun InviteReturnsCreatedWithACodeThatLetsAUserJoinOnce() = testApplication {
        useTestDatabase()
        application { module() }
        val (_, admin) = client.signUp()
        val (userId, token) = client.signUp("piet@example.com")
        val (_, other) = client.signUp("kees@example.com")
        val organizationId = client.createOrganization(admin)

        val response = client.invite(organizationId, admin)
        assertEquals(HttpStatusCode.Created, response.status)
        val code = response.json()["code"]!!.jsonPrimitive.content

        assertEquals(HttpStatusCode.NoContent, client.accept(code, token).status)
        assertEquals(Role.MEMBER, OrganizationRepository(TestDatabase.dataSource).findMembership(organizationId, userId)?.role)
        assertEquals(HttpStatusCode.NotFound, client.accept(code, other).status)
    }

    @Test
    fun InviteExpiresAfterSevenDays() = testApplication {
        useTestDatabase()
        application { module() }
        val (_, admin) = client.signUp()
        client.inviteCode(client.createOrganization(admin), admin)

        val validDays = TestDatabase.dataSource.connection.use { it.createStatement().use { s ->
            s.executeQuery("SELECT extract(epoch FROM expires_at - now()) / 86400 FROM invites").use { rows -> rows.next(); rows.getDouble(1) }
        } }
        assertEquals(7.0, validDays, 0.01)
    }

    @Test
    fun InviteCodesDiffer() = testApplication {
        useTestDatabase()
        application { module() }
        val (_, admin) = client.signUp()
        val organizationId = client.createOrganization(admin)

        assertNotEquals(client.inviteCode(organizationId, admin), client.inviteCode(organizationId, admin))
    }

    @Test
    fun InviteWithoutValidTokenReturnsUnauthorized() = testApplication {
        useTestDatabase()
        application { module() }

        assertEquals(HttpStatusCode.Unauthorized, client.postJson("/organizations/1/invites", "").status)
    }

    @Test
    fun InviteByAMemberReturnsForbidden() = testApplication {
        useTestDatabase()
        application { module() }
        val (_, admin) = client.signUp()
        val (_, member) = client.signUp("piet@example.com")
        val (_, outsider) = client.signUp("kees@example.com")
        val organizationId = client.createOrganization(admin)
        client.accept(client.inviteCode(organizationId, admin), member)

        assertEquals(HttpStatusCode.Forbidden, client.invite(organizationId, member).status)
        assertEquals(HttpStatusCode.Forbidden, client.invite(organizationId, outsider).status)
    }

    @Test
    fun InviteForUnknownOrganizationReturnsNotFound() = testApplication {
        useTestDatabase()
        application { module() }
        val (_, token) = client.signUp()

        assertEquals(HttpStatusCode.NotFound, client.invite(999, token).status)
    }

    @Test
    fun AcceptWithoutValidTokenReturnsUnauthorized() = testApplication {
        useTestDatabase()
        application { module() }

        assertEquals(HttpStatusCode.Unauthorized, client.accept("code").status)
    }

    @Test
    fun AcceptUnknownCodeReturnsNotFound() = testApplication {
        useTestDatabase()
        application { module() }
        val (_, token) = client.signUp()

        assertEquals(HttpStatusCode.NotFound, client.accept("unknown", token).status)
    }

    @Test
    fun AcceptExpiredCodeReturnsNotFound() = testApplication {
        useTestDatabase()
        application { module() }
        val (_, admin) = client.signUp()
        val (_, token) = client.signUp("piet@example.com")
        val code = client.inviteCode(client.createOrganization(admin), admin)
        TestDatabase.dataSource.connection.use { it.createStatement().use { s -> s.execute("UPDATE invites SET expires_at = now() - interval '1 second'") } }

        assertEquals(HttpStatusCode.NotFound, client.accept(code, token).status)
    }

    @Test
    fun AcceptByAMemberReturnsConflictAndKeepsTheCodeValid() = testApplication {
        useTestDatabase()
        application { module() }
        val (_, admin) = client.signUp()
        val (_, token) = client.signUp("piet@example.com")
        val code = client.inviteCode(client.createOrganization(admin), admin)

        assertEquals(HttpStatusCode.Conflict, client.accept(code, admin).status)
        assertEquals(HttpStatusCode.NoContent, client.accept(code, token).status)
    }

    private suspend fun HttpClient.setRole(organizationId: Int, userId: Int, role: String, token: String? = null) =
        put("/organizations/$organizationId/members/$userId") {
            token?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            setBody("""{"role":"$role"}""")
        }

    private suspend fun HttpClient.removeMember(organizationId: Int, userId: Int, token: String? = null) =
        delete("/organizations/$organizationId/members/$userId") { token?.let { bearerAuth(it) } }

    private data class Team(val organizationId: Int, val adminId: Int, val admin: String, val memberId: Int, val member: String, val outsiderId: Int, val outsider: String)

    // An organization with an admin and a member, plus a user outside it.
    private suspend fun HttpClient.team(): Team {
        val (adminId, admin) = signUp()
        val (memberId, member) = signUp("piet@example.com")
        val (outsiderId, outsider) = signUp("kees@example.com")
        val organizationId = createOrganization(admin)
        accept(inviteCode(organizationId, admin), member)
        return Team(organizationId, adminId, admin, memberId, member, outsiderId, outsider)
    }

    private fun role(organizationId: Int, userId: Int) =
        OrganizationRepository(TestDatabase.dataSource).findMembership(organizationId, userId)?.role

    @Test
    fun AdminPromotesAMemberAndTheyCanThenInvite() = testApplication {
        useTestDatabase()
        application { module() }
        val t = client.team()

        val response = client.setRole(t.organizationId, t.memberId, "ADMIN", t.admin)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ADMIN", response.json()["role"]!!.jsonPrimitive.content)
        assertEquals(HttpStatusCode.Created, client.invite(t.organizationId, t.member).status)
    }

    @Test
    fun AdminDemotesAnotherAdmin() = testApplication {
        useTestDatabase()
        application { module() }
        val t = client.team()
        client.setRole(t.organizationId, t.memberId, "ADMIN", t.admin)

        assertEquals(HttpStatusCode.OK, client.setRole(t.organizationId, t.adminId, "MEMBER", t.member).status)
        assertEquals(Role.MEMBER, role(t.organizationId, t.adminId))
    }

    @Test
    fun SetRoleByAMemberOrOutsiderReturnsForbidden() = testApplication {
        useTestDatabase()
        application { module() }
        val t = client.team()

        assertEquals(HttpStatusCode.Forbidden, client.setRole(t.organizationId, t.memberId, "ADMIN", t.member).status)
        assertEquals(HttpStatusCode.Forbidden, client.setRole(t.organizationId, t.memberId, "ADMIN", t.outsider).status)
        assertEquals(Role.MEMBER, role(t.organizationId, t.memberId))
    }

    @Test
    fun SetRoleWithoutValidTokenReturnsUnauthorized() = testApplication {
        useTestDatabase()
        application { module() }

        assertEquals(HttpStatusCode.Unauthorized, client.setRole(1, 1, "ADMIN").status)
    }

    @Test
    fun SetRoleOfUnknownOrganizationOrMemberReturnsNotFound() = testApplication {
        useTestDatabase()
        application { module() }
        val t = client.team()

        assertEquals(HttpStatusCode.NotFound, client.setRole(999, t.memberId, "ADMIN", t.admin).status)
        assertEquals(HttpStatusCode.NotFound, client.setRole(t.organizationId, t.outsiderId, "ADMIN", t.admin).status)
    }

    @Test
    fun SetRoleWithInvalidRoleReturnsBadRequest() = testApplication {
        useTestDatabase()
        application { module() }
        val t = client.team()

        assertEquals(HttpStatusCode.BadRequest, client.setRole(t.organizationId, t.memberId, "OWNER", t.admin).status)
    }

    @Test
    fun DemotingTheLastAdminReturnsConflict() = testApplication {
        useTestDatabase()
        application { module() }
        val t = client.team()

        assertEquals(HttpStatusCode.Conflict, client.setRole(t.organizationId, t.adminId, "MEMBER", t.admin).status)
        assertEquals(Role.ADMIN, role(t.organizationId, t.adminId))
    }

    @Test
    fun AdminRemovesAMember() = testApplication {
        useTestDatabase()
        application { module() }
        val t = client.team()

        assertEquals(HttpStatusCode.NoContent, client.removeMember(t.organizationId, t.memberId, t.admin).status)
        assertNull(role(t.organizationId, t.memberId))
    }

    @Test
    fun MemberLeaves() = testApplication {
        useTestDatabase()
        application { module() }
        val t = client.team()

        assertEquals(HttpStatusCode.NoContent, client.removeMember(t.organizationId, t.memberId, t.member).status)
        assertNull(role(t.organizationId, t.memberId))
    }

    @Test
    fun MemberRemovingSomeoneElseReturnsForbidden() = testApplication {
        useTestDatabase()
        application { module() }
        val t = client.team()

        assertEquals(HttpStatusCode.Forbidden, client.removeMember(t.organizationId, t.adminId, t.member).status)
        assertEquals(HttpStatusCode.Forbidden, client.removeMember(t.organizationId, t.memberId, t.outsider).status)
        assertEquals(Role.ADMIN, role(t.organizationId, t.adminId))
    }

    @Test
    fun RemoveWithoutValidTokenReturnsUnauthorized() = testApplication {
        useTestDatabase()
        application { module() }

        assertEquals(HttpStatusCode.Unauthorized, client.removeMember(1, 1).status)
    }

    @Test
    fun RemoveUnknownOrganizationOrMemberReturnsNotFound() = testApplication {
        useTestDatabase()
        application { module() }
        val t = client.team()

        assertEquals(HttpStatusCode.NotFound, client.removeMember(999, t.memberId, t.admin).status)
        assertEquals(HttpStatusCode.NotFound, client.removeMember(t.organizationId, t.outsiderId, t.admin).status)
        assertEquals(HttpStatusCode.NotFound, client.removeMember(t.organizationId, t.outsiderId, t.outsider).status)
    }

    @Test
    fun TheLastAdminCantLeaveOrBeRemoved() = testApplication {
        useTestDatabase()
        application { module() }
        val t = client.team()

        assertEquals(HttpStatusCode.Conflict, client.removeMember(t.organizationId, t.adminId, t.admin).status)
        assertEquals(Role.ADMIN, role(t.organizationId, t.adminId))
    }

    @Test
    fun AnAdminCanLeaveWhenAnotherAdminRemains() = testApplication {
        useTestDatabase()
        application { module() }
        val t = client.team()
        client.setRole(t.organizationId, t.memberId, "ADMIN", t.admin)

        assertEquals(HttpStatusCode.NoContent, client.removeMember(t.organizationId, t.adminId, t.admin).status)
    }

    @Test
    fun TheLastAdminCantDeleteTheirAccountUntilAnotherAdminExists() = testApplication {
        useTestDatabase()
        application { module() }
        val t = client.team()

        assertEquals(HttpStatusCode.Conflict, client.delete("/accounts/${t.adminId}") { bearerAuth(t.admin) }.status)

        client.setRole(t.organizationId, t.memberId, "ADMIN", t.admin)
        assertEquals(HttpStatusCode.NoContent, client.delete("/accounts/${t.adminId}") { bearerAuth(t.admin) }.status)
        assertNull(role(t.organizationId, t.adminId))
    }
}
