package com.example.trips

import io.ktor.http.Parameters
import kotlinx.serialization.Serializable

internal const val DEFAULT_LEADERBOARD_LIMIT = 10
internal const val MAX_LEADERBOARD_LIMIT = 100

// Only the name and the points: the id and the email of a driver stay private.
@Serializable
data class LeaderboardEntry(val name: String, val bonusPoints: Int)

// null when the limit is not a whole number from 1 to MAX_LEADERBOARD_LIMIT.
fun parseLeaderboardLimit(query: Parameters): Int? {
    val limit = query["limit"]?.let { it.toIntOrNull() ?: return null } ?: DEFAULT_LEADERBOARD_LIMIT
    return limit.takeIf { it in 1..MAX_LEADERBOARD_LIMIT }
}
