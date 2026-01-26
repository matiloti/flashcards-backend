package com.flashcards.study

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class StudyRepository(private val jdbcTemplate: JdbcTemplate) {

    fun createSession(deckId: UUID, sessionType: SessionType = SessionType.STUDY): StudySession {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcTemplate.update(
            "INSERT INTO study_sessions (id, deck_id, session_type, started_at) VALUES (?, ?, ?, ?)",
            id, deckId, sessionType.name, Timestamp.from(now)
        )
        return StudySession(id = id, deckId = deckId, sessionType = sessionType, startedAt = now)
    }

    fun findSessionById(sessionId: UUID): StudySession? {
        val results = jdbcTemplate.query(
            "SELECT id, deck_id, session_type, started_at, completed_at, concepts_viewed FROM study_sessions WHERE id = ?",
            { rs, _ ->
                StudySession(
                    id = UUID.fromString(rs.getString("id")),
                    deckId = UUID.fromString(rs.getString("deck_id")),
                    sessionType = SessionType.valueOf(rs.getString("session_type")),
                    startedAt = rs.getTimestamp("started_at").toInstant(),
                    completedAt = rs.getTimestamp("completed_at")?.toInstant(),
                    conceptsViewed = rs.getObject("concepts_viewed") as? Int
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
}
