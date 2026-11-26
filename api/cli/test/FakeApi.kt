package com.example.cli

import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.StringReader

/** Answers from a script ("METHOD /path" to response) and remembers every call. A path without a script answers 404. */
class FakeApi(vararg answers: Pair<String, ApiResponse>) : Api {
    override var token: String? = null
    val answers = answers.toMap().toMutableMap()
    val calls = mutableListOf<String>()
    val queries = mutableListOf<Map<String, String?>>()
    val bodies = mutableListOf<JsonElement?>()
    /** Answers that depend on the query (pages); returning null falls back to [answers]. */
    var handler: ((String, Map<String, String?>) -> ApiResponse?)? = null

    /** The files that were uploaded: the path and the file name, content type and size. */
    val uploads = mutableListOf<Pair<String, Triple<String, String, Int>>>()

    override fun upload(path: String, fileName: String, contentType: String, bytes: ByteArray): ApiResponse {
        calls += "POST $path"
        uploads += path to Triple(fileName, contentType, bytes.size)
        return answers["POST $path"] ?: ApiResponse(404, null)
    }

    override fun call(method: String, path: String, query: Map<String, String?>, body: JsonElement?): ApiResponse {
        calls += "$method $path"
        queries += query
        bodies += body
        return handler?.invoke("$method $path", query) ?: answers["$method $path"] ?: ApiResponse(404, null)
    }
}

fun json(text: String) = ApiResponse(200, Json.parseToJsonElement(text))
fun status(code: Int) = ApiResponse(code, null)

/** Runs the whole client on a scripted input and returns what it printed. */
fun runScript(api: Api, vararg lines: String, session: Session = Session()): String {
    val out = ByteArrayOutputStream()
    val console = Console(BufferedReader(StringReader(lines.joinToString("\n") + "\n")), PrintStream(out, true))
    runClient(Context(api, console, session))
    return out.toString()
}
