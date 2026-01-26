package com.flashcards.study

import com.flashcards.card.CardRepository
import com.flashcards.deck.DeckRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
class StudyController(
    private val studyRepository: StudyRepository,
    private val cardRepository: CardRepository,
    private val deckRepository: DeckRepository
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
        val counts = studyRepository.getReviewCounts(sessionId)

        val summary = SessionSummary(
            sessionId = sessionId,
            deckId = session.deckId,
            deckName = deck.name,
            totalCards = counts.values.sum(),
            easyCount = counts[Rating.EASY] ?: 0,
            hardCount = counts[Rating.HARD] ?: 0,
            againCount = counts[Rating.AGAIN] ?: 0,
            startedAt = session.startedAt,
            completedAt = completedAt
        )
        return ResponseEntity.ok(summary)
    }
}
