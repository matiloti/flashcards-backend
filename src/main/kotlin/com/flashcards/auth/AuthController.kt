package com.flashcards.auth

import com.flashcards.auth.dto.*
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/register")
    fun register(
        @RequestBody request: RegisterRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        // Validate request
        val validationError = validateRegisterRequest(request)
        if (validationError != null) {
            return ResponseEntity.badRequest().body(validationError)
        }

        return try {
            val result = authService.register(
                email = request.email,
                password = request.password,
                displayName = request.displayName,
                deviceInfo = httpRequest.getHeader("User-Agent"),
                ipAddress = getClientIp(httpRequest)
            )

            ResponseEntity.status(HttpStatus.CREATED).body(
                AuthResponse(
                    user = UserDto.from(result.user),
                    tokens = TokensDto.from(result.tokens)
                )
            )
        } catch (e: EmailAlreadyExistsException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(
                mapOf(
                    "error" to "EMAIL_ALREADY_EXISTS",
                    "message" to "This email is already registered",
                    "field" to "email"
                )
            )
        } catch (e: InvalidPasswordException) {
            ResponseEntity.badRequest().body(
                mapOf(
                    "error" to "INVALID_PASSWORD",
                    "message" to e.message,
                    "field" to "password"
                )
            )
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

    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        // Validate request
        if (request.email.isBlank() || request.password.isBlank()) {
            return ResponseEntity.badRequest().body(
                mapOf(
                    "error" to "MISSING_CREDENTIALS",
                    "message" to "Email and password are required"
                )
            )
        }

        return try {
            val result = authService.login(
                email = request.email,
                password = request.password,
                deviceInfo = httpRequest.getHeader("User-Agent"),
                ipAddress = getClientIp(httpRequest)
            )

            ResponseEntity.ok(
                AuthResponse(
                    user = UserDto.from(result.user),
                    tokens = TokensDto.from(result.tokens)
                )
            )
        } catch (e: InvalidCredentialsException) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                mapOf(
                    "error" to "INVALID_CREDENTIALS",
                    "message" to "Invalid email or password"
                )
            )
        }
    }

    @PostMapping("/refresh")
    fun refresh(
        @RequestBody request: RefreshRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        if (request.refreshToken.isBlank()) {
            return ResponseEntity.badRequest().body(
                mapOf(
                    "error" to "MISSING_TOKEN",
                    "message" to "Refresh token is required"
                )
            )
        }

        return try {
            val tokens = authService.refresh(
                refreshToken = request.refreshToken,
                deviceInfo = httpRequest.getHeader("User-Agent"),
                ipAddress = getClientIp(httpRequest)
            )

            ResponseEntity.ok(
                RefreshResponse(tokens = TokensDto.from(tokens))
            )
        } catch (e: InvalidTokenException) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                mapOf(
                    "error" to "INVALID_TOKEN",
                    "message" to "Invalid or expired refresh token"
                )
            )
        }
    }

    @PostMapping("/logout")
    fun logout(@RequestBody request: LogoutRequest): ResponseEntity<Void> {
        if (request.refreshToken.isNotBlank()) {
            authService.logout(request.refreshToken)
        }
        return ResponseEntity.noContent().build()
    }

    private fun validateRegisterRequest(request: RegisterRequest): Map<String, Any>? {
        return when {
            request.email.isBlank() -> mapOf(
                "error" to "INVALID_EMAIL",
                "message" to "Email is required",
                "field" to "email"
            )
            !isValidEmail(request.email) -> mapOf(
                "error" to "INVALID_EMAIL",
                "message" to "Invalid email format",
                "field" to "email"
            )
            request.password.isBlank() -> mapOf(
                "error" to "INVALID_PASSWORD",
                "message" to "Password is required",
                "field" to "password"
            )
            request.displayName.isBlank() -> mapOf(
                "error" to "INVALID_DISPLAY_NAME",
                "message" to "Display name is required",
                "field" to "displayName"
            )
            else -> null
        }
    }

    private fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        return emailRegex.matches(email)
    }

    private fun getClientIp(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        return xForwardedFor?.split(",")?.firstOrNull()?.trim()
            ?: request.remoteAddr
    }
}
