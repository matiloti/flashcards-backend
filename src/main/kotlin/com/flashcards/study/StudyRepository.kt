package com.flashcards.study

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class StudyRepository(private val jdbcTemplate: JdbcTemplate) {

    fun createSession(
        deckId: UUID,
        sessionType: SessionType = SessionType.STUDY,
        parentSessionId: UUID? = null,
        retakeType: RetakeType? = null
    ): StudySession {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcTemplate.update(
            """INSERT INTO study_sessions (id, deck_id, session_type, started_at, parent_session_id, retake_type)
               VALUES (?, ?, ?, ?, ?, ?)""",
            id, deckId, sessionType.name, Timestamp.from(now), parentSessionId, retakeType?.name
        )
        return StudySession(
            id = id,
            deckId = deckId,
            sessionType = sessionType,
            startedAt = now,
            parentSessionId = parentSessionId,
            retakeType = retakeType
        )
    }

    fun findSessionById(sessionId: UUID): StudySession? {
        val results = jdbcTemplate.query(
            """SELECT id, deck_id, session_type, started_at, completed_at, concepts_viewed, parent_session_id, retake_type
               FROM study_sessions WHERE id = ?""",
            { rs, _ ->
                StudySession(
                    id = UUID.fromString(rs.getString("id")),
                    deckId = UUID.fromString(rs.getString("deck_id")),
                    sessionType = SessionType.valueOf(rs.getString("session_type")),
                    startedAt = rs.getTimestamp("started_at").toInstant(),
                    completedAt = rs.getTimestamp("completed_at")?.toInstant(),
                    conceptsViewed = rs.getObject("concepts_viewed") as? Int,
                    parentSessionId = rs.getString("parent_session_id")?.let { UUID.fromString(it) },
                    retakeType = rs.getString("retake_type")?.let { RetakeType.valueOf(it) }
                )
            },
            sessionId
        )
        return results.firstOrNull()
    }

    fun createReview(sessionId: UUID, cardId: UUID, rating: Rating): CardReview {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcTemplate.update(
            "INSERT INTO card_reviews (id, session_id, card_id, rating, reviewed_at) VALUES (?, ?, ?, ?, ?)",
            id, sessionId, cardId, rating.name, Timestamp.from(now)
        )
        return CardReview(id = id, sessionId = sessionId, cardId = cardId, rating = rating, reviewedAt = now)
    }

    fun completeSession(sessionId: UUID): Instant {
        val now = Instant.now()
        jdbcTemplate.update(
            "UPDATE study_sessions SET completed_at = ? WHERE id = ?",
            Timestamp.from(now), sessionId
        )
        return now
    }

    fun completeFlashReviewSession(sessionId: UUID, conceptsViewed: Int?): Instant {
        val now = Instant.now()
        jdbcTemplate.update(
            "UPDATE study_sessions SET completed_at = ?, concepts_viewed = ? WHERE id = ?",
            Timestamp.from(now), conceptsViewed, sessionId
        )
        return now
    }

    fun getReviewCounts(sessionId: UUID): Map<Rating, Int> {
        val counts = mutableMapOf(Rating.EASY to 0, Rating.HARD to 0, Rating.AGAIN to 0)
        jdbcTemplate.query(
            "SELECT rating, COUNT(*) as cnt FROM card_reviews WHERE session_id = ? GROUP BY rating",
            { rs, _ ->
                val rating = Rating.valueOf(rs.getString("rating"))
                val count = rs.getInt("cnt")
                counts[rating] = count
            },
            sessionId
        )
        return counts
    }

    /**
     * Find all missed cards (HARD or AGAIN) from a session that still exist in the deck.
     * Returns cards with their front/back text for study, in random order.
     */
    fun findMissedCardsBySessionId(sessionId: UUID): List<StudyCard> {
        // Use a subquery for DISTINCT, then ORDER BY RANDOM() on the outer query
        // This avoids PostgreSQL error: "for SELECT DISTINCT, ORDER BY expressions must appear in select list"
        return jdbcTemplate.query(
            """SELECT id, front_text, back_text FROM (
                   SELECT DISTINCT c.id, c.front_text, c.back_text
                   FROM card_reviews cr
                   JOIN cards c ON cr.card_id = c.id
                   WHERE cr.session_id = ?
                     AND cr.rating IN ('HARD', 'AGAIN')
               ) AS missed_cards
               ORDER BY RANDOM()""",
            { rs, _ ->
                StudyCard(
                    id = UUID.fromString(rs.getString("id")),
                    frontText = rs.getString("front_text"),
                    backText = rs.getString("back_text") ?: ""
                )
            },
            sessionId
        )
    }

    /**
     * Count the number of unique missed cards (HARD or AGAIN) in a session.
     * Only counts cards that still exist in the deck.
     * Note: Due to ON DELETE CASCADE on card_reviews.card_id, if cards are deleted,
     * their reviews are also deleted, so this count will be 0.
     */
    fun countMissedCards(sessionId: UUID): Int {
        return jdbcTemplate.queryForObject(
            """SELECT COUNT(DISTINCT c.id)
               FROM card_reviews cr
               JOIN cards c ON cr.card_id = c.id
               WHERE cr.session_id = ?
                 AND cr.rating IN ('HARD', 'AGAIN')""",
            Int::class.java,
            sessionId
        ) ?: 0
    }

    /**
     * Count the total number of reviews in a session.
     * This represents the original session's card count.
     */
    fun countTotalReviews(sessionId: UUID): Int {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT card_id) FROM card_reviews WHERE session_id = ?",
            Int::class.java,
            sessionId
        ) ?: 0
    }
}
