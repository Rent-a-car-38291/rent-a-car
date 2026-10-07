package com.example.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// ponytail: in-memory store, swap for the database once #10 lands
object UserRepository {
    private val users = mutableListOf<User>()

    fun register(name: String, email: String, password: String): User? {
        val passwordHash = hash(password) // slow, so keep it outside the lock
        synchronized(this) {
            if (findByEmail(email) != null) return null
            return User(users.size + 1, name, email, passwordHash).also { users.add(it) }
        }
    }

    fun authenticate(email: String, password: String): User? =
        synchronized(this) { findByEmail(email) }?.takeIf { verify(password, it.passwordHash) }

    @Synchronized
    fun clear() = users.clear()

    private fun findByEmail(email: String) = users.find { it.email.equals(email, ignoreCase = true) }

    private fun hash(password: String, salt: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }): String {
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, 120_000, 256)).encoded
        val b64 = Base64.getEncoder()
        return "${b64.encodeToString(salt)}:${b64.encodeToString(key)}"
    }

    // Hashes the password again with the stored salt and compares in constant time.
    private fun verify(password: String, passwordHash: String): Boolean {
        val salt = Base64.getDecoder().decode(passwordHash.substringBefore(':'))
        return MessageDigest.isEqual(hash(password, salt).toByteArray(), passwordHash.toByteArray())
    }
}
