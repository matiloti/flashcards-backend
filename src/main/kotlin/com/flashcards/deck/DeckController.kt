package com.flashcards.deck

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/decks")
class DeckController(private val repository: DeckRepository) {

    @GetMapping
    fun listDecks(): ResponseEntity<List<Deck>> {
        return ResponseEntity.ok(repository.findAll())
    }

    /**
     * Get recently studied decks for the "Continue Studying" section.
     * Returns decks ordered by lastStudiedAt DESC, excluding decks that have never been studied.
     *
     * @param limit Maximum number of decks to return (1-10, default 3)
     * @return List of RecentDeck DTOs (without createdAt/updatedAt)
     */
    @GetMapping("/recent")
    fun listRecentDecks(
        @RequestParam(defaultValue = "3") limit: Int
    ): ResponseEntity<Any> {
        if (limit < 1 || limit > 10) {
            return ResponseEntity.badRequest().body(
                mapOf(
                    "status" to 400,
                    "error" to "Bad Request",
                    "message" to "limit must be between 1 and 10"
                )
            )
        }
        return ResponseEntity.ok(repository.findRecentlyStudied(limit))
    }

    @GetMapping("/{deckId}")
    fun getDeck(@PathVariable deckId: java.util.UUID): ResponseEntity<Deck> {
        val deck = repository.findById(deckId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(deck)
    }

    @PostMapping
    fun createDeck(@RequestBody request: CreateDeckRequest): ResponseEntity<Deck> {
        val name = request.name.trim()
        if (name.isBlank() || name.length > 100) {
            return ResponseEntity.badRequest().build()
        }
        val type = request.type ?: DeckType.STUDY
        val deck = repository.create(name, type)
        return ResponseEntity.status(HttpStatus.CREATED).body(deck)
    }

    @PutMapping("/{deckId}")
    fun updateDeck(
        @PathVariable deckId: java.util.UUID,
        @RequestBody request: UpdateDeckRequest
    ): ResponseEntity<Deck> {
        val name = request.name.trim()
        if (name.isBlank() || name.length > 100) {
            return ResponseEntity.badRequest().build()
        }
        val updated = repository.update(deckId, name)
        if (!updated) {
            return ResponseEntity.notFound().build()
        }
        val deck = repository.findById(deckId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(deck)
    }

    @DeleteMapping("/{deckId}")
    fun deleteDeck(@PathVariable deckId: java.util.UUID): ResponseEntity<Void> {
        val deleted = repository.delete(deckId)
        if (!deleted) {
            return ResponseEntity.notFound().build()
        }
        return ResponseEntity.noContent().build()
    }
}
