import com.example.database.useTestDatabase
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.yaml.YamlConfig
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
        assertTrue(client.get("/swagger/documentation.yaml").bodyAsText().contains("/accounts/{id}:"))
        assertTrue(client.get("/swagger/documentation.yaml").bodyAsText().contains("/cars/{id}:"))
        assertTrue(client.get("/swagger/documentation.yaml").bodyAsText().contains("/invites/{code}/accept:"))
        assertTrue(client.get("/swagger/documentation.yaml").bodyAsText().contains("/organizations:"))
    }

    // Swagger UI shows nothing when the file is not valid YAML, and the text checks above would not notice.
    @Test
    fun TheOpenApiFileIsValidYaml() {
        val config = YamlConfig("openapi/documentation.yaml")!!

        assertEquals("3.0.3", config.property("openapi").getString())
    }

    @Test
    fun SwaggerUiIsHiddenOutsideDevelopmentMode() = testApplication {
        useTestDatabase()
        serverConfig { developmentMode = false }
        application { module() }

        assertEquals(HttpStatusCode.NotFound, client.get("/swagger").status)
    }
}
