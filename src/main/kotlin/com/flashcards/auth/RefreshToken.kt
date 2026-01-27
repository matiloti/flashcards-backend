package com.flashcards.auth

import java.time.Instant
import java.util.UUID

data class RefreshToken(
    val id: UUID,
    val userId: UUID,
    val token: String,
    val expiresAt: Instant,
    val revoked: Boolean = false,
    val revokedAt: Instant? = null,
    val deviceInfo: String? = null,
    val ipAddress: String? = null,
    val createdAt: Instant
)
