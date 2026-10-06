package com.example.domain

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// ponytail: in-memory store, swap for the database once #10 lands
object UserRepository {
    private val users = mutableListOf<User>()

    @Synchronized
    fun register(name: String, email: String, password: String): User? {
        if (users.any { it.email.equals(email, ignoreCase = true) }) return null
        return User(users.size + 1, name, email, hash(password)).also { users.add(it) }
    }

    @Synchronized
    fun clear() = users.clear()

    private fun hash(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, 120_000, 256)).encoded
        val b64 = Base64.getEncoder()
        return "${b64.encodeToString(salt)}:${b64.encodeToString(key)}"
    }
}
