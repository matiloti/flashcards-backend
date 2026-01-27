package com.flashcards.deck

import java.time.Instant
import java.util.UUID

/**
 * Optimized DTO for recently studied decks.
 * Used by GET /api/v1/decks/recent endpoint.
 * Intentionally omits createdAt and updatedAt to reduce payload size.
 */
data class RecentDeck(
    val id: UUID,
    val name: String,
    val type: DeckType,
    val cardCount: Int,
    val lastStudiedAt: Instant  // Never null for recent decks
)
