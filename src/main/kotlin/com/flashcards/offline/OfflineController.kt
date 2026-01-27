package com.flashcards.offline

import com.flashcards.security.JwtAuthentication
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * Controller for offline mode API endpoints.
 */
@RestController
class OfflineController(
    private val offlineService: OfflineService
) {

    /**
     * Get the current authenticated user's ID from the SecurityContext.
     */
    private fun getCurrentUserId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication as JwtAuthentication
        return authentication.userId
    }

    /**
     * Download a complete deck with all cards and progress for offline storage.
     *
     * GET /api/v1/decks/{deckId}/download
     *
     * Returns:
     * - 200 OK with DeckDownloadResponse
     * - 404 Not Found if deck doesn't exist or doesn't belong to user
     */
    @GetMapping("/api/v1/decks/{deckId}/download")
    fun downloadDeck(@PathVariable deckId: UUID): ResponseEntity<DeckDownloadResponse> {
        val userId = getCurrentUserId()
        val response = offlineService.getDeckForDownload(userId, deckId)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(response)
    }

    /**
     * Sync offline study progress to the server.
     *
     * POST /api/v1/sync/study-progress
     *
     * Supports:
     * - Batch processing (up to 50 sessions per request)
     * - Idempotency (duplicate sessions are skipped)
     * - Partial success (individual session failures don't fail entire request)
     *
     * Returns:
     * - 200 OK with SyncStudyProgressResponse (even for partial failures)
     * - 400 Bad Request for validation errors (empty sessions, too many sessions)
     */
    @PostMapping("/api/v1/sync/study-progress")
    fun syncStudyProgress(
        @RequestBody request: SyncStudyProgressRequest
    ): ResponseEntity<Any> {
        // Validate request
        if (request.sessions.isEmpty()) {
            return ResponseEntity.badRequest().body(
                SyncErrorResponse(
                    error = "No sessions provided",
                    code = "EMPTY_SESSIONS"
                )
            )
        }

        if (request.sessions.size > OfflineService.MAX_SESSIONS_PER_REQUEST) {
            return ResponseEntity.badRequest().body(
                SyncErrorResponse(
                    error = "Maximum ${OfflineService.MAX_SESSIONS_PER_REQUEST} sessions per request",
                    code = "TOO_MANY_SESSIONS"
                )
            )
        }

        val userId = getCurrentUserId()
        val response = offlineService.syncStudyProgress(userId, request)

        return ResponseEntity.ok(response)
    }
}
