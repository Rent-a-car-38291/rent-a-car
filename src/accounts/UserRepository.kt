package com.example.accounts

import com.example.database.runScript
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.SQLException
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.sql.DataSource

private const val UNIQUE_VIOLATION = "23505"

class UserRepository(private val dataSource: DataSource) {
    private val dummyHash by lazy { hash("dummy") }

    init {
        dataSource.runScript("users.sql")
    }

    // null when the email is taken: the unique index on lower(email) decides, so two requests at once can't both win.
    fun register(name: String, email: String, password: String): User? {
        val passwordHash = hash(password) // slow, so keep it away from the database connection
        return queryUser("INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?) ON CONFLICT DO NOTHING RETURNING *", name, email, passwordHash)
    }

    // An unknown email is verified against a dummy hash, so it costs as much time as a known one
    // and the response time doesn't reveal which emails have an account.
    fun authenticate(email: String, password: String): User? {
        val user = queryUser("SELECT * FROM users WHERE lower(email) = lower(?)", email)
        val passwordMatches = verify(password, user?.passwordHash ?: dummyHash)
        return user?.takeIf { passwordMatches }
    }

    fun find(id: Int): User? = queryUser("SELECT * FROM users WHERE id = ?", id)

    // null when the email belongs to another account (the unique index decides, so two requests at once can't both win),
    // or when the user was deleted meanwhile: shortcut, the route reports both as 409.
    fun update(id: Int, name: String, email: String): User? = try {
        queryUser("UPDATE users SET name = ?, email = ? WHERE id = ? RETURNING *", name, email, id)
    } catch (e: SQLException) {
        if (e.sqlState == UNIQUE_VIOLATION) null else throw e
    }

    fun delete(id: Int): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement("DELETE FROM users WHERE id = ?").use { it.setInt(1, id); it.executeUpdate() > 0 }
    }

    fun clear() {
        dataSource.connection.use { it.createStatement().use { statement -> statement.execute("TRUNCATE users RESTART IDENTITY") } }
    }

    private fun queryUser(sql: String, vararg parameters: Any): User? = dataSource.connection.use { connection ->
        connection.prepareStatement(sql).use { statement ->
            parameters.forEachIndexed { index, parameter -> statement.setObject(index + 1, parameter) }
            statement.executeQuery().use { rows ->
                if (rows.next()) User(rows.getInt("id"), rows.getString("name"), rows.getString("email"), rows.getString("password_hash")) else null
            }
        }
    }

    private fun hash(password: String, salt: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }): String {
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, 600_000, 256)).encoded
        val b64 = Base64.getEncoder()
        return "${b64.encodeToString(salt)}:${b64.encodeToString(key)}"
    }

    // Hashes the password again with the stored salt and compares in constant time.
    private fun verify(password: String, passwordHash: String): Boolean {
        val salt = Base64.getDecoder().decode(passwordHash.substringBefore(':'))
        return MessageDigest.isEqual(hash(password, salt).toByteArray(), passwordHash.toByteArray())
    }
}
