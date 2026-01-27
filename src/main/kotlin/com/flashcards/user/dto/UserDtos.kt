package com.flashcards.user.dto

import com.flashcards.user.User
import com.flashcards.user.UserStats

data class UserResponse(
    val id: String,
    val email: String,
    val displayName: String,
    val emailVerified: Boolean,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun from(user: User) = UserResponse(
            id = user.id.toString(),
            email = user.email,
            displayName = user.displayName,
            emailVerified = user.emailVerified,
            createdAt = user.createdAt.toString(),
            updatedAt = user.updatedAt.toString()
        )
    }
}

data class UserProfileResponse(
    val id: String,
    val email: String,
    val displayName: String,
    val emailVerified: Boolean,
    val createdAt: String,
    val stats: UserStatsDto
) {
    companion object {
        fun from(user: User, stats: UserStats) = UserProfileResponse(
            id = user.id.toString(),
            email = user.email,
            displayName = user.displayName,
            emailVerified = user.emailVerified,
            createdAt = user.createdAt.toString(),
            stats = UserStatsDto.from(stats)
        )
    }
}

data class UserStatsDto(
    val deckCount: Int,
    val cardCount: Int,
    val totalStudyTimeMinutes: Int,
    val currentStreak: Int
) {
    companion object {
        fun from(stats: UserStats) = UserStatsDto(
            deckCount = stats.deckCount,
            cardCount = stats.cardCount,
            totalStudyTimeMinutes = stats.totalStudyTimeMinutes,
            currentStreak = stats.currentStreak
        )
    }
}

data class UpdateUserRequest(
    val displayName: String? = null
)
