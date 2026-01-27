package com.flashcards.deck

import java.time.Instant
import java.util.UUID

/**
 * DTO for deck search results.
 * Includes matchedField to indicate where the search query matched.
 */
data class DeckSearchResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val type: DeckType,
    val cardCount: Int,
    val lastStudiedAt: Instant?,
    /** Which field matched the search query: "name" or "description" */
    val matchedField: String,
    val createdAt: Instant,
    val updatedAt: Instant
)
