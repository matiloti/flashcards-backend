package com.flashcards.tag

import java.time.Instant
import java.util.UUID

/**
 * Full tag response DTO returned by tag API endpoints.
 */
data class TagResponse(
    val id: UUID,
    val name: String,
    val deckCount: Int,
    val createdAt: Instant
)

/**
 * Minimal tag summary for inclusion in deck responses.
 * Used to avoid N+1 queries when listing decks with tags.
 */
data class TagSummary(
    val id: UUID,
    val name: String
)

/**
 * Paginated response for tag list endpoint.
 */
data class TagListResponse(
    val content: List<TagResponse>,
    val totalElements: Long,
    val page: Int,
    val size: Int
)
