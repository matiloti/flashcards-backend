package com.flashcards.security

import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import java.util.UUID

/**
 * Authentication token containing the authenticated user's ID and email.
 * Used to identify the current user in controllers.
 */
class JwtAuthentication(
    val userId: UUID,
    val email: String
) : Authentication {

    private var authenticated = true

    override fun getName(): String = userId.toString()
    override fun getAuthorities(): Collection<GrantedAuthority> = emptyList()
    override fun getCredentials(): Any? = null
    override fun getDetails(): Any? = null
    override fun getPrincipal(): Any = userId
    override fun isAuthenticated(): Boolean = authenticated
    override fun setAuthenticated(isAuthenticated: Boolean) {
        authenticated = isAuthenticated
    }
}
