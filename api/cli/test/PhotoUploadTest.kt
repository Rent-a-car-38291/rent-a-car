package com.example.cli

import com.example.cli.areas.readPhoto
import com.example.cli.areas.typedPath
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.test.*

class PhotoUploadTest {
    private val login = "POST /login" to json("""{"token":"abc"}""")
    private val cars = "GET /cars" to json("""[{"id":7,"type":"BEV","brand":"Tesla","licensePlate":"AB-1","rate":50.0,"status":"Available"}]""")
    private val tinyPng = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==")

    private fun file(name: String, bytes: ByteArray): Path = Files.createTempDirectory("photo-test").resolve(name).also { Files.write(it, bytes) }

    // login (2), Cars (1) > Add a photo (8) > car 1 in the list > the path, then back, log out, quit
    private fun addPhoto(api: FakeApi, path: Path) = runScript(api, "2", "a@b.c", "secret123", "1", "8", "1", path.toString(), "0", "0", "0")

    @Test
    fun `a photo is uploaded as a multipart file and its url is shown`() {
        val api = FakeApi(login, cars, "POST /cars/7/images" to ApiResponse(201, kotlinx.serialization.json.Json.parseToJsonElement("""{"id":1,"carId":7,"url":"/images/abc.png"}""")))

        val output = addPhoto(api, file("car.png", tinyPng))

        assertEquals(listOf("/cars/7/images" to Triple("car.png", "image/png", tinyPng.size)), api.uploads)
        assertTrue("Photo added: /images/abc.png" in output, output)
    }

    @Test
    fun `a file that is not there or not a picture is refused before anything is sent`() {
        val api = FakeApi(login, cars)

        val missing = addPhoto(api, Path.of("/no/such/photo.png"))
        val text = addPhoto(api, file("notes.png", "hello".toByteArray()))

        assertTrue("There is no file at" in missing, missing)
        assertTrue("That is not a JPEG or PNG picture." in text, text)
        assertTrue(api.uploads.isEmpty())
    }

    @Test
    fun `every status code of the upload has a friendly message`() {
        val messages = mapOf(
            400 to "Use a JPEG or PNG picture.", 401 to "Your login has expired", 403 to "You do not manage that car.",
            404 to "Not found.", 409 to "already has 10 photos", 413 to "5 MB at most",
        )
        for ((code, expected) in messages) {
            val output = addPhoto(FakeApi(login, cars, "POST /cars/7/images" to status(code)), file("car.png", tinyPng))

            assertTrue(expected in output, "$code: $output")
        }
    }

    @Test
    fun `the file check knows both formats and the size limit`() {
        assertEquals("image/png", readPhoto(file("a.png", tinyPng)).contentType)
        assertEquals("image/jpeg", readPhoto(file("a.jpg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2))).contentType)
        assertNotNull(readPhoto(file("a.txt", "plain".toByteArray())).problem)
        assertNotNull(readPhoto(file("empty.png", ByteArray(0))).problem)
        val atLimit = tinyPng + ByteArray(5 * 1024 * 1024 - tinyPng.size)
        assertNull(readPhoto(file("limit.png", atLimit)).problem)
        assertEquals("The file is larger than 5 MB.", readPhoto(file("big.png", atLimit + 1)).problem)
    }

    @Test
    fun `a file name cannot break the multipart header`() {
        assertEquals("my _car_.png", safeFileName("my \"car\".png"))
        assertEquals("a_b_c_d.png", safeFileName("a\\b\rc\nd.png"))
        assertEquals("kar-\u00e9.png", safeFileName("kar-\u00e9.png"))
    }

    @Test
    fun `a typed path loses its quotes and escapes and expands the home folder`() {
        assertEquals(Path.of("/a b/c.png"), typedPath("'/a b/c.png'"))
        assertEquals(Path.of("/a b/c.png"), typedPath("/a\\ b/c.png"))
        assertEquals(Path.of(System.getProperty("user.home"), "pics", "c.png"), typedPath("~/pics/c.png"))
    }

    @Test
    fun `a folder is refused with its own message`() {
        assertTrue("is a folder" in readPhoto(Files.createTempDirectory("a-folder")).problem!!)
    }
}
