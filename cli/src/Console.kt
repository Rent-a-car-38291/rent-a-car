package com.example.cli

import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.PrintStream
import java.time.LocalDate

/** Typed 'back' (or 'b') at any prompt: abandon the current action. */
class BackException : Exception()

/** The input ended (Ctrl-D): leave the program. */
class QuitException : Exception()

/** Prompts and output on injected streams, so menus can be driven by a script in tests. */
class Console(private val input: BufferedReader, val out: PrintStream) {
    fun say(text: String = "") = out.println(text)

    /** Reads one line; 'back' abandons the action, the end of the input quits. */
    fun readLine(prompt: String): String {
        out.print("$prompt ")
        out.flush()
        val line = input.readLine() ?: throw QuitException()
        if (line.trim().lowercase() in setOf("b", "back")) throw BackException()
        return line.trim()
    }

    /** Asks until the answer passes [validate] (which returns an error text or null). A blank answer takes [default]. */
    fun <T> ask(label: String, default: String? = null, validate: (String) -> Pair<T?, String?>): T {
        while (true) {
            val answer = readLine(label + (default?.let { " [$it]" } ?: "") + ":").ifEmpty { default ?: "" }
            val (value, error) = validate(answer)
            if (value != null) return value
            say("  $error")
        }
    }

    fun text(label: String, default: String? = null): String =
        ask(label, default) { if (it.isEmpty()) null to "Please enter a value." else it to null }

    /** A blank answer gives null, for filters and optional fields. */
    fun optionalText(label: String): String? = readLine("$label (Enter to skip):").ifEmpty { null }

    /** A blank answer gives null; otherwise one of [options], typed in any case, returned as spelled in the list. */
    fun optionalOneOf(label: String, options: List<String>): String? =
        optional("$label (${options.joinToString(", ")})") { raw -> options.find { it.equals(raw, ignoreCase = true) }?.let { it to null } ?: (null to "Please choose one of: ${options.joinToString(", ")}.") }

    fun int(label: String, default: Int? = null, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): Int =
        ask(label, default?.toString()) { parseInt(it, min, max) }

    private fun <T> optional(label: String, parse: (String) -> Pair<T?, String?>): T? {
        while (true) {
            val raw = readLine("$label (Enter to skip):")
            if (raw.isEmpty()) return null
            val (value, error) = parse(raw)
            if (value != null) return value
            say("  $error")
        }
    }

    fun optionalInt(label: String, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): Int? = optional(label) { parseInt(it, min, max) }

    fun double(label: String, default: Double? = null, min: Double = -Double.MAX_VALUE): Double =
        ask(label, default?.toString()) { parseDouble(it, min) }

    fun optionalDouble(label: String): Double? = optional(label) { parseDouble(it, -Double.MAX_VALUE) }

    fun date(label: String, default: LocalDate? = null): LocalDate =
        ask(label + " (YYYY-MM-DD)", default?.toString()) { parseDate(it) }

    fun optionalDate(label: String): LocalDate? = optional("$label (YYYY-MM-DD)") { parseDate(it) }

    fun confirm(question: String): Boolean = readLine("$question (y/N):").lowercase() in setOf("y", "yes")

    /** A numbered list; returns the chosen option. */
    fun <T> choose(title: String, options: List<T>, label: (T) -> String = { it.toString() }, default: Int? = null): T {
        say(title)
        options.forEachIndexed { i, option -> say("  ${i + 1}) ${label(option)}") }
        val index = int("Choose", default, 1, options.size)
        return options[index - 1]
    }

    /** Prints a JSON array of objects as a table; [columns] are keys, with `a.b` reaching into nested objects. */
    fun table(rows: JsonArray, columns: List<String>? = null) {
        if (rows.isEmpty()) return say("  (nothing to show)")
        val objects = rows.map { it as? JsonObject ?: buildJsonObject { put("value", it) } }
        val keys = columns ?: flatten(objects.first()).keys.toList()
        val cells = objects.map { row -> keys.map { display(flatten(row)[it]) } }
        val widths = keys.indices.map { c -> maxOf(keys[c].length, cells.maxOf { it[c].length }) }
        fun line(values: List<String>) = say("  " + values.indices.joinToString("  ") { values[it].padEnd(widths[it]) }.trimEnd())
        line(keys)
        line(widths.map { "-".repeat(it) })
        cells.forEach(::line)
    }

    /** Prints one JSON object as `key: value` lines; nested objects get dotted keys. */
    fun show(obj: JsonObject, keys: List<String>? = null) {
        val flat = flatten(obj)
        (keys ?: flat.keys.toList()).forEach { say("  ${it.padEnd(flat.keys.maxOf(String::length).coerceAtLeast(it.length))}  ${display(flat[it])}") }
    }

    /** Prints any JSON answer the way it fits: a table for a list, lines for an object, the plain value otherwise. */
    fun showAny(json: JsonElement?, columns: List<String>? = null) = when (json) {
        is JsonArray -> table(json, columns)
        is JsonObject -> show(json, columns)
        null -> Unit
        else -> say("  $json")
    }
}

private val SERVER_ZONE = java.time.ZoneId.of("Europe/Amsterdam")

/** The server decides what "today" is in this zone, so the client must too. */
fun today(): LocalDate = LocalDate.now(SERVER_ZONE)

/** A moment from the server (UTC, like 2026-10-25T15:21:04.8Z) as the local time in the server's zone, like 16:21; unreadable text stays as it is. */
fun localTime(instant: String): String = runCatching {
    java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(java.time.Instant.parse(instant).atZone(SERVER_ZONE))
}.getOrDefault(instant)

fun parseInt(raw: String, min: Int, max: Int): Pair<Int?, String?> {
    val n = raw.toIntOrNull() ?: return null to "Please enter a whole number."
    return if (n in min..max) n to null else null to "Please enter a number between ${if (min == Int.MIN_VALUE) "-∞" else min} and ${if (max == Int.MAX_VALUE) "∞" else max}."
}

fun parseDouble(raw: String, min: Double): Pair<Double?, String?> {
    val n = raw.replace(',', '.').toDoubleOrNull() ?: return null to "Please enter a number."
    return if (n >= min) n to null else null to "Please enter a number of at least $min."
}

fun parseDate(raw: String): Pair<LocalDate?, String?> =
    runCatching { LocalDate.parse(raw) }.getOrNull()?.let { it to null } ?: (null to "Please use the format YYYY-MM-DD, for example 2026-10-12.")

internal fun flatten(obj: JsonObject, prefix: String = ""): Map<String, JsonElement> =
    obj.entries.flatMap { (k, v) ->
        if (v is JsonObject) flatten(v, "$prefix$k.").entries.map { it.key to it.value } else listOf("$prefix$k" to v)
    }.toMap()

internal fun display(value: JsonElement?): String = when {
    value == null || value is JsonNull -> "-"
    value is JsonPrimitive -> value.content
    value is JsonArray -> value.joinToString(", ") { display(it) }.ifEmpty { "-" }
    else -> value.toString()
}
