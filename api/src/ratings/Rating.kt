package com.example.ratings

import kotlinx.serialization.Serializable

internal const val MAX_COMMENT_LENGTH = 500

// stars is 1 to 5, the comment is optional.
@Serializable
data class RatingRequest(val stars: Int, val comment: String? = null)

// No data of the renter: a rating only says what a car is like.
@Serializable
data class Rating(val id: Int, val reservationId: Int, val carId: Int, val stars: Int, val comment: String?)

// averageRating is null without ratings.
@Serializable
data class CarRatings(val carId: Int, val averageRating: Double?, val ratingCount: Int, val ratings: List<Rating>)

sealed interface RateResult {
    data class Rated(val rating: Rating) : RateResult
    // The reservation has no finished trip (a started trip is not finished) or is gone.
    data object NoTrip : RateResult
    data object AlreadyRated : RateResult
}

// The request with a trimmed comment (blank becomes null), or null when the stars are not 1 to 5 or the comment is too long.
fun RatingRequest.cleaned(): RatingRequest? {
    val comment = comment?.trim()?.ifEmpty { null }
    return if (stars in 1..5 && (comment?.length ?: 0) <= MAX_COMMENT_LENGTH) RatingRequest(stars, comment) else null
}

// The average in whole tenths, rounded half up in integers: a double can land just under an exact .x5. Null without ratings.
fun averageRating(starsSum: Long, count: Int): Double? =
    if (count == 0) null else (starsSum * 20 + count) / (2 * count) / 10.0
