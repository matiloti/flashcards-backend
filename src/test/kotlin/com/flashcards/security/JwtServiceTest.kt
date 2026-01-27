package com.flashcards.security

import com.flashcards.user.User
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.security.SignatureException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class JwtServiceTest {

    private val secret = "test-secret-key-that-is-at-least-32-bytes-long-for-hs256-testing"
    private val accessTokenExpiry = 900L  // 15 minutes
    private val refreshTokenExpiry = 2592000L  // 30 days
    private val issuer = "flashcards-api-test"

    private val jwtService = JwtService(
        secret = secret,
        accessTokenExpirySeconds = accessTokenExpiry,
        refreshTokenExpirySeconds = refreshTokenExpiry,
        issuer = issuer
    )

    private val testUser = User(
        id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
        email = "test@example.com",
        displayName = "Test User",
        emailVerified = false,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    // =========================================================================
    // generateAccessToken() tests
    // =========================================================================

    @Test
    fun `generateAccessToken returns valid JWT`() {
        val token = jwtService.generateAccessToken(testUser)

        assertThat(token).isNotBlank()
        assertThat(token.split(".")).hasSize(3) // JWT has 3 parts
    }

    @Test
    fun `generateAccessToken includes user id as subject`() {
        val token = jwtService.generateAccessToken(testUser)

        val claims = jwtService.validateToken(token)

        assertThat(claims.subject).isEqualTo(testUser.id.toString())
    }

    @Test
    fun `generateAccessToken includes email claim`() {
        val token = jwtService.generateAccessToken(testUser)

        val claims = jwtService.validateToken(token)

        assertThat(claims["email"]).isEqualTo(testUser.email)
    }

    @Test
    fun `generateAccessToken includes correct issuer`() {
        val token = jwtService.generateAccessToken(testUser)

        val claims = jwtService.validateToken(token)

        assertThat(claims.issuer).isEqualTo(issuer)
    }

    @Test
    fun `generateAccessToken sets correct expiration`() {
        val beforeGeneration = Instant.now()

        val token = jwtService.generateAccessToken(testUser)

        val claims = jwtService.validateToken(token)
        val expirationTime = claims.expiration.toInstant()
        val expectedMinExpiry = beforeGeneration.plusSeconds(accessTokenExpiry - 1)
        val expectedMaxExpiry = beforeGeneration.plusSeconds(accessTokenExpiry + 1)

        assertThat(expirationTime).isAfter(expectedMinExpiry)
        assertThat(expirationTime).isBefore(expectedMaxExpiry)
    }

    // =========================================================================
    // validateToken() tests
    // =========================================================================

    @Test
    fun `validateToken returns claims for valid token`() {
        val token = jwtService.generateAccessToken(testUser)

        val claims = jwtService.validateToken(token)

        assertThat(claims).isNotNull
        assertThat(claims.subject).isEqualTo(testUser.id.toString())
    }

    @Test
    fun `validateToken throws exception for invalid token`() {
        val invalidToken = "invalid.jwt.token"

        assertThatThrownBy { jwtService.validateToken(invalidToken) }
            .isInstanceOf(Exception::class.java)
    }

    @Test
    fun `validateToken throws exception for tampered token`() {
        val token = jwtService.generateAccessToken(testUser)
        // Tamper with the signature
        val tamperedToken = token.dropLast(5) + "XXXXX"

        assertThatThrownBy { jwtService.validateToken(tamperedToken) }
            .isInstanceOf(SignatureException::class.java)
    }

    @Test
    fun `validateToken throws exception for token signed with different secret`() {
        // Create a service with different secret
        val otherService = JwtService(
            secret = "different-secret-key-that-is-also-32-bytes-for-testing",
            accessTokenExpirySeconds = accessTokenExpiry,
            refreshTokenExpirySeconds = refreshTokenExpiry,
            issuer = issuer
        )

        val token = otherService.generateAccessToken(testUser)

        assertThatThrownBy { jwtService.validateToken(token) }
            .isInstanceOf(SignatureException::class.java)
    }

    @Test
    fun `validateToken throws exception for expired token`() {
        // Create a service with 0 second expiry
        val expiredService = JwtService(
            secret = secret,
            accessTokenExpirySeconds = 0,
            refreshTokenExpirySeconds = 0,
            issuer = issuer
        )

        val token = expiredService.generateAccessToken(testUser)

        // Wait a tiny bit to ensure token is expired
        Thread.sleep(100)

        assertThatThrownBy { jwtService.validateToken(token) }
            .isInstanceOf(ExpiredJwtException::class.java)
    }

    @Test
    fun `validateToken throws exception for wrong issuer`() {
        val otherService = JwtService(
            secret = secret,
            accessTokenExpirySeconds = accessTokenExpiry,
            refreshTokenExpirySeconds = refreshTokenExpiry,
            issuer = "wrong-issuer"
        )

        val token = otherService.generateAccessToken(testUser)

        assertThatThrownBy { jwtService.validateToken(token) }
            .isInstanceOf(Exception::class.java)
    }

    // =========================================================================
    // generateRefreshToken() tests
    // =========================================================================

    @Test
    fun `generateRefreshToken returns 64-character hex string`() {
        val token = jwtService.generateRefreshToken()

        assertThat(token).hasSize(64)
        assertThat(token).matches("[0-9a-f]+")
    }

    @Test
    fun `generateRefreshToken returns unique tokens`() {
        val tokens = (1..100).map { jwtService.generateRefreshToken() }

        assertThat(tokens).doesNotHaveDuplicates()
    }

    @Test
    fun `generateRefreshToken returns different token each call`() {
        val token1 = jwtService.generateRefreshToken()
        val token2 = jwtService.generateRefreshToken()

        assertThat(token1).isNotEqualTo(token2)
    }

    // =========================================================================
    // Property access tests
    // =========================================================================

    @Test
    fun `accessTokenExpirySeconds returns configured value`() {
        assertThat(jwtService.accessTokenExpirySeconds).isEqualTo(accessTokenExpiry)
    }

    @Test
    fun `refreshTokenExpirySeconds returns configured value`() {
        assertThat(jwtService.refreshTokenExpirySeconds).isEqualTo(refreshTokenExpiry)
    }
}
