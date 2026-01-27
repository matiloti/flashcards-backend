package com.flashcards.deck

import com.flashcards.common.Page
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import kotlin.math.ceil

@Repository
class DeckRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        val lastStudiedAtTimestamp = rs.getTimestamp("last_studied_at")
        Deck(
            id = UUID.fromString(rs.getString("id")),
            name = rs.getString("name"),
            type = DeckType.valueOf(rs.getString("deck_type")),
            cardCount = rs.getInt("card_count"),
            lastStudiedAt = lastStudiedAtTimestamp?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }

    fun findAll(): List<Deck> {
        return jdbcTemplate.query(
            """
            SELECT d.id, d.name, d.deck_type, d.last_studied_at, d.created_at, d.updated_at,
                   COUNT(DISTINCT c.id) AS card_count
            FROM decks d
            LEFT JOIN cards c ON c.deck_id = d.id
            GROUP BY d.id, d.name, d.deck_type, d.last_studied_at, d.created_at, d.updated_at
            ORDER BY d.updated_at DESC
            """.trimIndent(),
            rowMapper
        )
    }

    fun findById(id: UUID): Deck? {
        val results = jdbcTemplate.query(
            """
            SELECT d.id, d.name, d.deck_type, d.last_studied_at, d.created_at, d.updated_at,
                   COUNT(DISTINCT c.id) AS card_count
            FROM decks d
            LEFT JOIN cards c ON c.deck_id = d.id
            WHERE d.id = ?
            GROUP BY d.id, d.name, d.deck_type, d.last_studied_at, d.created_at, d.updated_at
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

    /**
     * Find recently studied decks ordered by lastStudiedAt DESC.
     * Only returns decks that have been studied (lastStudiedAt IS NOT NULL).
     *
     * @param limit Maximum number of decks to return (1-10)
     * @return List of RecentDeck DTOs without createdAt/updatedAt fields
     */
    fun findRecentlyStudied(limit: Int): List<RecentDeck> {
        return jdbcTemplate.query(
            """
            SELECT d.id, d.name, d.deck_type, d.last_studied_at,
                   COUNT(DISTINCT c.id) AS card_count
            FROM decks d
            LEFT JOIN cards c ON c.deck_id = d.id
            WHERE d.last_studied_at IS NOT NULL
            GROUP BY d.id, d.name, d.deck_type, d.last_studied_at
            ORDER BY d.last_studied_at DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                RecentDeck(
                    id = UUID.fromString(rs.getString("id")),
                    name = rs.getString("name"),
                    type = DeckType.valueOf(rs.getString("deck_type")),
                    cardCount = rs.getInt("card_count"),
                    lastStudiedAt = rs.getTimestamp("last_studied_at").toInstant()
                )
            },
            limit
        )
    }

    /**
     * Update the lastStudiedAt timestamp for a deck.
     * Called when a study or flash review session is completed.
     *
     * @param id Deck ID
     * @param lastStudiedAt Timestamp of session completion
     * @return true if deck was updated, false if deck not found
     */
    fun updateLastStudiedAt(id: UUID, lastStudiedAt: Instant): Boolean {
        val updated = jdbcTemplate.update(
            "UPDATE decks SET last_studied_at = ? WHERE id = ?",
            java.sql.Timestamp.from(lastStudiedAt), id
        )
        return updated > 0
    }

    /**
     * Search for decks by name and description using ILIKE pattern matching.
     *
     * Results are ranked with name matches appearing before description-only matches,
     * and alphabetically sorted by name within each group.
     *
     * @param query Search query (min 2 chars, already validated by controller)
     * @param page Page number (0-indexed)
     * @param size Page size (1-50)
     * @return Paginated search results with matchedField indicator
     */
    fun search(query: String, page: Int, size: Int): Page<DeckSearchResponse> {
        val offset = page * size

        val searchSql = """
            SELECT
                d.id,
                d.name,
                d.description,
                d.deck_type,
                d.last_studied_at,
                d.created_at,
                d.updated_at,
                COUNT(c.id) AS card_count,
                CASE
                    WHEN d.name ILIKE '%' || ? || '%' THEN 'name'
                    ELSE 'description'
                END AS matched_field
            FROM decks d
            LEFT JOIN cards c ON d.id = c.deck_id
            WHERE
                d.name ILIKE '%' || ? || '%'
                OR d.description ILIKE '%' || ? || '%'
            GROUP BY d.id
            ORDER BY
                CASE WHEN d.name ILIKE '%' || ? || '%' THEN 0 ELSE 1 END,
                d.name ASC
            LIMIT ? OFFSET ?
        """.trimIndent()

        val countSql = """
            SELECT COUNT(*)
            FROM decks d
            WHERE
                d.name ILIKE '%' || ? || '%'
                OR d.description ILIKE '%' || ? || '%'
        """.trimIndent()

        val results = jdbcTemplate.query(
            searchSql,
            { rs, _ -> mapToDeckSearchResponse(rs) },
            query, query, query, query, size, offset
        )

        val totalElements = jdbcTemplate.queryForObject(countSql, Long::class.java, query, query)
        val totalPages = if (totalElements == 0L) 0 else ceil(totalElements.toDouble() / size).toInt()

        return Page(
            content = results,
            totalElements = totalElements,
            totalPages = totalPages,
            number = page,
            size = size,
            first = page == 0,
            last = page >= totalPages - 1 || totalPages == 0
        )
    }

    private fun mapToDeckSearchResponse(rs: ResultSet): DeckSearchResponse {
        return DeckSearchResponse(
            id = UUID.fromString(rs.getString("id")),
            name = rs.getString("name"),
            description = rs.getString("description"),
            type = DeckType.valueOf(rs.getString("deck_type")),
            cardCount = rs.getInt("card_count"),
            lastStudiedAt = rs.getTimestamp("last_studied_at")?.toInstant(),
            matchedField = rs.getString("matched_field"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }

    /**
     * Find decks filtered by tag ID.
     */
    fun findAllByTagId(tagId: UUID): List<Deck> {
        return jdbcTemplate.query(
            """
            SELECT d.id, d.name, d.deck_type, d.last_studied_at, d.created_at, d.updated_at,
                   COUNT(DISTINCT c.id) AS card_count
            FROM decks d
            INNER JOIN deck_tags dt ON d.id = dt.deck_id AND dt.tag_id = ?
            LEFT JOIN cards c ON c.deck_id = d.id
            GROUP BY d.id, d.name, d.deck_type, d.last_studied_at, d.created_at, d.updated_at
            ORDER BY d.updated_at DESC
            """.trimIndent(),
            rowMapper,
            tagId
        )
    }

    /**
     * Find decks that have no tags (untagged).
     */
    fun findAllUntagged(): List<Deck> {
        return jdbcTemplate.query(
            """
            SELECT d.id, d.name, d.deck_type, d.last_studied_at, d.created_at, d.updated_at,
                   COUNT(DISTINCT c.id) AS card_count
            FROM decks d
            LEFT JOIN deck_tags dt ON d.id = dt.deck_id
            LEFT JOIN cards c ON c.deck_id = d.id
            WHERE dt.deck_id IS NULL
            GROUP BY d.id, d.name, d.deck_type, d.last_studied_at, d.created_at, d.updated_at
            ORDER BY d.updated_at DESC
            """.trimIndent(),
            rowMapper
        )
    }

    /**
     * Check if a deck exists by ID.
     */
    fun existsById(id: UUID): Boolean {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM decks WHERE id = ?",
            Int::class.java,
            id
        )
        return count != null && count > 0
    }
}
