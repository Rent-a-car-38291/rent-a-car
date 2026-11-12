package com.example.ratings

import kotlin.test.*

class RatingTest {
    @Test
    fun StarsOneToFiveAreValid() {
        (1..5).forEach { assertEquals(it, RatingRequest(it).cleaned()!!.stars) }
    }

    @Test
    fun StarsOutsideOneToFiveAreInvalid() {
        listOf(0, 6, -1, Int.MAX_VALUE).forEach { assertNull(RatingRequest(it).cleaned(), "stars $it") }
    }

    @Test
    fun ACommentOfExactlyTheMaximumLengthIsValidAndOneMoreIsNot() {
        assertNotNull(RatingRequest(5, "a".repeat(MAX_COMMENT_LENGTH)).cleaned())
        assertNull(RatingRequest(5, "a".repeat(MAX_COMMENT_LENGTH + 1)).cleaned())
    }

    @Test
    fun TheCommentIsTrimmedBeforeItIsCounted() {
        assertEquals("nice", RatingRequest(4, "  nice \n").cleaned()!!.comment)
        assertNotNull(RatingRequest(4, " " + "a".repeat(MAX_COMMENT_LENGTH) + " ").cleaned())
    }

    @Test
    fun ABlankOrMissingCommentBecomesNull() {
        assertNull(RatingRequest(3, "   ").cleaned()!!.comment)
        assertNull(RatingRequest(3).cleaned()!!.comment)
    }

    @Test
    fun WithoutRatingsTheAverageIsNull() {
        assertNull(averageRating(0, 0))
    }

    @Test
    fun TheAverageIsRoundedToOneDecimalHalfUp() {
        assertEquals(4.0, averageRating(8, 2))
        assertEquals(4.3, averageRating(13, 3)) // 4.333...
        assertEquals(4.7, averageRating(14, 3)) // 4.666...
        assertEquals(4.6, averageRating(23, 5)) // exactly 4.6
        assertEquals(3.5, averageRating(7, 2))
        assertEquals(4.1, averageRating(82, 20)) // exactly 4.1, a double product would not be exact
    }

    @Test
    fun TheAverageOfOneRatingIsThatRating() {
        (1..5).forEach { assertEquals(it.toDouble(), averageRating(it.toLong(), 1)) }
    }

    @Test
    fun AnExactHalfTenthRoundsUp() {
        assertEquals(4.1, averageRating(81, 20)) // 4.05
    }
}
