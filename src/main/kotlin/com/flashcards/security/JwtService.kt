package com.flashcards.security

import com.flashcards.user.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Date
import javax.crypto.SecretKey

/**
 * Service for JWT token generation and validation.
 */
@Service
class JwtService(
    @Value("\${app.security.jwt.secret}")
    private val secret: String,

    @Value("\${app.security.jwt.access-token-expiry-seconds:900}")
    val accessTokenExpirySeconds: Long,

    @Value("\${app.security.jwt.refresh-token-expiry-seconds:2592000}")
    val refreshTokenExpirySeconds: Long,

    @Value("\${app.security.jwt.issuer:flashcards-api}")
    private val issuer: String
) {
    private val secretKey: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())
    private val secureRandom = SecureRandom()

    /**
     * Generate an access token (JWT) for the user.
     * @param user The user to generate the token for
     * @return The JWT access token
     */
    fun generateAccessToken(user: User): String {
        val now = Date()
        val expiry = Date(now.time + accessTokenExpirySeconds * 1000)

        return Jwts.builder()
            .subject(user.id.toString())
            .claim("email", user.email)
            .issuer(issuer)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(secretKey)
            .compact()
    }

    /**
     * Validate and parse an access token.
     * @param token The JWT token to validate
     * @return The token claims if valid
     * @throws JwtException if token is invalid, expired, or has wrong issuer
     */
    fun validateToken(token: String): Claims {
        return Jwts.parser()
            .verifyWith(secretKey)
            .requireIssuer(issuer)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    /**
     * Generate an opaque refresh token (256 bits of entropy).
     * @return A 64-character hex string
     */
    fun generateRefreshToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
