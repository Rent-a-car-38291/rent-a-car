package com.example

import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header

const val DEFAULT_LIMIT = 20
const val MAX_LIMIT = 100
const val TOTAL_COUNT_HEADER = "X-Total-Count"

data class Paging(val limit: Int, val offset: Int)

/** The limit and offset of a list request; null when limit is not a whole number from 1 to MAX_LIMIT or offset is not a whole number from 0 up. */
fun parsePaging(query: Parameters, defaultLimit: Int = DEFAULT_LIMIT): Paging? {
    val limit = query["limit"]?.let { it.toIntOrNull() ?: return null } ?: defaultLimit
    val offset = query["offset"]?.let { it.toIntOrNull() ?: return null } ?: 0
    return Paging(limit, offset).takeIf { limit in 1..MAX_LIMIT && offset >= 0 }
}

/** The part of the list from the offset on, at most limit long. */
fun <T> List<T>.page(paging: Paging): List<T> = drop(paging.offset).take(paging.limit)

/** Sets the size of the whole list in X-Total-Count, so a client knows how many pages there are. The response body stays the list of the page. */
fun ApplicationCall.setTotalCount(total: Int) = response.header(TOTAL_COUNT_HEADER, total.toString())
