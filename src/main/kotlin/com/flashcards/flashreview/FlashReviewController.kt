package com.flashcards.flashreview

import com.flashcards.deck.DeckType
import com.flashcards.security.JwtAuthentication
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class ErrorResponse(val error: String)

@RestController
@RequestMapping("/api/v1")
class FlashReviewController(private val service: FlashReviewService) {

    /**
     * Get the current authenticated user's ID from the SecurityContext.
     */
    private fun getCurrentUserId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication as JwtAuthentication
        return authentication.userId
    }

    @PostMapping("/decks/{deckId}/flash-review")
    fun startFlashReview(
        @PathVariable deckId: UUID,
        @RequestParam(defaultValue = "true") shuffle: Boolean
    ): ResponseEntity<*> {
        val userId = getCurrentUserId()
        return when (val result = service.startSession(userId, deckId, shuffle)) {
            is FlashReviewService.StartSessionResult.Success ->
                ResponseEntity.status(HttpStatus.CREATED).body(result.response)
            is FlashReviewService.StartSessionResult.DeckNotFound ->
                ResponseEntity.notFound().build<Any>()
            is FlashReviewService.StartSessionResult.InvalidDeckType ->
                ResponseEntity.badRequest().body(
                    ErrorResponse("Cannot start flash review: deck type is ${result.actualType}. Use /study endpoint instead.")
                )
            is FlashReviewService.StartSessionResult.EmptyDeck ->
                ResponseEntity.badRequest().body(
                    ErrorResponse("Cannot start review: deck has no cards")
                )
        }
    }

    @PostMapping("/flash-review/{sessionId}/complete")
    fun completeFlashReview(
        @PathVariable sessionId: UUID,
        @RequestBody(required = false) request: CompleteFlashReviewRequest?
    ): ResponseEntity<*> {
        val userId = getCurrentUserId()
        return when (val result = service.completeSession(userId, sessionId, request?.conceptsViewed)) {
            is FlashReviewService.CompleteSessionResult.Success ->
                ResponseEntity.ok(result.summary)
            is FlashReviewService.CompleteSessionResult.SessionNotFound ->
                ResponseEntity.notFound().build<Any>()
            is FlashReviewService.CompleteSessionResult.AlreadyCompleted ->
                ResponseEntity.badRequest().body(
                    ErrorResponse("Session already completed")
                )
        }
    }

    @GetMapping("/flash-review/{sessionId}")
    fun getFlashReviewSession(@PathVariable sessionId: UUID): ResponseEntity<*> {
        val userId = getCurrentUserId()
        return when (val result = service.getSession(userId, sessionId)) {
            is FlashReviewService.GetSessionResult.Success ->
                ResponseEntity.ok(result.status)
            is FlashReviewService.GetSessionResult.SessionNotFound ->
                ResponseEntity.notFound().build<Any>()
        }
    }
}
