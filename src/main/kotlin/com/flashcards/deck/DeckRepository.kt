package com.flashcards.deck

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class DeckRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        Deck(
            id = UUID.fromString(rs.getString("id")),
            name = rs.getString("name"),
            type = DeckType.valueOf(rs.getString("deck_type")),
            cardCount = rs.getInt("card_count"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }

    fun findAll(): List<Deck> {
        return jdbcTemplate.query(
            """
            SELECT d.id, d.name, d.deck_type, d.created_at, d.updated_at,
                   COUNT(c.id) AS card_count
            FROM decks d
            LEFT JOIN cards c ON c.deck_id = d.id
            GROUP BY d.id, d.name, d.deck_type, d.created_at, d.updated_at
            ORDER BY d.updated_at DESC
            """.trimIndent(),
            rowMapper
        )
    }

    fun findById(id: UUID): Deck? {
        val results = jdbcTemplate.query(
            """
            SELECT d.id, d.name, d.deck_type, d.created_at, d.updated_at,
                   COUNT(c.id) AS card_count
            FROM decks d
            LEFT JOIN cards c ON c.deck_id = d.id
            WHERE d.id = ?
            GROUP BY d.id, d.name, d.deck_type, d.created_at, d.updated_at
            """.trimIndent(),
            rowMapper,
            id
        )
        return results.firstOrNull()
    }

    fun create(name: String, type: DeckType = DeckType.STUDY): Deck {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcTemplate.update(
            "INSERT INTO decks (id, name, deck_type, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
            id, name, type.name, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now)
        )
        return Deck(id = id, name = name, type = type, cardCount = 0, createdAt = now, updatedAt = now)
    }

    fun update(id: UUID, name: String): Boolean {
        val updated = jdbcTemplate.update(
            "UPDATE decks SET name = ?, updated_at = NOW() WHERE id = ?",
            name, id
        )
        return updated > 0
    }

    fun delete(id: UUID): Boolean {
        val deleted = jdbcTemplate.update("DELETE FROM decks WHERE id = ?", id)
        return deleted > 0
    }
}
