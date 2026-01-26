package com.flashcards.card

import com.flashcards.deck.Deck
import com.flashcards.deck.DeckRepository
import com.flashcards.deck.DeckType
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/decks/{deckId}/cards")
class CardController(
    private val cardRepository: CardRepository,
    private val deckRepository: DeckRepository
) {

    @GetMapping
    fun listCards(@PathVariable deckId: UUID): ResponseEntity<List<Card>> {
        deckRepository.findById(deckId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(cardRepository.findByDeckId(deckId))
    }

    @GetMapping("/{cardId}")
    fun getCard(@PathVariable deckId: UUID, @PathVariable cardId: UUID): ResponseEntity<Card> {
        deckRepository.findById(deckId) ?: return ResponseEntity.notFound().build()
        val card = cardRepository.findById(deckId, cardId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(card)
    }

    @PostMapping
    fun createCard(
        @PathVariable deckId: UUID,
        @RequestBody request: CreateCardRequest
    ): ResponseEntity<Card> {
        val deck = deckRepository.findById(deckId) ?: return ResponseEntity.notFound().build()

        val frontText = request.frontText.trim()
        val backText = request.backText?.trim()

        if (!validateCard(deck, frontText, backText)) {
            return ResponseEntity.badRequest().build()
        }

        val card = cardRepository.create(deckId, frontText, backText)
        return ResponseEntity.status(HttpStatus.CREATED).body(card)
    }

    @PutMapping("/{cardId}")
    fun updateCard(
        @PathVariable deckId: UUID,
        @PathVariable cardId: UUID,
        @RequestBody request: UpdateCardRequest
    ): ResponseEntity<Card> {
        val deck = deckRepository.findById(deckId) ?: return ResponseEntity.notFound().build()

        val frontText = request.frontText.trim()
        val backText = request.backText?.trim()

        if (!validateCard(deck, frontText, backText)) {
            return ResponseEntity.badRequest().build()
        }

        val updated = cardRepository.update(deckId, cardId, frontText, backText)
        if (!updated) {
            return ResponseEntity.notFound().build()
        }
        val card = cardRepository.findById(deckId, cardId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(card)
    }

    @DeleteMapping("/{cardId}")
    fun deleteCard(@PathVariable deckId: UUID, @PathVariable cardId: UUID): ResponseEntity<Void> {
        deckRepository.findById(deckId) ?: return ResponseEntity.notFound().build()
        val deleted = cardRepository.delete(deckId, cardId)
        if (!deleted) {
            return ResponseEntity.notFound().build()
        }
        return ResponseEntity.noContent().build()
    }

    /**
     * Validates card content based on deck type:
     * - STUDY: frontText and backText are required (1-500 chars, not blank)
     * - FLASH_REVIEW: frontText required (1-500 chars), backText optional (0-500 chars if provided)
     */
    private fun validateCard(deck: Deck, frontText: String, backText: String?): Boolean {
        // frontText is always required
        if (frontText.isBlank() || frontText.length > 500) {
            return false
        }

        // backText validation depends on deck type
        return when (deck.type) {
            DeckType.STUDY -> {
                // backText is required for study decks
                !backText.isNullOrBlank() && backText.length <= 500
            }
            DeckType.FLASH_REVIEW -> {
                // backText is optional for flash review decks, but if provided must not exceed length limit
                backText == null || backText.length <= 500
            }
        }
    }
}
