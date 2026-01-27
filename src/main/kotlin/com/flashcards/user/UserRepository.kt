package com.flashcards.user

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class UserRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        User(
            id = UUID.fromString(rs.getString("id")),
            email = rs.getString("email"),
            displayName = rs.getString("display_name"),
            emailVerified = rs.getBoolean("email_verified"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }

    /**
     * Find user by ID.
     */
    fun findById(id: UUID): User? {
        val results = jdbcTemplate.query(
            "SELECT id, email, display_name, email_verified, created_at, updated_at FROM users WHERE id = ?",
            rowMapper,
            id
        )
        return results.firstOrNull()
    }

    /**
     * Find user by email (case-insensitive).
     */
    fun findByEmail(email: String): User? {
        val results = jdbcTemplate.query(
            "SELECT id, email, display_name, email_verified, created_at, updated_at FROM users WHERE LOWER(email) = LOWER(?)",
            rowMapper,
            email
        )
        return results.firstOrNull()
    }

    /**
     * Get password hash for authentication.
     * Returns null if user not found.
     */
    fun getPasswordHash(email: String): String? {
        val results = jdbcTemplate.query(
            "SELECT password_hash FROM users WHERE LOWER(email) = LOWER(?)",
            { rs, _ -> rs.getString("password_hash") },
            email
        )
        return results.firstOrNull()
    }

    /**
     * Check if email exists (for registration validation).
     */
    fun existsByEmail(email: String): Boolean {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?)",
            Int::class.java,
            email
        )
        return count != null && count > 0
    }

    /**
     * Create a new user.
     * Returns the created user (without password hash).
     */
    fun create(
        email: String,
        passwordHash: String,
        displayName: String
    ): User {
        val id = UUID.randomUUID()
        val now = Instant.now()
        val normalizedEmail = email.trim().lowercase()
        val trimmedDisplayName = displayName.trim()

        jdbcTemplate.update(
            """
            INSERT INTO users (id, email, password_hash, display_name, email_verified, created_at, updated_at)
            VALUES (?, ?, ?, ?, false, ?, ?)
            """.trimIndent(),
            id, normalizedEmail, passwordHash, trimmedDisplayName,
            java.sql.Timestamp.from(now), java.sql.Timestamp.from(now)
        )

        return User(
            id = id,
            email = normalizedEmail,
            displayName = trimmedDisplayName,
            emailVerified = false,
            createdAt = now,
            updatedAt = now
        )
    }

    /**
     * Update user's display name.
     */
    fun updateDisplayName(id: UUID, displayName: String): Boolean {
        val updated = jdbcTemplate.update(
            "UPDATE users SET display_name = ?, updated_at = NOW() WHERE id = ?",
            displayName.trim(), id
        )
        return updated > 0
    }

    /**
     * Update user's password hash.
     */
    fun updatePasswordHash(id: UUID, passwordHash: String): Boolean {
        val updated = jdbcTemplate.update(
            "UPDATE users SET password_hash = ?, updated_at = NOW() WHERE id = ?",
            passwordHash, id
        )
        return updated > 0
    }

    /**
     * Get user statistics for profile.
     */
    fun getUserStats(userId: UUID): UserStats {
        val deckCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM decks WHERE user_id = ?",
            Int::class.java,
            userId
        ) ?: 0

        val cardCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(c.id)
            FROM cards c
            JOIN decks d ON c.deck_id = d.id
            WHERE d.user_id = ?
            """.trimIndent(),
            Int::class.java,
            userId
        ) ?: 0

        val stats = jdbcTemplate.query(
            """
            SELECT total_study_time_minutes, current_streak
            FROM user_statistics
            WHERE user_id = ?
            """.trimIndent(),
            { rs, _ ->
                Pair(
                    rs.getInt("total_study_time_minutes"),
                    rs.getInt("current_streak")
                )
            },
            userId
        ).firstOrNull()

        return UserStats(
            deckCount = deckCount,
            cardCount = cardCount,
            totalStudyTimeMinutes = stats?.first ?: 0,
            currentStreak = stats?.second ?: 0
        )
    }
}
