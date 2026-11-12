package com.example.accounts

import java.time.Duration
import java.time.Instant
import kotlin.test.*

class LoginLimiterTest {
    private var now = Instant.parse("2026-10-23T10:00:00Z")
    private fun limiter(maxEmails: Int = 10_000) = LoginLimiter(maxFailures = 5, window = Duration.ofMinutes(15), maxEmails = maxEmails, now = { now })

    private fun LoginLimiter.fail(email: String, times: Int) = repeat(times) { tryAcquire(email) }

    @Test
    fun `an email is open until the fifth failure`() {
        val limiter = limiter()

        limiter.fail("jan@example.com", 4)
        assertNull(limiter.lockedFor("jan@example.com"))

        limiter.tryAcquire("jan@example.com")
        assertNotNull(limiter.lockedFor("jan@example.com"))
    }

    @Test
    fun `the wait runs from the oldest failure and rounds up to whole seconds`() {
        val limiter = limiter()
        limiter.tryAcquire("jan@example.com")
        now = now.plusSeconds(60)
        limiter.fail("jan@example.com", 4)
        now = now.plusMillis(500)

        // The first failure was 60.5 s ago, so 839.5 s remain.
        assertEquals(840, limiter.lockedFor("jan@example.com"))
    }

    @Test
    fun `failures older than the window no longer count`() {
        val limiter = limiter()
        limiter.fail("jan@example.com", 4)
        now = now.plus(Duration.ofMinutes(15))

        limiter.tryAcquire("jan@example.com")

        assertNull(limiter.lockedFor("jan@example.com"))
    }

    @Test
    fun `a lock ends when the oldest failure leaves the window`() {
        val limiter = limiter()
        limiter.fail("jan@example.com", 5)

        now = now.plus(Duration.ofMinutes(15)).minusSeconds(1)
        assertEquals(1, limiter.lockedFor("jan@example.com"))

        now = now.plusSeconds(1)
        assertNull(limiter.lockedFor("jan@example.com"))
    }

    @Test
    fun `a locked email does not extend its own lock by failing more`() {
        val limiter = limiter()
        limiter.fail("jan@example.com", 5)
        now = now.plus(Duration.ofMinutes(10))

        limiter.fail("jan@example.com", 3)

        assertEquals(300, limiter.lockedFor("jan@example.com"))
    }

    @Test
    fun `a success resets the counter`() {
        val limiter = limiter()
        limiter.fail("jan@example.com", 4)

        limiter.reset("jan@example.com")
        limiter.tryAcquire("jan@example.com")

        assertNull(limiter.lockedFor("jan@example.com"))
    }

    @Test
    fun `case and surrounding spaces make no difference`() {
        val limiter = limiter()

        limiter.tryAcquire("Jan@Example.com")
        limiter.tryAcquire(" jan@example.com ")
        limiter.fail("JAN@EXAMPLE.COM", 3)

        assertNotNull(limiter.lockedFor("jan@example.com"))
    }

    @Test
    fun `other emails are not affected`() {
        val limiter = limiter()
        limiter.fail("jan@example.com", 5)

        assertNull(limiter.lockedFor("piet@example.com"))
    }

    @Test
    fun `memory stays bounded and a flood of unknown emails cannot flush a lock`() {
        val limiter = limiter(maxEmails = 3)
        limiter.fail("locked@example.com", 5)

        repeat(50) { limiter.tryAcquire("guess$it@example.com") }

        assertNotNull(limiter.lockedFor("locked@example.com"))
        assertNull(limiter.lockedFor("guess0@example.com"))
    }

    @Test
    fun `expired emails are dropped before live ones when the cap is reached`() {
        val limiter = limiter(maxEmails = 2)
        limiter.fail("old@example.com", 5)
        now = now.plus(Duration.ofMinutes(16))
        limiter.fail("live@example.com", 5)

        limiter.tryAcquire("new@example.com")

        // Without the expired-first step "new" would be the oldest unlocked entry and be dropped, losing its attempt.
        assertNull(limiter.lockedFor("old@example.com"))
        assertNotNull(limiter.lockedFor("live@example.com"))
        limiter.fail("new@example.com", 4)
        assertNotNull(limiter.lockedFor("new@example.com"))
    }

    @Test
    fun `an attempt is counted when it is acquired and a locked email is refused with the seconds left`() {
        val limiter = limiter()

        repeat(5) { assertNull(limiter.tryAcquire("jan@example.com")) }

        assertEquals(900, limiter.tryAcquire("jan@example.com"))
    }

    @Test
    fun `parallel attempts cannot all pass before they are counted`() {
        val limiter = LoginLimiter()
        val allowed = java.util.concurrent.atomic.AtomicInteger()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(16)
        val start = java.util.concurrent.CountDownLatch(1)

        val jobs = (1..200).map {
            pool.submit {
                start.await()
                if (limiter.tryAcquire("victim@example.com") == null) allowed.incrementAndGet()
            }
        }
        start.countDown()
        jobs.forEach { it.get() }
        pool.shutdown()

        assertEquals(5, allowed.get())
    }
}
