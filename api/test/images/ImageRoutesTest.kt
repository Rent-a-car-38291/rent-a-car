package com.example.images

import com.example.accounts.UserRepository
import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.jetbrains.amper.ktor.module
import java.nio.file.Files
import java.util.UUID
import kotlin.test.*

class ImageRoutesTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 9, 9)

    @BeforeTest
    fun reset() = UserRepository(TestDatabase.dataSource).clear()

    private class Setup(val client: HttpClient, val owner: String, val other: String, val carId: Int)

    private fun withCar(block: suspend Setup.() -> Unit) = testApplication {
        useTestDatabase()
        application { module() }
        suspend fun signUp(email: String): String {
            client.post("/accounts") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Jan","email":"$email","password":"supersecret"}""")
            }
            val login = client.post("/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"$email","password":"supersecret"}""")
            }
            return Json.parseToJsonElement(login.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
        }
        val owner = signUp("owner@example.com")
        val other = signUp("other@example.com")
        val car = client.post("/cars") {
            bearerAuth(owner)
            contentType(ContentType.Application.Json)
            setBody("""{"type":"BEV","brand":"Test","licensePlate":"IMG-${UUID.randomUUID()}","mileage":1,"rate":50.0,
                "location":{"latitude":1.0,"longitude":2.0},"doors":4,"bodyStyle":"Sedan","modelYear":2024,"power":100,
                "transmission":"Automatic","color":"Blue","capacity":50.0,"consumption":15.0}""")
        }
        assertEquals(HttpStatusCode.Created, car.status)
        Setup(client, owner, other, Json.parseToJsonElement(car.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.int).block()
    }

    private suspend fun Setup.upload(
        bytes: ByteArray?, token: String? = owner, carId: Any = this.carId, declaredType: String = "image/jpeg", fileName: String = "photo.jpg",
    ): HttpResponse = client.post("/cars/$carId/images") {
        token?.let { bearerAuth(it) }
        setBody(MultiPartFormDataContent(formData {
            if (bytes == null) append("note", "no file here")
            else append("file", bytes, Headers.build {
                append(HttpHeaders.ContentType, declaredType)
                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
            })
        }))
    }

    private fun HttpResponse.url() = runBlocking { Json.parseToJsonElement(bodyAsText()).jsonObject["url"]!!.jsonPrimitive.content }

    @Test
    fun OwnerUploadsAJpegAndItIsServedAndListedOnTheCar() = withCar {
        val response = upload(jpeg)

        assertEquals(HttpStatusCode.Created, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(carId, json["carId"]!!.jsonPrimitive.int)
        val url = json["url"]!!.jsonPrimitive.content
        val served = client.get(url)
        assertEquals(HttpStatusCode.OK, served.status)
        assertEquals(ContentType.Image.JPEG, served.contentType()?.withoutParameters())
        assertContentEquals(jpeg, served.bodyAsBytes())
        val car = client.get("/cars/$carId") { bearerAuth(owner) }
        assertEquals(listOf(url), Json.parseToJsonElement(car.bodyAsText()).jsonObject["images"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun APngIsAccepted() = withCar {
        val response = upload(png, declaredType = "image/png", fileName = "photo.png")

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(ContentType.Image.PNG, client.get(response.url()).contentType()?.withoutParameters())
    }

    @Test
    fun AMissingFileIsABadRequest() = withCar {
        assertEquals(HttpStatusCode.BadRequest, upload(null).status)
    }

    @Test
    fun AnotherFileTypeIsABadRequestWhateverTheClientClaims() = withCar {
        assertEquals(HttpStatusCode.BadRequest, upload("<html>not an image</html>".toByteArray()).status)
        assertEquals(HttpStatusCode.BadRequest, upload("GIF89a".toByteArray(), declaredType = "image/gif", fileName = "a.gif").status)
        assertEquals(HttpStatusCode.BadRequest, upload(ByteArray(0)).status)
    }

    @Test
    fun AFileAboveFiveMegabytesIsRejectedAndOneOfExactlyFiveIsNot() = withCar {
        assertEquals(HttpStatusCode.PayloadTooLarge, upload(jpeg + ByteArray(MAX_IMAGE_BYTES)).status)
        assertEquals(HttpStatusCode.Created, upload(jpeg + ByteArray(MAX_IMAGE_BYTES - jpeg.size)).status)
    }

    @Test
    fun UploadNeedsLoginAnOwnCarAndAnExistingCar() = withCar {
        assertEquals(HttpStatusCode.Unauthorized, upload(jpeg, token = null).status)
        assertEquals(HttpStatusCode.Forbidden, upload(jpeg, token = other).status)
        assertEquals(HttpStatusCode.NotFound, upload(jpeg, carId = 999_999).status)
        assertEquals(HttpStatusCode.BadRequest, upload(jpeg, carId = "abc").status)
    }

    @Test
    fun TheClientFileNameNeverDecidesWhereTheFileGoes() = withCar {
        val url = upload(jpeg, fileName = "../../evil.jpg").url()

        assertTrue(Regex("/images/[0-9a-f-]{36}\\.jpg").matches(url), url)
        assertFalse(Files.exists(TestDatabase.imagesDirectory.parent.resolve("evil.jpg")))
    }

    @Test
    fun ANameThatIsNotAnUploadFindsNothing() = withCar {
        assertEquals(HttpStatusCode.NotFound, client.get("/images/..%2F..%2Fetc%2Fpasswd").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/images/${UUID.randomUUID()}.jpg").status)
    }

    @Test
    fun DeletingTheCarRemovesItsPhotos() = withCar {
        val url = upload(jpeg).url()
        val name = url.substringAfterLast('/')
        assertTrue(Files.exists(TestDatabase.imagesDirectory.resolve(name)))

        assertEquals(HttpStatusCode.NoContent, client.delete("/cars/$carId") { bearerAuth(owner) }.status)

        assertFalse(Files.exists(TestDatabase.imagesDirectory.resolve(name)))
        assertEquals(HttpStatusCode.NotFound, client.get(url).status)
    }

    @Test
    fun ABodyThatIsNotMultipartIsABadRequest() = withCar {
        val response = client.post("/cars/$carId/images") {
            bearerAuth(owner)
            contentType(ContentType.Application.Json)
            setBody("{}")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun ACarHoldsAtMostTenPhotos() = withCar {
        repeat(MAX_IMAGES_PER_CAR) { assertEquals(HttpStatusCode.Created, upload(jpeg).status) }

        assertEquals(HttpStatusCode.Conflict, upload(jpeg).status)
    }
}
