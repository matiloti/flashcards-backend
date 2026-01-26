package com.flashcards.study

import java.time.Instant
import java.util.UUID

enum class SessionType {
    STUDY,
    FLASH_REVIEW
}

data class StudySession(
    val id: UUID,
    val deckId: UUID,
    val sessionType: SessionType = SessionType.STUDY,
    val startedAt: Instant,
    val completedAt: Instant? = null,
    val conceptsViewed: Int? = null
)

data class CardReview(
    val id: UUID,
    val sessionId: UUID,
    val cardId: UUID,
    val rating: Rating,
    val reviewedAt: Instant
)

enum class Rating {
    EASY, HARD, AGAIN
}

data class StartSessionResponse(
    val sessionId: UUID,
    val deckId: UUID,
    val deckName: String,
    val cards: List<StudyCard>,
    val totalCards: Int,
    val startedAt: Instant
)

data class StudyCard(
    val id: UUID,
    val frontText: String,
    val backText: String
)

data class ReviewRequest(
    val cardId: UUID,
    val rating: Rating
)

data class ReviewResponse(
    val id: UUID,
    val sessionId: UUID,
    val cardId: UUID,
    val rating: Rating,
    val reviewedAt: Instant
)

data class SessionSummary(
    val sessionId: UUID,
    val deckId: UUID,
    val deckName: String,
    val totalCards: Int,
    val easyCount: Int,
    val hardCount: Int,
    val againCount: Int,
    val startedAt: Instant,
    val completedAt: Instant
)
