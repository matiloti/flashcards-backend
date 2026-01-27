package com.flashcards.offline

import java.time.Instant
import java.util.UUID

// =============================================================================
// Download DTOs
// =============================================================================

/**
 * Response containing full deck data for offline storage.
 */
data class DeckDownloadResponse(
    val deck: DeckDownloadDto,
    val cards: List<CardDownloadDto>,
    val progress: Map<UUID, CardProgressDto>,
    val downloadedAt: Instant
)

/**
 * Deck metadata for download.
 */
data class DeckDownloadDto(
    val id: UUID,
    val name: String,
    val description: String?,
    val type: String,
    val cardCount: Int,
    val lastStudiedAt: Instant?,
    val updatedAt: Instant,
    val version: String
)

/**
 * Card data for download.
 */
data class CardDownloadDto(
    val id: UUID,
    val frontText: String,
    val backText: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Card progress data for download.
 */
data class CardProgressDto(
    val lastReviewedAt: Instant?,
    val lastRating: String?,
    val totalReviews: Int
)

// =============================================================================
// Sync Request DTOs
// =============================================================================

/**
 * Request to sync offline study sessions.
 */
data class SyncStudyProgressRequest(
    val clientId: String,
    val sessions: List<OfflineStudySession>
)

/**
 * Offline study session to sync.
 */
data class OfflineStudySession(
    val clientSessionId: String,
    val deckId: UUID,
    val sessionType: String,
    val startedAt: Instant,
    val completedAt: Instant,
    val reviews: List<OfflineCardReview>
)

/**
 * Card review from offline session.
 */
data class OfflineCardReview(
    val cardId: UUID,
    val rating: String,
    val reviewedAt: Instant
)

// =============================================================================
// Sync Response DTOs
// =============================================================================

/**
 * Response from sync operation.
 */
data class SyncStudyProgressResponse(
    val syncedAt: Instant,
    val results: List<SessionSyncResult>,
    val summary: SyncSummary
)

/**
 * Result for a single synced session.
 */
data class SessionSyncResult(
    val clientSessionId: String,
    val status: SyncStatus,
    val serverSessionId: UUID? = null,
    val reviewsSynced: Int = 0,
    val error: String? = null
)

/**
 * Status of a sync operation for a session.
 */
enum class SyncStatus {
    SYNCED,
    SKIPPED,
    FAILED
}

/**
 * Summary of sync operation.
 */
data class SyncSummary(
    val total: Int,
    val synced: Int,
    val skipped: Int,
    val failed: Int
)

// =============================================================================
// Error DTOs
// =============================================================================

/**
 * Error response for sync validation errors.
 */
data class SyncErrorResponse(
    val error: String,
    val code: String
)
