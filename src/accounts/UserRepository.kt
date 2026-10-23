package com.example.accounts

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.stringParam
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.sql.DataSource

private const val UNIQUE_VIOLATION = "23505"
internal const val MAX_PASSWORD_LENGTH = 128

// Serial ids only go up, so the id of a deleted user is never handed out again.
internal object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val name = text("name")
    val email = text("email")
    val passwordHash = text("password_hash")
    override val primaryKey = PrimaryKey(id)

    // One account per email address, ignoring case.
    init {
        index("users_email_key", isUnique = true, functions = listOf(email.lowerCase()))
    }
}

internal fun Database.createUsers() = transaction(this) {
    SchemaUtils.create(Users)
    // Reserved owner for the sample fleet; its random password hash has no known password.
    // Checked first: a users table from the old SQL script has a GENERATED ALWAYS id, which refuses an explicit id even when the row exists.
    if (Users.selectAll().where { Users.id eq -1 }.empty()) {
        Users.insert {
            it[id] = -1
            it[name] = "Sample fleet"
            it[email] = "sample-fleet@rent-a-car.invalid"
            it[passwordHash] = "ej0M8Ts0bX5FqZlbL0eW7A==:tSdgcA12/mS+4UQvxObL5qo/ieZi7VUbZQubxBaG6hg="
        }
    }
}

private fun ResultRow.toUser() = User(this[Users.id], this[Users.name], this[Users.email], this[Users.passwordHash])

class UserRepository(private val dataSource: DataSource) {
    private val db = Database.connect(dataSource)
    private val dummyHash by lazy { hash("dummy") }

    init {
        db.createUsers()
    }

    // null when the email is taken: the unique index on lower(email) decides, so two requests at once can't both win.
    fun register(name: String, email: String, password: String): User? {
        val passwordHash = hash(password) // slow, so keep it away from the database connection
        return transaction(db) {
            val inserted = Users.insertIgnore {
                it[Users.name] = name
                it[Users.email] = email
                it[Users.passwordHash] = passwordHash
            }
            if (inserted.insertedCount > 0) inserted.resultedValues!!.single().toUser() else null
        }
    }

    // An unknown email is verified against a dummy hash, so it costs as much time as a known one
    // and the response time doesn't reveal which emails have an account.
    fun authenticate(email: String, password: String): User? {
        val user = transaction(db) { Users.selectAll().where { Users.email.lowerCase() eq stringParam(email).lowerCase() }.singleOrNull()?.toUser() }
        val passwordMatches = verify(password, user?.passwordHash ?: dummyHash)
        return user?.takeIf { passwordMatches }
    }

    fun find(id: Int): User? = transaction(db) { Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUser() }

    // null when the email belongs to another account (the unique index decides, so two requests at once can't both win),
    // or when the user was deleted meanwhile: shortcut, the route reports both as 409.
    fun update(id: Int, name: String, email: String): User? = try {
        transaction(db) {
            Users.update({ Users.id eq id }) {
                it[Users.name] = name
                it[Users.email] = email
            }
            Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUser()
        }
    } catch (e: ExposedSQLException) {
        if (e.sqlState == UNIQUE_VIOLATION) null else throw e
    }

    // false when the current password is wrong. The update only matches the hash that was verified,
    // so two changes at once can't both win.
    // shortcut: the loser of such a race (or a user deleted meanwhile) also gets false, so the route answers 403, give it its own result when that matters.
    fun changePassword(id: Int, currentPassword: String, newPassword: String): Boolean {
        val user = find(id) ?: return false
        if (!verify(currentPassword, user.passwordHash)) return false
        val newHash = hash(newPassword) // slow, so keep it away from the database connection
        return transaction(db) {
            Users.update({ (Users.id eq id) and (Users.passwordHash eq user.passwordHash) }) { it[passwordHash] = newHash } > 0
        }
    }

    fun delete(id: Int): Boolean = transaction(db) { Users.deleteWhere { Users.id eq id } > 0 }

    fun clear() {
        transaction(db) { exec("DELETE FROM users WHERE id <> -1; DO \$\$ BEGIN PERFORM setval(pg_get_serial_sequence('users', 'id'), 1, false); END \$\$") }
    }

    private fun hash(password: String, salt: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }): String {
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, 600_000, 256)).encoded
        val b64 = Base64.getEncoder()
        return "${b64.encodeToString(salt)}:${b64.encodeToString(key)}"
    }

    // Hashes the password again with the stored salt and compares in constant time.
    // A password above the maximum can't have been stored, so it never matches and is not hashed.
    private fun verify(password: String, passwordHash: String): Boolean {
        if (password.length > MAX_PASSWORD_LENGTH) return false
        val salt = Base64.getDecoder().decode(passwordHash.substringBefore(':'))
        return MessageDigest.isEqual(hash(password, salt).toByteArray(), passwordHash.toByteArray())
    }
}
