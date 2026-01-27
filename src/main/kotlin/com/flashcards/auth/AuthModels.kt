package com.flashcards.auth

import com.flashcards.user.User

/**
 * Result of a successful authentication (register or login).
 */
data class AuthResult(
    val user: User,
    val tokens: TokensResult
)

/**
 * Token pair with expiry information.
 */
data class TokensResult(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresIn: Long,
    val refreshTokenExpiresIn: Long
)
