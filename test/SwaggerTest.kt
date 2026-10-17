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
        assertTrue(client.get("/swagger/documentation.yaml").bodyAsText().contains("/organizations:"))
    }

    @Test
    fun SwaggerUiIsHiddenOutsideDevelopmentMode() = testApplication {
        useTestDatabase()
        serverConfig { developmentMode = false }
        application { module() }

        assertEquals(HttpStatusCode.NotFound, client.get("/swagger").status)
    }
}
