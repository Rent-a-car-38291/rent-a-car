package com.example.accounts

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

class AccountRoutesTest {
    @BeforeTest
    fun reset() = UserRepository(TestDatabase.dataSource).clear()

    private fun valid(email: String = "jan@example.com") =
        """{"name":"Jan","email":"$email","password":"supersecret"}"""

    private suspend fun HttpClient.postJson(path: String, body: String) =
        post(path) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun HttpClient.register(body: String) = postJson("/accounts", body)

    private suspend fun HttpClient.login(email: String = "jan@example.com", password: String = "supersecret") =
        postJson("/login", """{"email":"$email","password":"$password"}""")

    private suspend fun HttpResponse.json() = Json.parseToJsonElement(bodyAsText()).jsonObject

    // Registers and logs in a user, returning its id and token.
    private suspend fun HttpClient.signUp(email: String = "jan@example.com"): Pair<Int, String> {
        val id = register(valid(email)).json()["id"]!!.jsonPrimitive.int
        val token = login(email).json()["token"]!!.jsonPrimitive.content
        return id to token
    }

    private suspend fun HttpClient.logout(token: String? = null) =
        post("/logout") { token?.let { bearerAuth(it) } }

    private suspend fun HttpClient.updateAccount(id: String, body: String, token: String? = null) =
        put("/accounts/$id") {
            token?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun HttpClient.changePassword(id: String, body: String, token: String? = null) =
        put("/accounts/$id/password") {
            token?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private fun passwords(current: String = "supersecret", new: String = "evenmoresecret") =
        """{"currentPassword":"$current","newPassword":"$new"}"""

    private fun profile(name: String = "Janneke", email: String = "janneke@example.com") =
        """{"name":"$name","email":"$email"}"""

    private suspend fun HttpClient.deleteAccount(id: Int, token: String? = null) =
        delete("/accounts/$id") { token?.let { bearerAuth(it) } }

    @Test
    fun RegisterReturnsCreatedWithoutPassword() = testApplication {
        useTestDatabase()
        application { module() }

        val response = client.register(valid())

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("jan@example.com"))
        assertFalse(body.contains("password"))
        assertFalse(body.contains("supersecret"))
    }

    @Test
    fun RegisterWithInvalidFieldsReturnsBadRequest() = testApplication {
        useTestDatabase()
        application { module() }

        listOf(
            """{"name":"","email":"jan@example.com","password":"supersecret"}""",
            """{"name":"Jan","email":"not-an-email","password":"supersecret"}""",
            """{"name":"Jan","email":"jan@","password":"supersecret"}""",
            """{"name":"Jan","email":"@example.com","password":"supersecret"}""",
            """{"name":"Jan","email":"jan@example","password":"supersecret"}""",
            """{"name":"Jan","email":"jan@example.com","password":"short"}""",
            """{}""",
            "not json"
        ).forEach { assertEquals(HttpStatusCode.BadRequest, client.register(it).status, it) }
    }

    @Test
    fun RegisterDuplicateEmailReturnsConflict() = testApplication {
        useTestDatabase()
        application { module() }
        client.register(valid())

        val response = client.register(valid("JAN@example.com"))

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun LoginReturnsToken() = testApplication {
        useTestDatabase()
        application { module() }
        client.register(valid())

        val response = client.login(email = "JAN@example.com")

        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(response.json()["token"]?.jsonPrimitive?.content.isNullOrBlank())
    }

    @Test
    fun LoginWithWrongCredentialsReturnsUnauthorized() = testApplication {
        useTestDatabase()
        application { module() }
        client.register(valid())

        assertEquals(HttpStatusCode.Unauthorized, client.login(password = "wrongpassword").status)
        assertEquals(HttpStatusCode.Unauthorized, client.login(email = "piet@example.com").status)
    }

    @Test
    fun DeleteOwnAccountReturnsNoContent() = testApplication {
        useTestDatabase()
        application { module() }
        val (id, token) = client.signUp()

        val response = client.deleteAccount(id, token)

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(HttpStatusCode.Unauthorized, client.login().status)
        assertEquals(HttpStatusCode.Unauthorized, client.deleteAccount(id, token).status)
    }

    @Test
    fun DeleteUnknownAccountReturnsNotFound() = testApplication {
        useTestDatabase()
        application { module() }
        val (_, token) = client.signUp()

        val response = client.deleteAccount(999, token)

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun DeleteWithoutValidTokenReturnsUnauthorized() = testApplication {
        useTestDatabase()
        application { module() }
        val (id, _) = client.signUp()

        assertEquals(HttpStatusCode.Unauthorized, client.deleteAccount(id).status)
        assertEquals(HttpStatusCode.Unauthorized, client.deleteAccount(id, "not-a-token").status)
    }

    @Test
    fun DeleteSomeoneElsesAccountReturnsForbidden() = testApplication {
        useTestDatabase()
        application { module() }
        val (otherId, _) = client.signUp("piet@example.com")
        val (_, token) = client.signUp()

        val response = client.deleteAccount(otherId, token)

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun DeletedUsersTokenIsRevoked() = testApplication {
        useTestDatabase()
        application { module() }
        val (id, token) = client.signUp()
        client.deleteAccount(id, token)

        assertNull(TokenRepository.userId(token))
        assertEquals(HttpStatusCode.Unauthorized, client.deleteAccount(id, token).status)
    }

    @Test
    fun DeletedUsersTokenDoesNotWorkForNewUser() = testApplication {
        useTestDatabase()
        application { module() }
        val (id, token) = client.signUp()
        client.deleteAccount(id, token)
        val (newId, _) = client.signUp("piet@example.com")

        assertEquals(HttpStatusCode.Unauthorized, client.deleteAccount(newId, token).status)
    }

    @Test
    fun LogoutReturnsNoContentAndRevokesToken() = testApplication {
        useTestDatabase()
        application { module() }
        val (id, token) = client.signUp()

        assertEquals(HttpStatusCode.NoContent, client.logout(token).status)

        assertEquals(HttpStatusCode.Unauthorized, client.logout(token).status)
        assertEquals(HttpStatusCode.Unauthorized, client.deleteAccount(id, token).status)
    }

    @Test
    fun LogoutKeepsOtherLoginsOfTheUser() = testApplication {
        useTestDatabase()
        application { module() }
        val (_, token) = client.signUp()
        val otherToken = client.login().json()["token"]!!.jsonPrimitive.content

        client.logout(token)

        assertEquals(HttpStatusCode.NoContent, client.logout(otherToken).status)
    }

    @Test
    fun LogoutWithoutValidTokenReturnsUnauthorized() = testApplication {
        useTestDatabase()
        application { module() }

        assertEquals(HttpStatusCode.Unauthorized, client.logout().status)
        assertEquals(HttpStatusCode.Unauthorized, client.logout("not-a-token").status)
    }

    @Test
    fun UpdateOwnAccountReturnsUpdatedAccountWithoutPassword() = testApplication {
        useTestDatabase()
        application { module() }
        val (id, token) = client.signUp()

        val response = client.updateAccount("$id", profile(), token)

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.json()
        assertEquals("Janneke", body["name"]!!.jsonPrimitive.content)
        assertEquals("janneke@example.com", body["email"]!!.jsonPrimitive.content)
        assertFalse(response.bodyAsText().contains("password"))
    }

    @Test
    fun UpdateTrimsNameAndEmail() = testApplication {
        useTestDatabase()
        application { module() }
        val (id, token) = client.signUp()

        val body = client.updateAccount("$id", profile(name = "  Janneke ", email = " janneke@example.com  "), token).json()

        assertEquals("Janneke", body["name"]!!.jsonPrimitive.content)
        assertEquals("janneke@example.com", body["email"]!!.jsonPrimitive.content)
        assertEquals(HttpStatusCode.OK, client.login("janneke@example.com").status)
    }

    @Test
    fun UpdatedEmailCanBeUsedToLogIn() = testApplication {
        useTestDatabase()
        application { module() }
        val (id, token) = client.signUp()
        client.updateAccount("$id", profile(), token)

        assertEquals(HttpStatusCode.OK, client.login("janneke@example.com").status)
        assertEquals(HttpStatusCode.Unauthorized, client.login("jan@example.com").status)
    }

    @Test
    fun UpdateKeepingOwnEmailReturnsOk() = testApplication {
        useTestDatabase()
        application { module() }
        val (id, token) = client.signUp()

        assertEquals(HttpStatusCode.OK, client.updateAccount("$id", profile(email = "jan@example.com"), token).status)
        assertEquals(HttpStatusCode.OK, client.updateAccount("$id", profile(email = "JAN@example.com"), token).status)
    }

    @Test
    fun UpdateWithInvalidFieldsReturnsBadRequest() = testApplication {
        useTestDatabase()
        application { module() }
        val (id, token) = client.signUp()

        listOf(profile(name = " "), profile(email = "not-an-email"), profile(email = "jan@"), "{}", "not json").forEach {
            assertEquals(HttpStatusCode.BadRequest, client.updateAccount("$id", it, token).status, it)
        }
        assertEquals(HttpStatusCode.BadRequest, client.updateAccount("abc", profile(), token).status)
    }

    @Test
    fun UpdateWithoutValidTokenReturnsUnauthorized() = testApplication {
        useTestDatabase()
        application { module() }
        val (id, _) = client.signUp()

        assertEquals(HttpStatusCode.Unauthorized, client.updateAccount("$id", profile()).status)
        assertEquals(HttpStatusCode.Unauthorized, client.updateAccount("$id", profile(), "not-a-token").status)
    }

    @Test
    fun UpdateSomeoneElsesAccountReturnsForbidden() = testApplication {
        useTestDatabase()
        application { module() }
        val (otherId, _) = client.signUp("piet@example.com")
        val (_, token) = client.signUp()

        assertEquals(HttpStatusCode.Forbidden, client.updateAccount("$otherId", profile(), token).status)
        assertEquals(HttpStatusCode.OK, client.login("piet@example.com").status)
    }

    @Test
    fun UpdateUnknownAccountReturnsNotFound() = testApplication {
        useTestDatabase()
        application { module() }
        val (_, token) = client.signUp()

        assertEquals(HttpStatusCode.NotFound, client.updateAccount("999", profile(), token).status)
    }

    @Test
    fun UpdateToEmailOfAnotherAccountReturnsConflict() = testApplication {
        useTestDatabase()
        application { module() }
        client.signUp("piet@example.com")
        val (id, token) = client.signUp()

        assertEquals(HttpStatusCode.Conflict, client.updateAccount("$id", profile(email = "PIET@example.com"), token).status)
        assertEquals(HttpStatusCode.OK, client.login("jan@example.com").status)
    }

    @Test
    fun ChangePasswordReturnsNoContentAndNewPasswordLogsIn() = testApplication {
        useTestDatabase()
        application { module() }
        val (id, token) = client.signUp()

        assertEquals(HttpStatusCode.NoContent, client.changePassword("$id", passwords(), token).status)

        assertEquals(HttpStatusCode.OK, client.login(password = "evenmoresecret").status)
        assertEquals(HttpStatusCode.Unauthorized, client.login(password = "supersecret").status)
    }

    @Test
    fun ChangePasswordRevokesOtherTokensButKeepsTheCurrentOne() = testApplication {
        useTestDatabase()
        application { module() }
        val (id, token) = client.signUp()
        val otherToken = client.login().json()["token"]!!.jsonPrimitive.content
        val (_, someoneElsesToken) = client.signUp("piet@example.com")

        client.changePassword("$id", passwords(), token)

        assertEquals(HttpStatusCode.Unauthorized, client.logout(otherToken).status)
        assertEquals(HttpStatusCode.NoContent, client.logout(someoneElsesToken).status)
        assertEquals(HttpStatusCode.NoContent, client.logout(token).status)
    }

    @Test
    fun ChangePasswordWithWrongCurrentPasswordReturnsForbidden() = testApplication {
        useTestDatabase()
        application { module() }
        val (id, token) = client.signUp()
        val otherToken = client.login().json()["token"]!!.jsonPrimitive.content

        assertEquals(HttpStatusCode.Forbidden, client.changePassword("$id", passwords(current = "wrongpassword"), token).status)

        assertEquals(HttpStatusCode.OK, client.login().status)
        assertEquals(HttpStatusCode.NoContent, client.logout(otherToken).status)
    }

    @Test
    fun ChangePasswordWithInvalidRequestReturnsBadRequest() = testApplication {
        useTestDatabase()
        application { module() }
        val (id, token) = client.signUp()

        listOf(passwords(new = "short"), passwords(new = ""), """{"currentPassword":"supersecret"}""", "{}", "not json").forEach {
            assertEquals(HttpStatusCode.BadRequest, client.changePassword("$id", it, token).status, it)
        }
        assertEquals(HttpStatusCode.BadRequest, client.changePassword("abc", passwords(), token).status)
        assertEquals(HttpStatusCode.OK, client.login().status)
    }

    @Test
    fun ChangePasswordWithoutValidTokenReturnsUnauthorized() = testApplication {
        useTestDatabase()
        application { module() }
        val (id, _) = client.signUp()

        assertEquals(HttpStatusCode.Unauthorized, client.changePassword("$id", passwords()).status)
        assertEquals(HttpStatusCode.Unauthorized, client.changePassword("$id", passwords(), "not-a-token").status)
    }

    @Test
    fun ChangeSomeoneElsesPasswordReturnsForbidden() = testApplication {
        useTestDatabase()
        application { module() }
        val (otherId, _) = client.signUp("piet@example.com")
        val (_, token) = client.signUp()

        assertEquals(HttpStatusCode.Forbidden, client.changePassword("$otherId", passwords(), token).status)
        assertEquals(HttpStatusCode.OK, client.login("piet@example.com").status)
    }

    @Test
    fun ChangePasswordOfUnknownAccountReturnsNotFound() = testApplication {
        useTestDatabase()
        application { module() }
        val (_, token) = client.signUp()

        assertEquals(HttpStatusCode.NotFound, client.changePassword("999", passwords(), token).status)
    }
}
