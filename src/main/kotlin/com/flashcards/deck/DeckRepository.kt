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

    fun findAll(userId: UUID): List<Deck> {
        return jdbcTemplate.query(
            """
            SELECT d.id, d.name, d.deck_type, d.last_studied_at, d.created_at, d.updated_at,
                   COUNT(DISTINCT c.id) AS card_count
            FROM decks d
            LEFT JOIN cards c ON c.deck_id = d.id
            WHERE d.user_id = ?
            GROUP BY d.id, d.name, d.deck_type, d.last_studied_at, d.created_at, d.updated_at
            ORDER BY d.updated_at DESC
            """.trimIndent(),
            rowMapper,
            userId
        )
    }

    fun findById(id: UUID, userId: UUID): Deck? {
        val results = jdbcTemplate.query(
            """
            SELECT d.id, d.name, d.deck_type, d.last_studied_at, d.created_at, d.updated_at,
                   COUNT(DISTINCT c.id) AS card_count
            FROM decks d
            LEFT JOIN cards c ON c.deck_id = d.id
            WHERE d.id = ? AND d.user_id = ?
            GROUP BY d.id, d.name, d.deck_type, d.last_studied_at, d.created_at, d.updated_at
            """.trimIndent(),
            rowMapper,
            id, userId
        )
        return results.firstOrNull()
    }

    fun create(name: String, type: DeckType = DeckType.STUDY, userId: UUID): Deck {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcTemplate.update(
            "INSERT INTO decks (id, name, deck_type, user_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
            id, name, type.name, userId, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now)
        )
        return Deck(id = id, name = name, type = type, cardCount = 0, createdAt = now, updatedAt = now)
    }

    fun update(id: UUID, name: String, userId: UUID): Boolean {
        val updated = jdbcTemplate.update(
            "UPDATE decks SET name = ?, updated_at = NOW() WHERE id = ? AND user_id = ?",
            name, id, userId
        )
        return updated > 0
    }

    fun delete(id: UUID, userId: UUID): Boolean {
        val deleted = jdbcTemplate.update("DELETE FROM decks WHERE id = ? AND user_id = ?", id, userId)
        return deleted > 0
    }

    /**
     * Find recently studied decks ordered by lastStudiedAt DESC.
     * Only returns decks that have been studied (lastStudiedAt IS NOT NULL).
     *
     * @param userId User ID to filter by
     * @param limit Maximum number of decks to return (1-10)
     * @return List of RecentDeck DTOs without createdAt/updatedAt fields
     */
    fun findRecentlyStudied(userId: UUID, limit: Int): List<RecentDeck> {
        return jdbcTemplate.query(
            """
            SELECT d.id, d.name, d.deck_type, d.last_studied_at,
                   COUNT(DISTINCT c.id) AS card_count
            FROM decks d
            LEFT JOIN cards c ON c.deck_id = d.id
            WHERE d.user_id = ? AND d.last_studied_at IS NOT NULL
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
            userId, limit
        )
    }

    /**
     * Update the lastStudiedAt timestamp for a deck.
     * Called when a study or flash review session is completed.
     * Note: This method does not filter by user_id since it's called internally
     * after the deck ownership has already been verified.
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
     * @param userId User ID to filter by
     * @param query Search query (min 2 chars, already validated by controller)
     * @param page Page number (0-indexed)
     * @param size Page size (1-50)
     * @return Paginated search results with matchedField indicator
     */
    fun search(userId: UUID, query: String, page: Int, size: Int): Page<DeckSearchResponse> {
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
            WHERE d.user_id = ? AND (
                d.name ILIKE '%' || ? || '%'
                OR d.description ILIKE '%' || ? || '%'
            )
            GROUP BY d.id
            ORDER BY
                CASE WHEN d.name ILIKE '%' || ? || '%' THEN 0 ELSE 1 END,
                d.name ASC
            LIMIT ? OFFSET ?
        """.trimIndent()

        val countSql = """
            SELECT COUNT(*)
            FROM decks d
            WHERE d.user_id = ? AND (
                d.name ILIKE '%' || ? || '%'
                OR d.description ILIKE '%' || ? || '%'
            )
        """.trimIndent()

        val results = jdbcTemplate.query(
            searchSql,
            { rs, _ -> mapToDeckSearchResponse(rs) },
            query, userId, query, query, query, size, offset
        )

        val totalElements = jdbcTemplate.queryForObject(countSql, Long::class.java, userId, query, query)
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
    fun findAllByTagId(userId: UUID, tagId: UUID): List<Deck> {
        return jdbcTemplate.query(
            """
            SELECT d.id, d.name, d.deck_type, d.last_studied_at, d.created_at, d.updated_at,
                   COUNT(DISTINCT c.id) AS card_count
            FROM decks d
            INNER JOIN deck_tags dt ON d.id = dt.deck_id AND dt.tag_id = ?
            LEFT JOIN cards c ON c.deck_id = d.id
            WHERE d.user_id = ?
            GROUP BY d.id, d.name, d.deck_type, d.last_studied_at, d.created_at, d.updated_at
            ORDER BY d.updated_at DESC
            """.trimIndent(),
            rowMapper,
            tagId, userId
        )
    }

    /**
     * Find decks that have no tags (untagged).
     */
    fun findAllUntagged(userId: UUID): List<Deck> {
        return jdbcTemplate.query(
            """
            SELECT d.id, d.name, d.deck_type, d.last_studied_at, d.created_at, d.updated_at,
                   COUNT(DISTINCT c.id) AS card_count
            FROM decks d
            LEFT JOIN deck_tags dt ON d.id = dt.deck_id
            LEFT JOIN cards c ON c.deck_id = d.id
            WHERE d.user_id = ? AND dt.deck_id IS NULL
            GROUP BY d.id, d.name, d.deck_type, d.last_studied_at, d.created_at, d.updated_at
            ORDER BY d.updated_at DESC
            """.trimIndent(),
            rowMapper,
            userId
        )
    }

    /**
     * Check if a deck exists by ID and belongs to user.
     */
    fun existsById(id: UUID, userId: UUID): Boolean {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM decks WHERE id = ? AND user_id = ?",
            Int::class.java,
            id, userId
        )
        return count != null && count > 0
    }
}
