package com.flashcards.card

import java.time.Instant
import java.util.UUID

data class Card(
    val id: UUID,
    val deckId: UUID,
    val frontText: String,
    val backText: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)
