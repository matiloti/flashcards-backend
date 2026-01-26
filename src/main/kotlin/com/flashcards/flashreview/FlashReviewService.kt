package com.flashcards.flashreview

import com.flashcards.card.CardRepository
import com.flashcards.deck.DeckRepository
import com.flashcards.deck.DeckType
import com.flashcards.study.SessionType
import com.flashcards.study.StudyRepository
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

@Service
class FlashReviewService(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val studyRepository: StudyRepository
) {

    sealed class StartSessionResult {
        data class Success(val response: StartFlashReviewResponse) : StartSessionResult()
        data object DeckNotFound : StartSessionResult()
        data class InvalidDeckType(val actualType: DeckType) : StartSessionResult()
        data object EmptyDeck : StartSessionResult()
    }

    sealed class CompleteSessionResult {
        data class Success(val summary: FlashReviewSummary) : CompleteSessionResult()
        data object SessionNotFound : CompleteSessionResult()
        data object AlreadyCompleted : CompleteSessionResult()
    }

    sealed class GetSessionResult {
        data class Success(val status: FlashReviewSessionStatus) : GetSessionResult()
        data object SessionNotFound : GetSessionResult()
    }

    fun startSession(deckId: UUID, shuffle: Boolean = true): StartSessionResult {
        val deck = deckRepository.findById(deckId)
            ?: return StartSessionResult.DeckNotFound

        if (deck.type != DeckType.FLASH_REVIEW) {
            return StartSessionResult.InvalidDeckType(deck.type)
        }

        val cards = cardRepository.findByDeckId(deckId)
        if (cards.isEmpty()) {
            return StartSessionResult.EmptyDeck
        }

        val session = studyRepository.createSession(deckId, SessionType.FLASH_REVIEW)

        val concepts = cards
            .let { if (shuffle) it.shuffled() else it }
            .map { card ->
                ConceptDto(
                    id = card.id,
                    term = card.frontText,
                    notes = card.backText,
                    hasNotes = !card.backText.isNullOrBlank()
                )
            }

        return StartSessionResult.Success(
            StartFlashReviewResponse(
                sessionId = session.id,
                deckId = deckId,
                deckName = deck.name,
                concepts = concepts,
                totalConcepts = concepts.size,
                startedAt = session.startedAt
            )
        )
    }

    fun completeSession(sessionId: UUID, conceptsViewed: Int?): CompleteSessionResult {
        val session = studyRepository.findSessionById(sessionId)
            ?: return CompleteSessionResult.SessionNotFound

        if (session.sessionType != SessionType.FLASH_REVIEW) {
            return CompleteSessionResult.SessionNotFound
        }

        if (session.completedAt != null) {
            return CompleteSessionResult.AlreadyCompleted
        }

        val deck = deckRepository.findById(session.deckId)
            ?: return CompleteSessionResult.SessionNotFound

        val totalConcepts = cardRepository.findByDeckId(session.deckId).size
        val actualConceptsViewed = conceptsViewed ?: totalConcepts

        val completedAt = studyRepository.completeFlashReviewSession(sessionId, actualConceptsViewed)

        val durationSeconds = Duration.between(session.startedAt, completedAt).seconds

        return CompleteSessionResult.Success(
            FlashReviewSummary(
                sessionId = sessionId,
                deckId = session.deckId,
                deckName = deck.name,
                totalConcepts = totalConcepts,
                conceptsViewed = actualConceptsViewed,
                startedAt = session.startedAt,
                completedAt = completedAt,
                durationSeconds = durationSeconds
            )
        )
    }

    fun getSession(sessionId: UUID): GetSessionResult {
        val session = studyRepository.findSessionById(sessionId)
            ?: return GetSessionResult.SessionNotFound

        if (session.sessionType != SessionType.FLASH_REVIEW) {
            return GetSessionResult.SessionNotFound
        }

        val deck = deckRepository.findById(session.deckId)
            ?: return GetSessionResult.SessionNotFound

        val totalConcepts = cardRepository.findByDeckId(session.deckId).size

        return GetSessionResult.Success(
            FlashReviewSessionStatus(
                sessionId = sessionId,
                deckId = session.deckId,
                deckName = deck.name,
                totalConcepts = totalConcepts,
                startedAt = session.startedAt,
                completedAt = session.completedAt,
                isCompleted = session.completedAt != null
            )
        )
    }
}
