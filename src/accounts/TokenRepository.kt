package com.example.accounts

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// ponytail: in-memory tokens that never expire and are lost on restart, fine until #10 lands
object TokenRepository {
    private val userIds = ConcurrentHashMap<String, Int>()

    // A random UUID comes from SecureRandom, so it can't be guessed.
    fun issue(userId: Int): String = UUID.randomUUID().toString().also { userIds[it] = userId }

    fun userId(token: String): Int? = userIds[token]

    fun revokeAll(userId: Int) {
        userIds.values.removeIf { it == userId }
    }
}
