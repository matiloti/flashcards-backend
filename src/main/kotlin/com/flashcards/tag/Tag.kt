package com.flashcards.tag

import java.time.Instant
import java.util.UUID

/**
 * Domain entity representing a user-created tag for organizing decks.
 */
data class Tag(
    val id: UUID,
    val name: String,
    val userId: UUID,
    val createdAt: Instant
)

/**
 * Tag with deck count for list responses.
 */
data class TagWithDeckCount(
    val id: UUID,
    val name: String,
    val userId: UUID,
    val deckCount: Int,
    val createdAt: Instant
)
