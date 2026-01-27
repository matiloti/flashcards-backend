package com.flashcards.deck

import com.flashcards.common.Page
import com.flashcards.security.JwtAuthentication
import com.flashcards.tag.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/decks")
class DeckController(
    private val repository: DeckRepository,
    private val deckService: DeckService
) {

    /**
     * Get the current authenticated user's ID from the SecurityContext.
     */
    private fun getCurrentUserId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication as JwtAuthentication
        return authentication.userId
    }

    @GetMapping
    fun listDecks(
        @RequestParam(required = false) tagId: UUID?,
        @RequestParam(required = false) untagged: Boolean?
    ): ResponseEntity<Any> {
        // Validate mutually exclusive filters
        if (tagId != null && untagged == true) {
            return ResponseEntity.badRequest().body(
                mapOf(
                    "error" to "CONFLICTING_FILTERS",
                    "message" to "Cannot specify both tagId and untagged filters"
                )
            )
        }

        val userId = getCurrentUserId()
        val decks = deckService.getAllDecks(userId, tagId, untagged)
        return ResponseEntity.ok(decks)
    }

    /**
     * Search for decks by name and description.
     *
     * @param q Search query (min 2 characters, max 100 characters)
     * @param page Page number (0-indexed, default 0)
     * @param size Page size (1-50, default 20)
     * @return Paginated search results sorted by relevance
     */
    @GetMapping("/search")
    fun searchDecks(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Any> {
        // Validate query parameter
        val trimmedQuery = q?.trim() ?: ""
        if (trimmedQuery.isBlank()) {
            return ResponseEntity.badRequest().body(
                mapOf(
                    "error" to "MISSING_QUERY",
                    "message" to "Search query parameter 'q' is required"
                )
            )
        }
        if (trimmedQuery.length < 2) {
            return ResponseEntity.badRequest().body(
                mapOf(
                    "error" to "QUERY_TOO_SHORT",
                    "message" to "Search query must be at least 2 characters"
                )
            )
        }
        if (trimmedQuery.length > 100) {
            return ResponseEntity.badRequest().body(
                mapOf(
                    "error" to "QUERY_TOO_LONG",
                    "message" to "Search query must not exceed 100 characters"
                )
            )
        }

        // Validate pagination parameters
        if (page < 0 || size < 1 || size > 50) {
            return ResponseEntity.badRequest().body(
                mapOf(
                    "error" to "INVALID_PAGINATION",
                    "message" to "Page must be >= 0, size must be 1-50"
                )
            )
        }

        val userId = getCurrentUserId()
        val results = repository.search(userId, trimmedQuery, page, size)
        return ResponseEntity.ok(results)
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
        val userId = getCurrentUserId()
        return ResponseEntity.ok(repository.findRecentlyStudied(userId, limit))
    }

    @GetMapping("/{deckId}")
    fun getDeck(@PathVariable deckId: UUID): ResponseEntity<Any> {
        val userId = getCurrentUserId()
        val deck = deckService.getDeckById(userId, deckId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(deck)
    }

    @PostMapping
    fun createDeck(@RequestBody request: CreateDeckRequest): ResponseEntity<Any> {
        val name = request.name.trim()
        if (name.isBlank() || name.length > 100) {
            return ResponseEntity.badRequest().build()
        }

        val userId = getCurrentUserId()
        return try {
            val deck = deckService.createDeck(userId, request)
            ResponseEntity.status(HttpStatus.CREATED).body(deck)
        } catch (e: TooManyTagsException) {
            ResponseEntity.badRequest().body(
                mapOf(
                    "error" to "TOO_MANY_TAGS",
                    "message" to e.message,
                    "field" to "tagIds",
                    "max" to e.max,
                    "provided" to e.provided
                )
            )
        } catch (e: InvalidTagIdsException) {
            ResponseEntity.badRequest().body(
                mapOf(
                    "error" to "INVALID_TAG_IDS",
                    "message" to "One or more tag IDs are invalid",
                    "invalidIds" to e.invalidIds.map { it.toString() }
                )
            )
        }
    }

    @PutMapping("/{deckId}")
    fun updateDeck(
        @PathVariable deckId: UUID,
        @RequestBody request: UpdateDeckRequest
    ): ResponseEntity<Any> {
        val name = request.name.trim()
        if (name.isBlank() || name.length > 100) {
            return ResponseEntity.badRequest().build()
        }
        val userId = getCurrentUserId()
        val updated = repository.update(deckId, name, userId)
        if (!updated) {
            return ResponseEntity.notFound().build()
        }
        val deck = deckService.getDeckById(userId, deckId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(deck)
    }

    @DeleteMapping("/{deckId}")
    fun deleteDeck(@PathVariable deckId: UUID): ResponseEntity<Void> {
        val userId = getCurrentUserId()
        val deleted = repository.delete(deckId, userId)
        if (!deleted) {
            return ResponseEntity.notFound().build()
        }
        return ResponseEntity.noContent().build()
    }

    /**
     * PATCH /api/v1/decks/{deckId}/tags
     * Update the tags assigned to a deck.
     */
    @PatchMapping("/{deckId}/tags")
    fun updateDeckTags(
        @PathVariable deckId: UUID,
        @RequestBody request: UpdateDeckTagsRequest
    ): ResponseEntity<Any> {
        val userId = getCurrentUserId()
        return try {
            val deck = deckService.updateDeckTags(userId, deckId, request.tagIds)
            ResponseEntity.ok(deck)
        } catch (e: DeckNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (e: TooManyTagsException) {
            ResponseEntity.badRequest().body(
                mapOf(
                    "error" to "TOO_MANY_TAGS",
                    "message" to e.message,
                    "field" to "tagIds",
                    "max" to e.max,
                    "provided" to e.provided
                )
            )
        } catch (e: InvalidTagIdsException) {
            ResponseEntity.badRequest().body(
                mapOf(
                    "error" to "INVALID_TAG_IDS",
                    "message" to "One or more tag IDs are invalid",
                    "invalidIds" to e.invalidIds.map { it.toString() }
                )
            )
        }
    }
}
