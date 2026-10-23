package com.example

import com.example.database.useTestDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.*
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import org.jetbrains.amper.ktor.module
import kotlin.test.*

class ErrorsAndPagingRoutesTest {
    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        useTestDatabase()
        application {
            module()
            routing { get("/boom") { error("secret detail: password=hunter2") } }
        }
        block()
    }

    private suspend fun HttpResponse.error(status: HttpStatusCode, code: String): JsonObject {
        assertEquals(status, this.status)
        assertEquals(true, contentType()?.match(ContentType.Application.Json), "content type")
        val body = Json.parseToJsonElement(bodyAsText()).jsonObject
        assertEquals(setOf("error", "message"), body.keys)
        assertEquals(code, body["error"]!!.jsonPrimitive.content)
        assertTrue(body["message"]!!.jsonPrimitive.content.isNotBlank())
        return body
    }

    private suspend fun HttpClient.postJson(path: String, body: String) = post(path) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpClient.carIds(query: String): Pair<List<Int>, Int> {
        val response = get("/cars$query")
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.parseToJsonElement(response.bodyAsText()).jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.int } to response.headers["X-Total-Count"]!!.toInt()
    }

    @Test
    fun TheCarsCanBePagedThroughAndTheTotalIsInTheHeader() = withApp {
        val (all, total) = client.carIds("?limit=100")
        assertEquals(all.size, total)

        val paged = (0 until total step 5).flatMap { offset -> client.carIds("?limit=5&offset=$offset").first }

        assertEquals(all, paged) // every car once, in the same order
        assertEquals(emptyList(), client.carIds("?offset=$total").first)
        assertEquals(total, client.carIds("?offset=$total").second)
    }

    @Test
    fun TheDefaultPageHasAtMostTwentyCarsAndAFilterChangesTheTotal() = withApp {
        val (ids, total) = client.carIds("")
        assertEquals(minOf(DEFAULT_LIMIT, total), ids.size)

        val (bev, bevTotal) = client.carIds("?type=BEV&limit=1")
        assertEquals(1, bev.size)
        assertTrue(bevTotal in 1 until total, "the total counts only the matching cars")
    }

    @Test
    fun InvalidPagingGives400WithTheErrorBody() = withApp {
        for (query in listOf("limit=0", "limit=${MAX_LIMIT + 1}", "limit=x", "limit=1.5", "offset=-1", "offset=x")) {
            client.get("/cars?$query").error(HttpStatusCode.BadRequest, "bad_request")
        }
    }

    @Test
    fun EveryErrorStatusHasTheSameBody() = withApp {
        // 404: an unknown route, and an unknown car with its own message
        client.get("/nowhere").error(HttpStatusCode.NotFound, "not_found")
        client.get("/cars/999999/cost-per-km").error(HttpStatusCode.NotFound, "not_found")
        // 401: no token, with the Bearer challenge
        val unauthorized = client.get("/trips")
        unauthorized.error(HttpStatusCode.Unauthorized, "unauthorized")
        assertEquals(listOf("Bearer"), unauthorized.headers.getAll(HttpHeaders.WWWAuthenticate)) // once, not twice
        // a wrong password is not a Bearer challenge
        val wrongPassword = client.postJson("/login", """{"email":"nobody@example.com","password":"wrong"}""")
        assertEquals("Wrong email or password.", wrongPassword.error(HttpStatusCode.Unauthorized, "unauthorized")["message"]!!.jsonPrimitive.content)
        assertNull(wrongPassword.headers[HttpHeaders.WWWAuthenticate])
        // 400: an unreadable body, and a missing field
        client.postJson("/login", "{not json").error(HttpStatusCode.BadRequest, "bad_request")
        client.postJson("/login", """{"email":"a@example.com"}""").error(HttpStatusCode.BadRequest, "bad_request")
        // 415: a body in another format
        client.post("/login") {
            contentType(ContentType.Text.Plain)
            setBody("email=a")
        }.error(HttpStatusCode.UnsupportedMediaType, "unsupported_media_type")
        // 405: a known path with another method
        client.post("/leaderboard").error(HttpStatusCode.MethodNotAllowed, "method_not_allowed")
    }

    @Test
    fun AConflictAndTooManyLoginsUseTheSameBodyToo() = withApp {
        val account = """{"name":"Jan","email":"errors@example.com","password":"supersecret"}"""
        assertEquals(HttpStatusCode.Created, client.postJson("/accounts", account).status)
        client.postJson("/accounts", account).error(HttpStatusCode.Conflict, "conflict")

        repeat(5) { assertEquals(HttpStatusCode.Unauthorized, client.postJson("/login", """{"email":"limit@example.com","password":"wrong"}""").status) }
        val limited = client.postJson("/login", """{"email":"limit@example.com","password":"wrong"}""")
        limited.error(HttpStatusCode.TooManyRequests, "too_many_requests")
        assertNotNull(limited.headers[HttpHeaders.RetryAfter])
    }

    @Test
    fun AnUnexpectedFailureGives500WithoutTheDetails() = withApp {
        val response = client.get("/boom")

        response.error(HttpStatusCode.InternalServerError, "internal_server_error")
        assertFalse(response.bodyAsText().contains("hunter2"))
    }
}
