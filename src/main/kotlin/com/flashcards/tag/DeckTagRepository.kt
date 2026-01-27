package com.flashcards.tag

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class DeckTagRepository(private val jdbcTemplate: JdbcTemplate) {

    private val tagSummaryRowMapper = RowMapper { rs: ResultSet, _: Int ->
        TagSummary(
            id = UUID.fromString(rs.getString("id")),
            name = rs.getString("name")
        )
    }

    /**
     * Find all tags for a deck, ordered by name.
     */
    fun findTagsByDeckId(deckId: UUID): List<TagSummary> {
        return jdbcTemplate.query(
            """
            SELECT t.id, t.name
            FROM tags t
            INNER JOIN deck_tags dt ON t.id = dt.tag_id
            WHERE dt.deck_id = ?
            ORDER BY t.name ASC
            """.trimIndent(),
            tagSummaryRowMapper,
            deckId
        )
    }

    /**
     * Set tags for a deck, replacing any existing tags.
     * Deduplicates tag IDs before inserting.
     */
    fun setTagsForDeck(deckId: UUID, tagIds: List<UUID>) {
        // Remove existing tags
        jdbcTemplate.update("DELETE FROM deck_tags WHERE deck_id = ?", deckId)

        // Insert new tags (deduplicated)
        val uniqueTagIds = tagIds.distinct()
        for (tagId in uniqueTagIds) {
            jdbcTemplate.update(
                "INSERT INTO deck_tags (deck_id, tag_id, created_at) VALUES (?, ?, NOW())",
                deckId, tagId
            )
        }
    }

    /**
     * Count how many decks have a specific tag.
     */
    fun countByTagId(tagId: UUID): Int {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM deck_tags WHERE tag_id = ?",
            Int::class.java,
            tagId
        ) ?: 0
    }

    /**
     * Delete all tags from a deck.
     */
    fun deleteByDeckId(deckId: UUID) {
        jdbcTemplate.update("DELETE FROM deck_tags WHERE deck_id = ?", deckId)
    }

    /**
     * Find tags for multiple decks in a single query (avoids N+1).
     * Returns a map of deckId -> list of TagSummary.
     */
    fun findTagsForDecks(deckIds: List<UUID>): Map<UUID, List<TagSummary>> {
        if (deckIds.isEmpty()) return emptyMap()

        val placeholders = deckIds.joinToString(",") { "?" }

        val results = jdbcTemplate.query(
            """
            SELECT dt.deck_id, t.id, t.name
            FROM deck_tags dt
            INNER JOIN tags t ON dt.tag_id = t.id
            WHERE dt.deck_id IN ($placeholders)
            ORDER BY t.name ASC
            """.trimIndent(),
            { rs, _ ->
                Triple(
                    UUID.fromString(rs.getString("deck_id")),
                    UUID.fromString(rs.getString("id")),
                    rs.getString("name")
                )
            },
            *deckIds.toTypedArray()
        )

        return results.groupBy(
            { it.first },
            { TagSummary(it.second, it.third) }
        )
    }
}
