package com.flashcards.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class RefreshTokenRepositoryTest {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var refreshTokenRepository: RefreshTokenRepository

    private lateinit var testUserId: UUID

    @BeforeEach
    fun setup() {
        // Clean up tokens (but keep default user)
        jdbcTemplate.execute("DELETE FROM refresh_tokens")

        // Create a test user
        testUserId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO users (id, email, password_hash, display_name, created_at, updated_at)
            VALUES (?, ?, '${"$"}2a${"$"}12${"$"}hashvalue123456789012345678', 'Test User', NOW(), NOW())
            """.trimIndent(),
            testUserId, "tokentest-${testUserId}@example.com"
        )
    }

    // =========================================================================
    // create() tests
    // =========================================================================

    @Test
    fun `create saves refresh token and returns it`() {
        val tokenValue = "abc123def456" + "a".repeat(52) // 64 chars
        val expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)

        val token = refreshTokenRepository.create(
            userId = testUserId,
            token = tokenValue,
            expiresAt = expiresAt,
            deviceInfo = "TestDevice",
            ipAddress = "192.168.1.1"
        )

        assertThat(token.id).isNotNull()
        assertThat(token.userId).isEqualTo(testUserId)
        assertThat(token.token).isEqualTo(tokenValue)
        assertThat(token.expiresAt).isEqualTo(expiresAt)
        assertThat(token.revoked).isFalse()
        assertThat(token.revokedAt).isNull()
        assertThat(token.deviceInfo).isEqualTo("TestDevice")
        assertThat(token.ipAddress).isEqualTo("192.168.1.1")
        assertThat(token.createdAt).isNotNull()
    }

    @Test
    fun `create works with null device info and ip address`() {
        val tokenValue = "xyz789" + "b".repeat(58)
        val expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)

        val token = refreshTokenRepository.create(
            userId = testUserId,
            token = tokenValue,
            expiresAt = expiresAt
        )

        assertThat(token.deviceInfo).isNull()
        assertThat(token.ipAddress).isNull()
    }

    // =========================================================================
    // findActiveByToken() tests
    // =========================================================================

    @Test
    fun `findActiveByToken returns token when found and active`() {
        val tokenValue = "findactive" + "c".repeat(54)
        val expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
        refreshTokenRepository.create(
            userId = testUserId,
            token = tokenValue,
            expiresAt = expiresAt
        )

        val found = refreshTokenRepository.findActiveByToken(tokenValue)

        assertThat(found).isNotNull
        assertThat(found!!.token).isEqualTo(tokenValue)
        assertThat(found.userId).isEqualTo(testUserId)
    }

    @Test
    fun `findActiveByToken returns null for revoked token`() {
        val tokenValue = "revokedtoken" + "d".repeat(52)
        val expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
        refreshTokenRepository.create(
            userId = testUserId,
            token = tokenValue,
            expiresAt = expiresAt
        )
        refreshTokenRepository.revokeByToken(tokenValue)

        val found = refreshTokenRepository.findActiveByToken(tokenValue)

        assertThat(found).isNull()
    }

    @Test
    fun `findActiveByToken returns null for expired token`() {
        val tokenValue = "expiredtoken" + "e".repeat(52)
        val expiredAt = Instant.now().minus(1, ChronoUnit.DAYS)

        // Insert directly with past expiry
        jdbcTemplate.update(
            """
            INSERT INTO refresh_tokens (id, user_id, token, expires_at, created_at)
            VALUES (?, ?, ?, ?, NOW())
            """.trimIndent(),
            UUID.randomUUID(), testUserId, tokenValue,
            java.sql.Timestamp.from(expiredAt)
        )

        val found = refreshTokenRepository.findActiveByToken(tokenValue)

        assertThat(found).isNull()
    }

    @Test
    fun `findActiveByToken returns null for non-existent token`() {
        val found = refreshTokenRepository.findActiveByToken("nonexistent" + "f".repeat(53))

        assertThat(found).isNull()
    }

    // =========================================================================
    // revokeByToken() tests
    // =========================================================================

    @Test
    fun `revokeByToken revokes token and returns true`() {
        val tokenValue = "torevoke" + "g".repeat(56)
        val expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
        refreshTokenRepository.create(
            userId = testUserId,
            token = tokenValue,
            expiresAt = expiresAt
        )

        val revoked = refreshTokenRepository.revokeByToken(tokenValue)

        assertThat(revoked).isTrue()

        // Verify token is actually revoked
        val found = refreshTokenRepository.findActiveByToken(tokenValue)
        assertThat(found).isNull()
    }

    @Test
    fun `revokeByToken returns false for already revoked token`() {
        val tokenValue = "alreadyrevoked" + "h".repeat(50)
        val expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
        refreshTokenRepository.create(
            userId = testUserId,
            token = tokenValue,
            expiresAt = expiresAt
        )
        refreshTokenRepository.revokeByToken(tokenValue)

        val revoked = refreshTokenRepository.revokeByToken(tokenValue)

        assertThat(revoked).isFalse()
    }

    @Test
    fun `revokeByToken returns false for non-existent token`() {
        val revoked = refreshTokenRepository.revokeByToken("nonexistent" + "i".repeat(53))

        assertThat(revoked).isFalse()
    }

    // =========================================================================
    // revokeAllForUser() tests
    // =========================================================================

    @Test
    fun `revokeAllForUser revokes all tokens for user`() {
        val token1 = "usertoken1" + "j".repeat(54)
        val token2 = "usertoken2" + "k".repeat(54)
        val token3 = "usertoken3" + "l".repeat(54)
        val expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)

        refreshTokenRepository.create(testUserId, token1, expiresAt)
        refreshTokenRepository.create(testUserId, token2, expiresAt)
        refreshTokenRepository.create(testUserId, token3, expiresAt)

        val revokedCount = refreshTokenRepository.revokeAllForUser(testUserId)

        assertThat(revokedCount).isEqualTo(3)

        // Verify all are revoked
        assertThat(refreshTokenRepository.findActiveByToken(token1)).isNull()
        assertThat(refreshTokenRepository.findActiveByToken(token2)).isNull()
        assertThat(refreshTokenRepository.findActiveByToken(token3)).isNull()
    }

    @Test
    fun `revokeAllForUser does not affect other users`() {
        // Create another user
        val otherUserId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO users (id, email, password_hash, display_name, created_at, updated_at)
            VALUES (?, ?, '${"$"}2a${"$"}12${"$"}hashvalue123456789012345678', 'Other User', NOW(), NOW())
            """.trimIndent(),
            otherUserId, "other-${otherUserId}@example.com"
        )

        val testUserToken = "testuser" + "m".repeat(56)
        val otherUserToken = "otheruser" + "n".repeat(55)
        val expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)

        refreshTokenRepository.create(testUserId, testUserToken, expiresAt)
        refreshTokenRepository.create(otherUserId, otherUserToken, expiresAt)

        refreshTokenRepository.revokeAllForUser(testUserId)

        // Test user's token is revoked
        assertThat(refreshTokenRepository.findActiveByToken(testUserToken)).isNull()
        // Other user's token is still active
        assertThat(refreshTokenRepository.findActiveByToken(otherUserToken)).isNotNull
    }

    @Test
    fun `revokeAllForUser returns 0 when no active tokens`() {
        val revokedCount = refreshTokenRepository.revokeAllForUser(testUserId)

        assertThat(revokedCount).isEqualTo(0)
    }

    // =========================================================================
    // deleteExpiredAndRevoked() tests
    // =========================================================================

    @Test
    fun `deleteExpiredAndRevoked removes old tokens`() {
        // Insert token expired more than 7 days ago
        val oldExpiredToken = "oldexpired" + "o".repeat(54)
        jdbcTemplate.update(
            """
            INSERT INTO refresh_tokens (id, user_id, token, expires_at, created_at)
            VALUES (?, ?, ?, NOW() - INTERVAL '8 days', NOW() - INTERVAL '38 days')
            """.trimIndent(),
            UUID.randomUUID(), testUserId, oldExpiredToken
        )

        // Insert token revoked more than 7 days ago
        val oldRevokedToken = "oldrevoked" + "p".repeat(54)
        jdbcTemplate.update(
            """
            INSERT INTO refresh_tokens (id, user_id, token, expires_at, revoked, revoked_at, created_at)
            VALUES (?, ?, ?, NOW() + INTERVAL '20 days', true, NOW() - INTERVAL '8 days', NOW() - INTERVAL '10 days')
            """.trimIndent(),
            UUID.randomUUID(), testUserId, oldRevokedToken
        )

        // Insert active token (should not be deleted)
        val activeToken = "activetoken" + "q".repeat(53)
        refreshTokenRepository.create(testUserId, activeToken, Instant.now().plus(30, ChronoUnit.DAYS))

        val deletedCount = refreshTokenRepository.deleteExpiredAndRevoked()

        assertThat(deletedCount).isEqualTo(2)
        // Active token still exists
        assertThat(refreshTokenRepository.findActiveByToken(activeToken)).isNotNull
    }
}
