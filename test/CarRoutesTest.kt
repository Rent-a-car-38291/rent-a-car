import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.jetbrains.amper.ktor.module
import kotlin.test.*

class CarRoutesTest {
    @Test
    fun GetCarsReturnsAllThreeCarTypes() = testApplication {
        // Arrange
        application {
            module()
        }

        // Act
        val response = client.get("/cars").bodyAsText()

        // Assert
        assertTrue(response.contains("\"type\":\"com.example.domain.ICECar\""))
        assertTrue(response.contains("\"type\":\"com.example.domain.BEVCar\""))
        assertTrue(response.contains("\"type\":\"com.example.domain.FCEVCar\""))
    }

    @Test
    fun GetCarsReturnsOkWithJsonList() = testApplication {
        // Arrange
        application {
            module()
        }

        // Act
        val response = client.get("/cars")

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(true, response.contentType()?.match(ContentType.Application.Json))
    }
}