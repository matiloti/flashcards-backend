package com.flashcards.user

import com.flashcards.auth.InvalidDisplayNameException
import com.flashcards.security.JwtAuthentication
import com.flashcards.user.dto.UpdateUserRequest
import com.flashcards.user.dto.UserProfileResponse
import com.flashcards.user.dto.UserResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService
) {

    @GetMapping("/me")
    fun getProfile(authentication: JwtAuthentication): ResponseEntity<Any> {
        val profile = userService.getProfile(authentication.userId)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(UserProfileResponse.from(profile.user, profile.stats))
    }

    @PatchMapping("/me")
    fun updateProfile(
        authentication: JwtAuthentication,
        @RequestBody request: UpdateUserRequest
    ): ResponseEntity<Any> {
        // Validate display name if provided
        if (request.displayName != null) {
            val trimmed = request.displayName.trim()

            return try {
                val updated = userService.updateDisplayName(authentication.userId, trimmed)
                    ?: return ResponseEntity.notFound().build()

                ResponseEntity.ok(UserResponse.from(updated))
            } catch (e: InvalidDisplayNameException) {
                ResponseEntity.badRequest().body(
                    mapOf(
                        "error" to "INVALID_DISPLAY_NAME",
                        "message" to e.message,
                        "field" to "displayName"
                    )
                )
            }
        }

        // No updates requested
        return ResponseEntity.badRequest().body(
            mapOf(
                "error" to "NO_UPDATES",
                "message" to "No fields to update"
            )
        )
    }
}
