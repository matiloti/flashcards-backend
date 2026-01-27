package com.flashcards.deck

import java.util.UUID

data class CreateDeckRequest(
    val name: String,
    val type: DeckType? = null,
    val tagIds: List<UUID>? = null
)
