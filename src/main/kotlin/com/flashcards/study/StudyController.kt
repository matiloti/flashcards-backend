package com.flashcards.study

import com.flashcards.card.CardRepository
import com.flashcards.deck.DeckRepository
import com.flashcards.statistics.StatisticsService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

@RestController
class StudyController(
    private val studyRepository: StudyRepository,
    private val cardRepository: CardRepository,
    private val deckRepository: DeckRepository,
    private val statisticsService: StatisticsService
) {

    @PostMapping("/api/v1/decks/{deckId}/study")
    fun startSession(@PathVariable deckId: UUID): ResponseEntity<StartSessionResponse> {
        val deck = deckRepository.findById(deckId)
            ?: return ResponseEntity.notFound().build()

        val cards = cardRepository.findByDeckId(deckId)
        if (cards.isEmpty()) {
            return ResponseEntity.badRequest().build()
        }

        val session = studyRepository.createSession(deckId)
        val studyCards = cards.shuffled().map { StudyCard(id = it.id, frontText = it.frontText, backText = it.backText ?: "") }

        val response = StartSessionResponse(
            sessionId = session.id,
            deckId = deckId,
            deckName = deck.name,
            cards = studyCards,
            totalCards = studyCards.size,
            startedAt = session.startedAt
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/api/v1/study/{sessionId}/reviews")
    fun submitReview(
        @PathVariable sessionId: UUID,
        @RequestBody request: ReviewRequest
    ): ResponseEntity<ReviewResponse> {
        studyRepository.findSessionById(sessionId)
            ?: return ResponseEntity.notFound().build()

        val review = studyRepository.createReview(sessionId, request.cardId, request.rating)

        // Update card progress for statistics tracking
        statisticsService.recordCardReview(
            cardId = request.cardId,
            rating = request.rating.name,
            reviewedAt = review.reviewedAt
        )

        val response = ReviewResponse(
            id = review.id,
            sessionId = review.sessionId,
            cardId = review.cardId,
            rating = review.rating,
            reviewedAt = review.reviewedAt
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/api/v1/study/{sessionId}/complete")
    fun completeSession(@PathVariable sessionId: UUID): ResponseEntity<SessionSummary> {
        val session = studyRepository.findSessionById(sessionId)
            ?: return ResponseEntity.notFound().build()

        val deck = deckRepository.findById(session.deckId)
            ?: return ResponseEntity.notFound().build()

        val completedAt = studyRepository.completeSession(sessionId)

        // Update deck's lastStudiedAt timestamp
        deckRepository.updateLastStudiedAt(session.deckId, completedAt)

        val counts = studyRepository.getReviewCounts(sessionId)

        val easyCount = counts[Rating.EASY] ?: 0
        val hardCount = counts[Rating.HARD] ?: 0
        val againCount = counts[Rating.AGAIN] ?: 0
        val totalCards = counts.values.sum()

        // Calculate session duration in minutes
        val durationMinutes = ChronoUnit.MINUTES.between(session.startedAt, completedAt).toInt()
            .coerceAtLeast(1) // Minimum 1 minute

        // Update statistics (using UTC for now; timezone handling could be added)
        statisticsService.recordSessionCompletion(
            cardsStudied = totalCards,
            easyCount = easyCount,
            hardCount = hardCount,
            againCount = againCount,
            sessionDurationMinutes = durationMinutes,
            zoneId = ZoneId.of("UTC")
        )

        val summary = SessionSummary(
            sessionId = sessionId,
            deckId = session.deckId,
            deckName = deck.name,
            totalCards = totalCards,
            easyCount = easyCount,
            hardCount = hardCount,
            againCount = againCount,
            missedCount = hardCount + againCount,
            parentSessionId = session.parentSessionId,
            retakeType = session.retakeType,
            startedAt = session.startedAt,
            completedAt = completedAt
        )
        return ResponseEntity.ok(summary)
    }

    @PostMapping("/api/v1/study/{sessionId}/retake-missed")
    fun retakeMissed(@PathVariable sessionId: UUID): ResponseEntity<Any> {
        // Validate session exists
        val session = studyRepository.findSessionById(sessionId)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse("Session not found"))

        // Validate session is completed
        if (session.completedAt == null) {
            return ResponseEntity.badRequest()
                .body(ErrorResponse("Cannot retake: session is not completed"))
        }

        // Validate deck still exists
        val deck = deckRepository.findById(session.deckId)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse("Cannot retake: deck no longer exists"))

        // Find missed cards (HARD or AGAIN) that still exist
        val missedCards = studyRepository.findMissedCardsBySessionId(sessionId)

        // Validate at least one missed card exists
        // Note: Due to ON DELETE CASCADE on card_reviews.card_id, if all missed cards
        // are deleted, their reviews are also deleted. This makes it indistinguishable
        // from sessions that had no missed cards originally.
        if (missedCards.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(ErrorResponse("Cannot retake: no cards were rated Hard or Again in this session"))
        }

        // Get original session card count
        val originalSessionCards = studyRepository.countTotalReviews(sessionId)

        // Create new retake session
        val retakeSession = studyRepository.createSession(
            deckId = session.deckId,
            sessionType = SessionType.STUDY,
            parentSessionId = sessionId,
            retakeType = RetakeType.MISSED_ONLY
        )

        val response = RetakeMissedResponse(
            sessionId = retakeSession.id,
            deckId = session.deckId,
            deckName = deck.name,
            parentSessionId = sessionId,
            retakeType = RetakeType.MISSED_ONLY,
            cards = missedCards,
            totalCards = missedCards.size,
            originalSessionCards = originalSessionCards,
            startedAt = retakeSession.startedAt
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
}
