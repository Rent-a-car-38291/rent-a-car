import com.example.domain.UserRepository
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.jetbrains.amper.ktor.module
import kotlin.test.*

class AccountRoutesTest {
    @BeforeTest
    fun reset() = UserRepository.clear()

    private fun valid(email: String = "jan@example.com") =
        """{"name":"Jan","email":"$email","password":"supersecret"}"""

    private suspend fun io.ktor.client.HttpClient.register(body: String) =
        post("/accounts") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    @Test
    fun RegisterReturnsCreatedWithoutPassword() = testApplication {
        application { module() }

        val response = client.register(valid())

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("jan@example.com"))
        assertFalse(body.contains("assword"))
    }

    @Test
    fun RegisterWithInvalidFieldsReturnsBadRequest() = testApplication {
        application { module() }

        listOf(
            """{"name":"","email":"jan@example.com","password":"supersecret"}""",
            """{"name":"Jan","email":"not-an-email","password":"supersecret"}""",
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
    fun RegisterWithoutJsonContentTypeReturnsUnsupportedMediaType() = testApplication {
        application { module() }

        val response = client.post("/accounts") {
            contentType(ContentType.Text.Plain)
            setBody(valid())
        }

        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
    }
}
