import com.example.database.useTestDatabase
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.jetbrains.amper.ktor.module
import kotlin.test.*

class SwaggerTest {
    @Test
    fun SwaggerUiIsServedInDevelopmentMode() = testApplication {
        useTestDatabase()
        application { module() }

        assertEquals(HttpStatusCode.OK, client.get("/swagger").status)
        assertTrue(client.get("/swagger/documentation.yaml").bodyAsText().contains("/cars:"))
        assertTrue(client.get("/swagger/documentation.yaml").bodyAsText().contains("/organizations/{id}/members/{userId}:"))
        assertTrue(client.get("/swagger/documentation.yaml").bodyAsText().contains("/invites/{code}/accept:"))
        assertTrue(client.get("/swagger/documentation.yaml").bodyAsText().contains("/organizations:"))
    }

    @Test
    fun EveryEndpointIsDescribed() = testApplication {
        useTestDatabase()
        application { module() }

        val documentation = client.get("/swagger/documentation.yaml").bodyAsText()
        listOf("/cars/{id}:", "/organizations:", "/organizations/{id}/invites:", "/invites/{code}/accept:", "/organizations/{id}/members/{userId}:")
            .forEach { assertTrue(documentation.contains("  $it"), it) }
    }

    @Test
    fun SwaggerUiIsHiddenOutsideDevelopmentMode() = testApplication {
        useTestDatabase()
        serverConfig { developmentMode = false }
        application { module() }

        assertEquals(HttpStatusCode.NotFound, client.get("/swagger").status)
    }
}
