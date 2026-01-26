package com.flashcards.card

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class CardRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        Card(
            id = UUID.fromString(rs.getString("id")),
            deckId = UUID.fromString(rs.getString("deck_id")),
            frontText = rs.getString("front_text"),
            backText = rs.getString("back_text"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }

    fun findByDeckId(deckId: UUID): List<Card> {
        return jdbcTemplate.query(
            "SELECT * FROM cards WHERE deck_id = ? ORDER BY created_at ASC",
            rowMapper,
            deckId
        )
    }

    fun findById(deckId: UUID, cardId: UUID): Card? {
        val results = jdbcTemplate.query(
            "SELECT * FROM cards WHERE id = ? AND deck_id = ?",
            rowMapper,
            cardId, deckId
        )
        return results.firstOrNull()
    }

    fun create(deckId: UUID, frontText: String, backText: String): Card {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcTemplate.update(
            "INSERT INTO cards (id, deck_id, front_text, back_text, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
            id, deckId, frontText, backText, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now)
        )
        return Card(id = id, deckId = deckId, frontText = frontText, backText = backText, createdAt = now, updatedAt = now)
    }

    fun update(deckId: UUID, cardId: UUID, frontText: String, backText: String): Boolean {
        val updated = jdbcTemplate.update(
            "UPDATE cards SET front_text = ?, back_text = ?, updated_at = NOW() WHERE id = ? AND deck_id = ?",
            frontText, backText, cardId, deckId
        )
        return updated > 0
    }

    fun delete(deckId: UUID, cardId: UUID): Boolean {
        val deleted = jdbcTemplate.update(
            "DELETE FROM cards WHERE id = ? AND deck_id = ?",
            cardId, deckId
        )
        return deleted > 0
    }
}
