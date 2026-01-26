package com.flashcards.flashreview

import java.time.Instant
import java.util.UUID

data class StartFlashReviewResponse(
    val sessionId: UUID,
    val deckId: UUID,
    val deckName: String,
    val concepts: List<ConceptDto>,
    val totalConcepts: Int,
    val startedAt: Instant
)

data class ConceptDto(
    val id: UUID,
    val term: String,
    val notes: String?,
    val hasNotes: Boolean
)

data class CompleteFlashReviewRequest(
    val conceptsViewed: Int? = null
)

data class FlashReviewSummary(
    val sessionId: UUID,
    val deckId: UUID,
    val deckName: String,
    val totalConcepts: Int,
    val conceptsViewed: Int,
    val startedAt: Instant,
    val completedAt: Instant,
    val durationSeconds: Long
)

data class FlashReviewSessionStatus(
    val sessionId: UUID,
    val deckId: UUID,
    val deckName: String,
    val totalConcepts: Int,
    val startedAt: Instant,
    val completedAt: Instant?,
    val isCompleted: Boolean
)
