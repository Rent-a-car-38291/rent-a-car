package com.example.accounts

import com.example.database.TestDatabase
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.HexFormat
import kotlin.test.*

class TokenRepositoryTest {
    private val users = UserRepository(TestDatabase.dataSource)
    private val tokens = TokenRepository(TestDatabase.dataSource)

    // A clock the test can move forward.
    private class MovableClock(private var now: Instant = Instant.now()) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
        override fun instant() = now
        fun advance(by: Duration) { now += by }
    }

    private var emailCounter = 0
    private fun user() = users.register("Jan", "token${emailCounter++}@example.com", "supersecret")!!.id

    @BeforeTest
    fun reset() = users.clear()

    @Test
    fun RevokeAllRemovesEveryTokenOfThatUserOnly() {
        val (mine, other) = user() to user()
        val first = tokens.issue(mine)
        val second = tokens.issue(mine)
        val otherToken = tokens.issue(other)

        tokens.revokeAll(mine)

        assertNull(tokens.userId(first))
        assertNull(tokens.userId(second))
        assertEquals(other, tokens.userId(otherToken))
    }

    @Test
    fun RevokeRemovesOnlyThatToken() {
        val id = user()
        val first = tokens.issue(id)
        val second = tokens.issue(id)

        tokens.revoke(first)

        assertNull(tokens.userId(first))
        assertEquals(id, tokens.userId(second))
    }

    @Test
    fun RevokeAllCanKeepOneToken() {
        val id = user()
        val kept = tokens.issue(id)
        val revoked = tokens.issue(id)

        tokens.revokeAll(id, except = kept)

        assertEquals(id, tokens.userId(kept))
        assertNull(tokens.userId(revoked))
    }

    @Test
    fun ATokenStillWorksForANewRepositoryOnTheSameDatabase() {
        val id = user()
        val token = tokens.issue(id)

        assertEquals(id, TokenRepository(TestDatabase.dataSource).userId(token))
    }

    @Test
    fun AnUnknownTokenGivesNull() {
        assertNull(tokens.userId("not-a-token"))
    }

    @Test
    fun ATokenWorksUntilSevenDaysAfterLoginAndIsRefusedAfterwards() {
        val clock = MovableClock()
        val repository = TokenRepository(TestDatabase.dataSource, clock)
        val id = user()
        val token = repository.issue(id)

        clock.advance(TOKEN_LIFETIME - Duration.ofSeconds(1))
        assertEquals(id, repository.userId(token))

        clock.advance(Duration.ofSeconds(1))
        assertNull(repository.userId(token))
    }

    @Test
    fun IssuingATokenPurgesTheExpiredOnes() {
        val clock = MovableClock()
        val repository = TokenRepository(TestDatabase.dataSource, clock)
        val id = user()
        repository.issue(id)

        clock.advance(TOKEN_LIFETIME + Duration.ofMinutes(1))
        repository.issue(id)

        assertEquals(1, rowCount())
    }

    @Test
    fun OnlyTheHashOfATokenIsStored() {
        val token = tokens.issue(user())

        val stored = TestDatabase.dataSource.connection.use { c ->
            c.createStatement().executeQuery("SELECT token_hash FROM tokens").use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() }
        }

        assertEquals(1, stored.size)
        assertNotEquals(token, stored.single())
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        assertEquals(HexFormat.of().formatHex(digest), stored.single())
    }

    @Test
    fun DeletingAUserRemovesTheirTokens() {
        val id = user()
        tokens.issue(id)

        users.delete(id)

        assertEquals(0, rowCount())
    }

    private fun rowCount() = TestDatabase.dataSource.connection.use { c ->
        c.createStatement().executeQuery("SELECT count(*) FROM tokens").use { rs -> rs.next(); rs.getInt(1) }
    }
}
