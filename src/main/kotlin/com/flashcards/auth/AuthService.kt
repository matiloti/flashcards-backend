package com.flashcards.auth

import com.flashcards.security.JwtService
import com.flashcards.security.PasswordService
import com.flashcards.user.User
import com.flashcards.user.UserRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordService: PasswordService,
    private val jwtService: JwtService
) {

    companion object {
        // Dummy bcrypt hash for timing attack prevention
        private const val DUMMY_HASH = "\$2a\$12\$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/X4qKqWq8z4LqQZYHa"
    }

    /**
     * Register a new user.
     * @throws EmailAlreadyExistsException if email is taken
     * @throws InvalidPasswordException if password doesn't meet requirements
     * @throws InvalidDisplayNameException if display name doesn't meet requirements
     */
    fun register(
        email: String,
        password: String,
        displayName: String,
        deviceInfo: String? = null,
        ipAddress: String? = null
    ): AuthResult {
        // Validate password
        validatePassword(password)

        // Validate display name
        validateDisplayName(displayName)

        // Validate email uniqueness
        if (userRepository.existsByEmail(email)) {
            throw EmailAlreadyExistsException(email)
        }

        // Hash password and create user
        val passwordHash = passwordService.hash(password)
        val user = userRepository.create(email, passwordHash, displayName)

        // Generate tokens
        val tokens = generateTokens(user, deviceInfo, ipAddress)

        return AuthResult(user, tokens)
    }

    /**
     * Authenticate user with email and password.
     * @throws InvalidCredentialsException if credentials are wrong
     */
    fun login(
        email: String,
        password: String,
        deviceInfo: String? = null,
        ipAddress: String? = null
    ): AuthResult {
        // Get password hash (or dummy hash for timing safety)
        val storedHash = userRepository.getPasswordHash(email)

        val isValid = if (storedHash != null) {
            passwordService.verify(password, storedHash)
        } else {
            // Hash against dummy to prevent timing attacks
            passwordService.verify(password, DUMMY_HASH)
            false
        }

        if (!isValid) {
            throw InvalidCredentialsException()
        }

        val user = userRepository.findByEmail(email)
            ?: throw InvalidCredentialsException()

        val tokens = generateTokens(user, deviceInfo, ipAddress)

        return AuthResult(user, tokens)
    }

    /**
     * Refresh access token using refresh token.
     * Implements token rotation: old token is revoked, new one is issued.
     * @throws InvalidTokenException if refresh token is invalid
     */
    fun refresh(
        refreshToken: String,
        deviceInfo: String? = null,
        ipAddress: String? = null
    ): TokensResult {
        // Find and validate token
        val token = refreshTokenRepository.findActiveByToken(refreshToken)
            ?: throw InvalidTokenException("Invalid or expired refresh token")

        // Revoke old token (rotation)
        refreshTokenRepository.revokeByToken(refreshToken)

        // Get user
        val user = userRepository.findById(token.userId)
            ?: throw InvalidTokenException("User not found")

        // Generate new tokens
        return generateTokens(user, deviceInfo, ipAddress)
    }

    /**
     * Logout by revoking the refresh token.
     */
    fun logout(refreshToken: String) {
        refreshTokenRepository.revokeByToken(refreshToken)
    }

    /**
     * Logout from all devices by revoking all refresh tokens.
     */
    fun logoutAll(userId: UUID) {
        refreshTokenRepository.revokeAllForUser(userId)
    }

    private fun generateTokens(
        user: User,
        deviceInfo: String?,
        ipAddress: String?
    ): TokensResult {
        // Generate access token (JWT)
        val accessToken = jwtService.generateAccessToken(user)
        val accessTokenExpiresIn = jwtService.accessTokenExpirySeconds

        // Generate refresh token (opaque)
        val refreshTokenValue = jwtService.generateRefreshToken()
        val refreshTokenExpiresAt = Instant.now().plusSeconds(jwtService.refreshTokenExpirySeconds)

        // Store refresh token
        refreshTokenRepository.create(
            userId = user.id,
            token = refreshTokenValue,
            expiresAt = refreshTokenExpiresAt,
            deviceInfo = deviceInfo,
            ipAddress = ipAddress
        )

        return TokensResult(
            accessToken = accessToken,
            refreshToken = refreshTokenValue,
            accessTokenExpiresIn = accessTokenExpiresIn,
            refreshTokenExpiresIn = jwtService.refreshTokenExpirySeconds
        )
    }

    private fun validatePassword(password: String) {
        when {
            password.length < 8 ->
                throw InvalidPasswordException("Password must be at least 8 characters")
            password.length > 72 ->
                throw InvalidPasswordException("Password must not exceed 72 characters")
        }
    }

    private fun validateDisplayName(displayName: String) {
        val trimmed = displayName.trim()
        when {
            trimmed.length < 2 ->
                throw InvalidDisplayNameException("Display name must be at least 2 characters")
            trimmed.length > 50 ->
                throw InvalidDisplayNameException("Display name must not exceed 50 characters")
        }
    }
}
