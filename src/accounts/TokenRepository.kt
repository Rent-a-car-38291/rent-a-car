package com.example.accounts

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.util.HexFormat
import java.util.UUID
import javax.sql.DataSource

internal val TOKEN_LIFETIME: Duration = Duration.ofDays(7)

// Only the SHA-256 of a token is stored, so a copy of the table can't be used to log in. Removing a user removes the tokens.
internal object Tokens : Table("tokens") {
    val hash = text("token_hash")
    val userId = integer("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val expiresAt = timestampWithTimeZone("expires_at").index()
    override val primaryKey = PrimaryKey(hash)
}

// An expired token is refused like an unknown one. Expired rows are purged whenever a token is issued.
class TokenRepository(dataSource: DataSource, private val clock: Clock = Clock.systemUTC()) {
    private val db = Database.connect(dataSource)

    init {
        db.createUsers() // tokens reference users
        transaction(db) { SchemaUtils.create(Tokens) }
    }

    // A random UUID comes from SecureRandom, so it can't be guessed. The token itself is only returned here, once.
    fun issue(userId: Int): String = UUID.randomUUID().toString().also { token ->
        val now = OffsetDateTime.now(clock)
        transaction(db) {
            Tokens.deleteWhere { expiresAt lessEq now }
            Tokens.insert {
                it[hash] = hash(token)
                it[Tokens.userId] = userId
                it[expiresAt] = now.plus(TOKEN_LIFETIME)
            }
        }
    }

    fun userId(token: String): Int? = transaction(db) {
        Tokens.select(Tokens.userId)
            .where { (Tokens.hash eq hash(token)) and (Tokens.expiresAt greater OffsetDateTime.now(clock)) }
            .singleOrNull()?.get(Tokens.userId)
    }

    fun revoke(token: String) {
        transaction(db) { Tokens.deleteWhere { hash eq hash(token) } }
    }

    fun revokeAll(userId: Int, except: String? = null) {
        val kept = except?.let(::hash)
        transaction(db) { Tokens.deleteWhere { (Tokens.userId eq userId) and (if (kept == null) Op.TRUE else hash neq kept) } }
    }

    private fun hash(token: String) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))
}
