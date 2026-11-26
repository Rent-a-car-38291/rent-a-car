package com.example

import io.ktor.http.Parameters
import io.ktor.http.parametersOf
import kotlin.test.*

class PagingTest {
    private fun paging(vararg pairs: Pair<String, String>, defaultLimit: Int = DEFAULT_LIMIT): Paging? =
        parsePaging(if (pairs.isEmpty()) Parameters.Empty else parametersOf(*pairs.map { it.first to listOf(it.second) }.toTypedArray()), defaultLimit)

    @Test
    fun WithoutParametersTheDefaultsAreUsed() {
        assertEquals(Paging(DEFAULT_LIMIT, 0), paging())
        assertEquals(Paging(10, 0), paging(defaultLimit = 10))
    }

    @Test
    fun TheLimitStaysBetween1AndTheMaximum() {
        assertEquals(Paging(1, 0), paging("limit" to "1"))
        assertEquals(Paging(MAX_LIMIT, 0), paging("limit" to MAX_LIMIT.toString()))
        assertNull(paging("limit" to "0"))
        assertNull(paging("limit" to (MAX_LIMIT + 1).toString()))
        assertNull(paging("limit" to "-5"))
    }

    @Test
    fun TheOffsetIsZeroOrMore() {
        assertEquals(Paging(DEFAULT_LIMIT, 40), paging("offset" to "40"))
        assertNull(paging("offset" to "-1"))
    }

    @Test
    fun ValuesThatAreNotWholeNumbersAreRejected() {
        listOf("abc", "2.5", "").forEach {
            assertNull(paging("limit" to it), "limit=$it")
            assertNull(paging("offset" to it), "offset=$it")
        }
    }

    @Test
    fun APageIsASliceOfTheList() {
        val list = (1..10).toList()
        assertEquals(listOf(1, 2, 3), list.page(Paging(3, 0)))
        assertEquals(listOf(4, 5, 6), list.page(Paging(3, 3)))
        assertEquals(listOf(10), list.page(Paging(3, 9)))
        assertEquals(emptyList(), list.page(Paging(3, 10)))
        assertEquals(emptyList(), list.page(Paging(3, Int.MAX_VALUE)))
    }
}
