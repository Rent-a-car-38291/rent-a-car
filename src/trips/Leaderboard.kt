package com.example.trips

import kotlinx.serialization.Serializable

// The leaderboard is short by default: the top 10, paged with limit and offset like the other lists.
internal const val DEFAULT_LEADERBOARD_LIMIT = 10

// Only the name and the points: the id and the email of a driver stay private.
@Serializable
data class LeaderboardEntry(val name: String, val bonusPoints: Int)
