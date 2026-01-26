package com.flashcards.card

import com.flashcards.deck.DeckRepository
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
        deckRepository.findById(deckId) ?: return ResponseEntity.notFound().build()

        val frontText = request.frontText.trim()
        val backText = request.backText.trim()

        if (frontText.isBlank() || frontText.length > 500 || backText.length > 500) {
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
        deckRepository.findById(deckId) ?: return ResponseEntity.notFound().build()

        val frontText = request.frontText.trim()
        val backText = request.backText.trim()

        if (frontText.isBlank() || frontText.length > 500 || backText.length > 500) {
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
}
