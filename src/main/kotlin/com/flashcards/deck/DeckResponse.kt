package com.flashcards.deck

import com.flashcards.tag.TagSummary
import java.time.Instant
import java.util.UUID

/**
 * Deck response DTO that includes tags.
 * Used for all deck API responses to ensure tags are always included.
 */
data class DeckResponse(
    val id: UUID,
    val name: String,
    val type: DeckType = DeckType.STUDY,
    val cardCount: Int = 0,
    val tags: List<TagSummary> = emptyList(),
    val lastStudiedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Extension function to convert Deck to DeckResponse.
 */
fun Deck.toResponse(tags: List<TagSummary> = emptyList()) = DeckResponse(
    id = this.id,
    name = this.name,
    type = this.type,
    cardCount = this.cardCount,
    tags = tags,
    lastStudiedAt = this.lastStudiedAt,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt
)
