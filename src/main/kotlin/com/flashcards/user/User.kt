package com.flashcards.user

import java.time.Instant
import java.util.UUID

/**
 * User account entity.
 *
 * Note: passwordHash is deliberately excluded from this class
 * to prevent accidental exposure. Use UserRepository for password operations.
 */
data class User(
    val id: UUID,
    val email: String,
    val displayName: String,
    val emailVerified: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * User statistics for profile display.
 */
data class UserStats(
    val deckCount: Int,
    val cardCount: Int,
    val totalStudyTimeMinutes: Int,
    val currentStreak: Int
)
