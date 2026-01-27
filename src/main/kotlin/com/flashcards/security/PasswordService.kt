package com.flashcards.security

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service

/**
 * Service for password hashing and verification using bcrypt.
 * Uses cost factor 12 for ~250ms hash time on modern hardware.
 */
@Service
class PasswordService {
    private val encoder = BCryptPasswordEncoder(12)

    /**
     * Hash a password using bcrypt with cost factor 12.
     * @param password The plain text password to hash
     * @return The bcrypt hash (60 characters)
     */
    fun hash(password: String): String = encoder.encode(password)

    /**
     * Verify a password against its hash.
     * @param password The plain text password to verify
     * @param hash The bcrypt hash to verify against
     * @return true if the password matches the hash
     */
    fun verify(password: String, hash: String): Boolean =
        encoder.matches(password, hash)
}
