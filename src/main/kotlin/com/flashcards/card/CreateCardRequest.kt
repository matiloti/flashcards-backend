package com.flashcards.card

data class CreateCardRequest(
    val frontText: String,
    val backText: String? = null
)
