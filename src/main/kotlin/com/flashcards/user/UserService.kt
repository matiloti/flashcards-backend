package com.flashcards.user

import com.flashcards.auth.InvalidDisplayNameException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository
) {

    /**
     * User profile with stats for display.
     */
    data class UserProfileResult(
        val user: User,
        val stats: UserStats
    )

    /**
     * Get user profile with stats.
     */
    fun getProfile(userId: UUID): UserProfileResult? {
        val user = userRepository.findById(userId) ?: return null
        val stats = userRepository.getUserStats(userId)

        return UserProfileResult(user, stats)
    }

    /**
     * Update user's display name.
     * @throws InvalidDisplayNameException if display name doesn't meet requirements
     */
    fun updateDisplayName(userId: UUID, displayName: String): User? {
        val trimmed = displayName.trim()

        // Validate
        when {
            trimmed.length < 2 ->
                throw InvalidDisplayNameException("Display name must be at least 2 characters")
            trimmed.length > 50 ->
                throw InvalidDisplayNameException("Display name must not exceed 50 characters")
        }

        // Update
        if (!userRepository.updateDisplayName(userId, trimmed)) {
            return null
        }

        return userRepository.findById(userId)
    }
}
