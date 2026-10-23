package com.example.accounts

import java.time.Duration
import java.time.Instant

// Locks an email out of POST /login after too many failed logins within a window. Kept in memory, per server.
// Unknown emails count like known ones, so a lock doesn't reveal whether an account exists.
// shortcut: an attacker can lock out a victim's email by failing on purpose, and a restart or a second server resets the counters; add a per-IP limit and a shared store when that matters.
class LoginLimiter(
    private val maxFailures: Int = 5,
    private val window: Duration = Duration.ofMinutes(15),
    private val maxEmails: Int = 10_000,
    private val now: () -> Instant = Instant::now,
) {
    // Failure times per email, oldest first, at most maxFailures each. Insertion order lets eviction drop the oldest email.
    private val failures = LinkedHashMap<String, ArrayDeque<Instant>>()

    // Same normalization as the login lookup, which ignores case.
    private fun key(email: String) = email.trim().lowercase()

    // Seconds until the email may try again, or null when it isn't locked.
    @Synchronized
    fun lockedFor(email: String): Long? {
        val times = recent(key(email)) ?: return null
        if (times.size < maxFailures) return null
        val wait = Duration.between(now(), times.first().plus(window))
        return maxOf(1, (wait.toMillis() + 999) / 1000)
    }

    @Synchronized
    fun recordFailure(email: String) {
        val key = key(email)
        val times = recent(key) ?: ArrayDeque<Instant>().also { failures[key] = it }
        if (times.size < maxFailures) times.addLast(now())
        if (failures.size > maxEmails) evict()
    }

    @Synchronized
    fun reset(email: String) {
        failures.remove(key(email))
    }

    // The failures of this email that are still inside the window, dropping the older ones; null when none are left.
    private fun recent(key: String): ArrayDeque<Instant>? {
        val times = failures[key] ?: return null
        val cutoff = now().minus(window)
        while (times.isNotEmpty() && !times.first().isAfter(cutoff)) times.removeFirst()
        if (times.isEmpty()) failures.remove(key)
        return times.takeIf { it.isNotEmpty() }
    }

    // Over the cap: drop emails whose failures all expired, then the oldest emails that are not locked, then the oldest.
    // A flood of random emails can't flush a lock until every entry is locked.
    private fun evict() {
        val cutoff = now().minus(window)
        failures.values.removeIf { !it.last().isAfter(cutoff) }
        val unlocked = failures.entries.filter { it.value.size < maxFailures }.map { it.key }.iterator()
        while (failures.size > maxEmails && unlocked.hasNext()) failures.remove(unlocked.next())
        while (failures.size > maxEmails) failures.remove(failures.keys.first())
    }
}
