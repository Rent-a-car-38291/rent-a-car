package com.example.accounts

import kotlin.test.*

class TokenRepositoryTest {
    @Test
    fun RevokeAllRemovesEveryTokenOfThatUserOnly() {
        val first = TokenRepository.issue(101)
        val second = TokenRepository.issue(101)
        val other = TokenRepository.issue(102)

        TokenRepository.revokeAll(101)

        assertNull(TokenRepository.userId(first))
        assertNull(TokenRepository.userId(second))
        assertEquals(102, TokenRepository.userId(other))
    }

    @Test
    fun RevokeRemovesThatTokenOnly() {
        val first = TokenRepository.issue(103)
        val second = TokenRepository.issue(103)

        TokenRepository.revoke(first)

        assertNull(TokenRepository.userId(first))
        assertEquals(103, TokenRepository.userId(second))
    }
}
