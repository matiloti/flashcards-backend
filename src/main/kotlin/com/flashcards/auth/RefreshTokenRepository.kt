package com.flashcards.auth

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class RefreshTokenRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        RefreshToken(
            id = UUID.fromString(rs.getString("id")),
            userId = UUID.fromString(rs.getString("user_id")),
            token = rs.getString("token"),
            expiresAt = rs.getTimestamp("expires_at").toInstant(),
            revoked = rs.getBoolean("revoked"),
            revokedAt = rs.getTimestamp("revoked_at")?.toInstant(),
            deviceInfo = rs.getString("device_info"),
            ipAddress = rs.getString("ip_address"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    /**
     * Find active (not revoked, not expired) token by token value.
     */
    fun findActiveByToken(token: String): RefreshToken? {
        val results = jdbcTemplate.query(
            """
            SELECT * FROM refresh_tokens
            WHERE token = ? AND NOT revoked AND expires_at > NOW()
            """.trimIndent(),
            rowMapper,
            token
        )
        return results.firstOrNull()
    }

    /**
     * Create a new refresh token.
     */
    fun create(
        userId: UUID,
        token: String,
        expiresAt: Instant,
        deviceInfo: String? = null,
        ipAddress: String? = null
    ): RefreshToken {
        val id = UUID.randomUUID()
        val now = Instant.now()

        jdbcTemplate.update(
            """
            INSERT INTO refresh_tokens (id, user_id, token, expires_at, device_info, ip_address, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id, userId, token, java.sql.Timestamp.from(expiresAt),
            deviceInfo, ipAddress, java.sql.Timestamp.from(now)
        )

        return RefreshToken(
            id = id,
            userId = userId,
            token = token,
            expiresAt = expiresAt,
            revoked = false,
            revokedAt = null,
            deviceInfo = deviceInfo,
            ipAddress = ipAddress,
            createdAt = now
        )
    }

    /**
     * Revoke a token by its value.
     */
    fun revokeByToken(token: String): Boolean {
        val updated = jdbcTemplate.update(
            """
            UPDATE refresh_tokens
            SET revoked = true, revoked_at = NOW()
            WHERE token = ? AND NOT revoked
            """.trimIndent(),
            token
        )
        return updated > 0
    }

    /**
     * Revoke all tokens for a user (logout all devices).
     */
    fun revokeAllForUser(userId: UUID): Int {
        return jdbcTemplate.update(
            """
            UPDATE refresh_tokens
            SET revoked = true, revoked_at = NOW()
            WHERE user_id = ? AND NOT revoked
            """.trimIndent(),
            userId
        )
    }

    /**
     * Delete expired and revoked tokens (cleanup job).
     * Keeps revoked tokens for 7 days for audit purposes.
     */
    fun deleteExpiredAndRevoked(): Int {
        return jdbcTemplate.update(
            """
            DELETE FROM refresh_tokens
            WHERE expires_at < NOW() - INTERVAL '7 days'
               OR (revoked AND revoked_at < NOW() - INTERVAL '7 days')
            """.trimIndent()
        )
    }
}
