package com.flashcards.deck

import java.time.Instant
import java.util.UUID

data class Deck(
    val id: UUID,
    val name: String,
    val type: DeckType = DeckType.STUDY,
    val cardCount: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant
)
