package com.example.cli.areas

import com.example.cli.*
import kotlinx.serialization.json.*

val insightsMenu = Menu("Insights", needsLogin = false, items = listOf(
    MenuItem("Leaderboard: most bonus points", needsLogin = false) { ctx ->
        val limit = ctx.io.int("How many drivers (1-100)", 10, 1, 100)
        ctx.call("GET", "/leaderboard", mapOf("limit" to limit.toString()))?.let { ctx.io.table(it.jsonArray, listOf("name", "bonusPoints")) }
    },
    MenuItem("My driving stats") { ctx ->
        ctx.call("GET", "/accounts/${ctx.accountId()}/stats", hints = mapOf(403 to "You can only see your own stats. Is that your account id?"))
            ?.let { ctx.io.show(it.jsonObject) }
    },
))
