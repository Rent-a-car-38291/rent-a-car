package com.example

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

// Any port on this machine: the web portal's dev server runs next to the API on another port.
private val LOCAL_ORIGIN = Regex("""http://(localhost|127\.0\.0\.1)(:\d+)?""")

/**
 * Lets the web portal call the API from the browser. The browser refuses a call from another origin
 * unless the response allows it. Allowed: the origins in [origins] (CORS_ORIGINS), and in development
 * mode any origin on this machine.
 */
fun Application.configureCors(origins: List<String>) {
    val local = developmentMode
    install(CORS) {
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        // Without these the browser hides them from the portal: paging and the wait after too many logins.
        exposeHeader("X-Total-Count")
        exposeHeader(HttpHeaders.RetryAfter)
        allowOrigins { it in origins || (local && LOCAL_ORIGIN.matches(it)) }
    }
}

/** CORS_ORIGINS is a comma-separated list such as `https://portal.example.com,https://test.example.com`. */
fun parseOrigins(value: String): List<String> = value.split(',').map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }
