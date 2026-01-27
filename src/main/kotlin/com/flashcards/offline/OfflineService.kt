package com.flashcards.offline

import com.flashcards.card.Card
import com.flashcards.card.CardRepository
import com.flashcards.deck.Deck
import com.flashcards.deck.DeckRepository
import com.flashcards.statistics.StatisticsService
import com.flashcards.study.Rating
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Service for offline mode operations.
 */
@Service
class OfflineService(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val offlineRepository: OfflineRepository,
    private val statisticsService: StatisticsService
) {

    companion object {
        const val MAX_SESSIONS_PER_REQUEST = 50
        const val MAX_SESSION_AGE_DAYS = 30L
    }

    // In-memory cache for tracking synced sessions (for idempotency)
    // Key: "$clientId:$clientSessionId", Value: serverSessionId
    private val syncedSessions: Cache<String, UUID> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofDays(7))
        .maximumSize(100_000)
        .build()

    /**
     * Get a deck with all cards and progress data for offline download.
     */
    fun getDeckForDownload(userId: UUID, deckId: UUID): DeckDownloadResponse? {
        val deck = deckRepository.findById(deckId, userId) ?: return null
        val cards = cardRepository.findByDeckId(deckId)
        val progress = offlineRepository.getCardProgressForDeck(deckId)
        val description = offlineRepository.getDeckDescription(deckId)

        return DeckDownloadResponse(
            deck = deck.toDownloadDto(description),
            cards = cards.map { it.toDownloadDto() },
            progress = progress,
            downloadedAt = Instant.now()
        )
    }

    /**
     * Sync offline study sessions to the server.
     * Handles idempotency, validation, and partial success.
     */
    @Transactional
    fun syncStudyProgress(userId: UUID, request: SyncStudyProgressRequest): SyncStudyProgressResponse {
        val results = mutableListOf<SessionSyncResult>()

        for (session in request.sessions) {
            try {
                val result = syncSingleSession(userId, request.clientId, session)
                results.add(result)
            } catch (e: Exception) {
                results.add(
                    SessionSyncResult(
                        clientSessionId = session.clientSessionId,
                        status = SyncStatus.FAILED,
                        error = e.message ?: "Unknown error"
                    )
                )
            }
        }

        return SyncStudyProgressResponse(
            syncedAt = Instant.now(),
            results = results,
            summary = SyncSummary(
                total = results.size,
                synced = results.count { it.status == SyncStatus.SYNCED },
                skipped = results.count { it.status == SyncStatus.SKIPPED },
                failed = results.count { it.status == SyncStatus.FAILED }
            )
        )
    }

    /**
     * Sync a single offline session.
     */
    private fun syncSingleSession(
        userId: UUID,
        clientId: String,
        session: OfflineStudySession
    ): SessionSyncResult {
        val cacheKey = "$clientId:${session.clientSessionId}"

        // Check for duplicate (idempotency)
        val existingServerSessionId = syncedSessions.getIfPresent(cacheKey)
        if (existingServerSessionId != null) {
            return SessionSyncResult(
                clientSessionId = session.clientSessionId,
                status = SyncStatus.SKIPPED,
                serverSessionId = existingServerSessionId
            )
        }

        // Validate timestamps
        val now = Instant.now()
        val maxAge = now.minus(MAX_SESSION_AGE_DAYS, ChronoUnit.DAYS)

        if (session.startedAt.isAfter(now) || session.completedAt.isAfter(now)) {
            return SessionSyncResult(
                clientSessionId = session.clientSessionId,
                status = SyncStatus.FAILED,
                error = "Timestamps cannot be in the future"
            )
        }

        if (session.startedAt.isBefore(maxAge)) {
            return SessionSyncResult(
                clientSessionId = session.clientSessionId,
                status = SyncStatus.FAILED,
                error = "Session is older than 30 days"
            )
        }

        // Validate deck ownership
        val deck = deckRepository.findById(session.deckId, userId)
        if (deck == null) {
            return SessionSyncResult(
                clientSessionId = session.clientSessionId,
                status = SyncStatus.FAILED,
                error = "Deck not found or not owned"
            )
        }

        // Validate session type
        val sessionType = try {
            session.sessionType.uppercase()
        } catch (e: Exception) {
            return SessionSyncResult(
                clientSessionId = session.clientSessionId,
                status = SyncStatus.FAILED,
                error = "Invalid session type: ${session.sessionType}"
            )
        }

        // Validate and filter reviews
        val validReviews = session.reviews.filter { review ->
            try {
                Rating.valueOf(review.rating.uppercase())
                true
            } catch (e: Exception) {
                false
            }
        }

        if (session.reviews.isNotEmpty() && validReviews.isEmpty()) {
            return SessionSyncResult(
                clientSessionId = session.clientSessionId,
                status = SyncStatus.FAILED,
                error = "All reviews have invalid ratings"
            )
        }

        // Create the study session
        val serverSessionId = offlineRepository.createSessionWithTimestamps(
            deckId = session.deckId,
            sessionType = sessionType,
            startedAt = session.startedAt,
            completedAt = session.completedAt
        )

        // Create reviews
        var reviewsSynced = 0
        var easyCount = 0
        var hardCount = 0
        var againCount = 0

        for (review in validReviews) {
            val rating = try {
                Rating.valueOf(review.rating.uppercase())
            } catch (e: Exception) {
                continue
            }

            val created = offlineRepository.createReviewWithTimestamp(
                sessionId = serverSessionId,
                cardId = review.cardId,
                rating = rating.name,
                reviewedAt = review.reviewedAt
            )

            if (created) {
                reviewsSynced++
                when (rating) {
                    Rating.EASY -> easyCount++
                    Rating.HARD -> hardCount++
                    Rating.AGAIN -> againCount++
                }

                // Update card progress for statistics
                statisticsService.recordCardReview(
                    cardId = review.cardId,
                    rating = rating.name,
                    reviewedAt = review.reviewedAt
                )
            }
        }

        // Update deck's lastStudiedAt
        deckRepository.updateLastStudiedAt(session.deckId, session.completedAt)

        // Update statistics
        val durationMinutes = ChronoUnit.MINUTES.between(session.startedAt, session.completedAt).toInt()
            .coerceAtLeast(1)

        statisticsService.recordSessionCompletion(
            userId = userId,
            cardsStudied = reviewsSynced,
            easyCount = easyCount,
            hardCount = hardCount,
            againCount = againCount,
            sessionDurationMinutes = durationMinutes,
            zoneId = ZoneId.of("UTC")
        )

        // Mark session as synced in cache
        syncedSessions.put(cacheKey, serverSessionId)

        return SessionSyncResult(
            clientSessionId = session.clientSessionId,
            status = SyncStatus.SYNCED,
            serverSessionId = serverSessionId,
            reviewsSynced = reviewsSynced
        )
    }

    /**
     * Check if a session has already been synced (for testing).
     */
    fun isSessionSynced(clientId: String, clientSessionId: String): Boolean {
        return syncedSessions.getIfPresent("$clientId:$clientSessionId") != null
    }

    /**
     * Clear the sync cache (for testing).
     */
    fun clearSyncCache() {
        syncedSessions.invalidateAll()
    }

    // Extension functions to convert domain objects to DTOs

    private fun Deck.toDownloadDto(description: String? = null): DeckDownloadDto {
        return DeckDownloadDto(
            id = this.id,
            name = this.name,
            description = description,
            type = this.type.name,
            cardCount = this.cardCount,
            lastStudiedAt = this.lastStudiedAt,
            updatedAt = this.updatedAt,
            version = this.updatedAt.toString()
        )
    }

    private fun Card.toDownloadDto(): CardDownloadDto {
        return CardDownloadDto(
            id = this.id,
            frontText = this.frontText,
            backText = this.backText,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }
}
