package com.flashcards.tag

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class TagRepository(private val jdbcTemplate: JdbcTemplate) {

    private val tagRowMapper = RowMapper { rs: ResultSet, _: Int ->
        Tag(
            id = UUID.fromString(rs.getString("id")),
            name = rs.getString("name"),
            userId = UUID.fromString(rs.getString("user_id")),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    private val tagWithDeckCountRowMapper = RowMapper { rs: ResultSet, _: Int ->
        TagWithDeckCount(
            id = UUID.fromString(rs.getString("id")),
            name = rs.getString("name"),
            userId = UUID.fromString(rs.getString("user_id")),
            deckCount = rs.getInt("deck_count"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    /**
     * Find all tags for a user with deck counts, ordered by name.
     */
    fun findByUserIdWithDeckCount(userId: UUID): List<TagWithDeckCount> {
        return jdbcTemplate.query(
            """
            SELECT t.id, t.name, t.user_id, t.created_at, COUNT(dt.deck_id) as deck_count
            FROM tags t
            LEFT JOIN deck_tags dt ON t.id = dt.tag_id
            WHERE t.user_id = ?
            GROUP BY t.id, t.name, t.user_id, t.created_at
            ORDER BY t.name ASC
            """.trimIndent(),
            tagWithDeckCountRowMapper,
            userId
        )
    }

    /**
     * Find paginated tags for a user with deck counts.
     * @param userId The user ID
     * @param page Page number (0-indexed)
     * @param size Page size
     * @param sortField Sort field: "name" or "createdAt"
     * @param sortDir Sort direction: "asc" or "desc"
     */
    fun findByUserIdWithDeckCountPaginated(
        userId: UUID,
        page: Int,
        size: Int,
        sortField: String = "name",
        sortDir: String = "asc"
    ): List<TagWithDeckCount> {
        val orderBy = when (sortField) {
            "createdAt" -> "t.created_at"
            else -> "t.name"
        }
        val direction = if (sortDir.equals("desc", ignoreCase = true)) "DESC" else "ASC"
        val offset = page * size

        return jdbcTemplate.query(
            """
            SELECT t.id, t.name, t.user_id, t.created_at, COUNT(dt.deck_id) as deck_count
            FROM tags t
            LEFT JOIN deck_tags dt ON t.id = dt.tag_id
            WHERE t.user_id = ?
            GROUP BY t.id, t.name, t.user_id, t.created_at
            ORDER BY $orderBy $direction
            LIMIT ? OFFSET ?
            """.trimIndent(),
            tagWithDeckCountRowMapper,
            userId, size, offset
        )
    }

    /**
     * Count total tags for a user.
     */
    fun countByUserId(userId: UUID): Long {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tags WHERE user_id = ?",
            Long::class.java,
            userId
        ) ?: 0L
    }

    /**
     * Find a tag by ID and user ID.
     * Returns null if not found or belongs to different user.
     */
    fun findByIdAndUserId(id: UUID, userId: UUID): Tag? {
        val results = jdbcTemplate.query(
            "SELECT id, name, user_id, created_at FROM tags WHERE id = ? AND user_id = ?",
            tagRowMapper,
            id, userId
        )
        return results.firstOrNull()
    }

    /**
     * Check if a tag with the given name exists for the user (case-insensitive).
     */
    fun existsByUserIdAndNameIgnoreCase(userId: UUID, name: String): Boolean {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tags WHERE user_id = ? AND LOWER(name) = LOWER(?)",
            Int::class.java,
            userId, name
        )
        return count != null && count > 0
    }

    /**
     * Check if a tag with the given name exists for the user (case-insensitive),
     * excluding a specific tag ID (used for updates).
     */
    fun existsByUserIdAndNameIgnoreCaseExcludingId(userId: UUID, name: String, excludeId: UUID): Boolean {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tags WHERE user_id = ? AND LOWER(name) = LOWER(?) AND id != ?",
            Int::class.java,
            userId, name, excludeId
        )
        return count != null && count > 0
    }

    /**
     * Save a new tag to the database.
     */
    fun save(tag: Tag): Tag {
        jdbcTemplate.update(
            "INSERT INTO tags (id, name, user_id, created_at) VALUES (?, ?, ?, ?)",
            tag.id, tag.name, tag.userId, java.sql.Timestamp.from(tag.createdAt)
        )
        return tag
    }

    /**
     * Update an existing tag.
     */
    fun update(tag: Tag): Tag {
        jdbcTemplate.update(
            "UPDATE tags SET name = ? WHERE id = ?",
            tag.name, tag.id
        )
        return tag
    }

    /**
     * Delete a tag by ID.
     * Associated deck_tags entries are deleted via CASCADE.
     */
    fun deleteById(id: UUID) {
        jdbcTemplate.update("DELETE FROM tags WHERE id = ?", id)
    }

    /**
     * Find multiple tags by IDs for a user.
     * Returns only tags that exist and belong to the user.
     */
    fun findByIdsAndUserId(ids: List<UUID>, userId: UUID): List<Tag> {
        if (ids.isEmpty()) return emptyList()

        val placeholders = ids.joinToString(",") { "?" }
        val args = listOf(userId) + ids

        return jdbcTemplate.query(
            """
            SELECT id, name, user_id, created_at
            FROM tags
            WHERE user_id = ? AND id IN ($placeholders)
            """.trimIndent(),
            tagRowMapper,
            *args.toTypedArray()
        )
    }
}
