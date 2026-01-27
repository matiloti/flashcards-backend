package com.flashcards.offline

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Repository for offline mode operations.
 */
@Repository
class OfflineRepository(private val jdbcTemplate: JdbcTemplate) {

    /**
     * Get card progress data for all cards in a deck.
     * Returns progress for cards that have been reviewed at least once.
     */
    fun getCardProgressForDeck(deckId: UUID): Map<UUID, CardProgressDto> {
        val results = mutableMapOf<UUID, CardProgressDto>()

        jdbcTemplate.query(
            """
            SELECT
                cp.card_id,
                cp.last_reviewed_at,
                cp.last_rating,
                cp.total_reviews
            FROM card_progress cp
            JOIN cards c ON cp.card_id = c.id
            WHERE c.deck_id = ?
            """.trimIndent(),
            { rs, _ ->
                val cardId = UUID.fromString(rs.getString("card_id"))
                val progress = CardProgressDto(
                    lastReviewedAt = rs.getTimestamp("last_reviewed_at")?.toInstant(),
                    lastRating = rs.getString("last_rating"),
                    totalReviews = rs.getInt("total_reviews")
                )
                results[cardId] = progress
            },
            deckId
        )

        return results
    }

    /**
     * Create a study session with custom timestamps (for syncing offline sessions).
     */
    fun createSessionWithTimestamps(
        deckId: UUID,
        sessionType: String,
        startedAt: Instant,
        completedAt: Instant
    ): UUID {
        val id = UUID.randomUUID()
        jdbcTemplate.update(
            """INSERT INTO study_sessions (id, deck_id, session_type, started_at, completed_at)
               VALUES (?, ?, ?, ?, ?)""",
            id, deckId, sessionType,
            Timestamp.from(startedAt), Timestamp.from(completedAt)
        )
        return id
    }

    /**
     * Create a card review with custom timestamp (for syncing offline reviews).
     * Returns true if the review was created, false if the card doesn't exist.
     */
    fun createReviewWithTimestamp(
        sessionId: UUID,
        cardId: UUID,
        rating: String,
        reviewedAt: Instant
    ): Boolean {
        // Check if card exists first
        val cardExists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM cards WHERE id = ?",
            Int::class.java,
            cardId
        ) ?: 0

        if (cardExists == 0) {
            return false
        }

        val id = UUID.randomUUID()
        jdbcTemplate.update(
            """INSERT INTO card_reviews (id, session_id, card_id, rating, reviewed_at)
               VALUES (?, ?, ?, ?, ?)""",
            id, sessionId, cardId, rating, Timestamp.from(reviewedAt)
        )
        return true
    }

    /**
     * Check if a card belongs to a specific deck.
     */
    fun cardBelongsToDeck(cardId: UUID, deckId: UUID): Boolean {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM cards WHERE id = ? AND deck_id = ?",
            Int::class.java,
            cardId, deckId
        ) ?: 0
        return count > 0
    }

    /**
     * Get deck description for download (separate query since Deck model doesn't include it).
     */
    fun getDeckDescription(deckId: UUID): String? {
        return try {
            jdbcTemplate.queryForObject(
                "SELECT description FROM decks WHERE id = ?",
                String::class.java,
                deckId
            )
        } catch (e: Exception) {
            null
        }
    }
}
