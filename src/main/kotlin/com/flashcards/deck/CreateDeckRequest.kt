package com.flashcards.deck

data class CreateDeckRequest(
    val name: String,
    val type: DeckType? = null
)
