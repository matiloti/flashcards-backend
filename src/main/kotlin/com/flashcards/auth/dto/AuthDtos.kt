package com.flashcards.auth.dto

import com.flashcards.auth.TokensResult
import com.flashcards.user.User

// Request DTOs

data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class RefreshRequest(
    val refreshToken: String
)

data class LogoutRequest(
    val refreshToken: String = ""
)

// Response DTOs

data class AuthResponse(
    val user: UserDto,
    val tokens: TokensDto
)

data class RefreshResponse(
    val tokens: TokensDto
)

data class TokensDto(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresIn: Long,
    val refreshTokenExpiresIn: Long
) {
    companion object {
        fun from(tokens: TokensResult) = TokensDto(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            accessTokenExpiresIn = tokens.accessTokenExpiresIn,
            refreshTokenExpiresIn = tokens.refreshTokenExpiresIn
        )
    }
}

data class UserDto(
    val id: String,
    val email: String,
    val displayName: String,
    val createdAt: String
) {
    companion object {
        fun from(user: User) = UserDto(
            id = user.id.toString(),
            email = user.email,
            displayName = user.displayName,
            createdAt = user.createdAt.toString()
        )
    }
}
