import com.example.domain.UserRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.amper.ktor.module
import kotlin.test.*

class AccountRoutesTest {
    @BeforeTest
    fun reset() = UserRepository.clear()

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

    @Test
    fun RegisterReturnsCreatedWithoutPassword() = testApplication {
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
        application { module() }
        client.register(valid())

        val response = client.register(valid("JAN@example.com"))

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun LoginReturnsToken() = testApplication {
        application { module() }
        client.register(valid())

        val response = client.login(email = "JAN@example.com")

        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(response.json()["token"]?.jsonPrimitive?.content.isNullOrBlank())
    }

    @Test
    fun LoginWithWrongCredentialsReturnsUnauthorized() = testApplication {
        application { module() }
        client.register(valid())

        assertEquals(HttpStatusCode.Unauthorized, client.login(password = "wrongpassword").status)
        assertEquals(HttpStatusCode.Unauthorized, client.login(email = "piet@example.com").status)
    }
}
