package com.flashcards.auth

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setup() {
        jdbcTemplate.execute("DELETE FROM refresh_tokens WHERE user_id != '00000000-0000-0000-0000-000000000001'")
        jdbcTemplate.execute("DELETE FROM users WHERE id != '00000000-0000-0000-0000-000000000001'")
    }

    // =========================================================================
    // POST /api/v1/auth/register tests
    // =========================================================================

    @Test
    fun `register returns 201 with user and tokens`() {
        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "newuser@example.com",
                        "password": "password123",
                        "displayName": "New User"
                    }
                """.trimIndent())
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.user.id").isNotEmpty)
            .andExpect(jsonPath("$.user.email").value("newuser@example.com"))
            .andExpect(jsonPath("$.user.displayName").value("New User"))
            .andExpect(jsonPath("$.tokens.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.tokens.refreshToken").isNotEmpty)
            .andExpect(jsonPath("$.tokens.accessTokenExpiresIn").value(900))
            .andExpect(jsonPath("$.tokens.refreshTokenExpiresIn").value(2592000))
    }

    @Test
    fun `register returns 409 for duplicate email`() {
        // First registration
        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "duplicate@example.com",
                        "password": "password123",
                        "displayName": "First User"
                    }
                """.trimIndent())
        ).andExpect(status().isCreated)

        // Second registration with same email
        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "duplicate@example.com",
                        "password": "different123",
                        "displayName": "Second User"
                    }
                """.trimIndent())
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_EXISTS"))
            .andExpect(jsonPath("$.field").value("email"))
    }

    @Test
    fun `register returns 400 for invalid email`() {
        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "notanemail",
                        "password": "password123",
                        "displayName": "Test User"
                    }
                """.trimIndent())
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INVALID_EMAIL"))
    }

    @Test
    fun `register returns 400 for blank email`() {
        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "",
                        "password": "password123",
                        "displayName": "Test User"
                    }
                """.trimIndent())
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INVALID_EMAIL"))
    }

    @Test
    fun `register returns 400 for short password`() {
        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "shortpass@example.com",
                        "password": "short",
                        "displayName": "Test User"
                    }
                """.trimIndent())
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INVALID_PASSWORD"))
            .andExpect(jsonPath("$.field").value("password"))
    }

    @Test
    fun `register returns 400 for short display name`() {
        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "shortname@example.com",
                        "password": "password123",
                        "displayName": "X"
                    }
                """.trimIndent())
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INVALID_DISPLAY_NAME"))
            .andExpect(jsonPath("$.field").value("displayName"))
    }

    // =========================================================================
    // POST /api/v1/auth/login tests
    // =========================================================================

    @Test
    fun `login returns 200 with user and tokens`() {
        // Register first
        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "loginuser@example.com",
                        "password": "password123",
                        "displayName": "Login User"
                    }
                """.trimIndent())
        )

        // Login
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "loginuser@example.com",
                        "password": "password123"
                    }
                """.trimIndent())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.user.email").value("loginuser@example.com"))
            .andExpect(jsonPath("$.tokens.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.tokens.refreshToken").isNotEmpty)
    }

    @Test
    fun `login returns 401 for invalid credentials`() {
        // Register first
        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "wrongpass@example.com",
                        "password": "correctpassword",
                        "displayName": "Test User"
                    }
                """.trimIndent())
        )

        // Login with wrong password
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "wrongpass@example.com",
                        "password": "wrongpassword"
                    }
                """.trimIndent())
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
    }

    @Test
    fun `login returns 401 for non-existent user`() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "nonexistent@example.com",
                        "password": "password123"
                    }
                """.trimIndent())
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
    }

    @Test
    fun `login returns 400 for missing credentials`() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "",
                        "password": ""
                    }
                """.trimIndent())
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("MISSING_CREDENTIALS"))
    }

    // =========================================================================
    // POST /api/v1/auth/refresh tests
    // =========================================================================

    @Test
    fun `refresh returns 200 with new tokens`() {
        // Register to get tokens
        val registerResult = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "refresh@example.com",
                        "password": "password123",
                        "displayName": "Refresh User"
                    }
                """.trimIndent())
        ).andReturn()

        val refreshToken = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(registerResult.response.contentAsString)["tokens"]["refreshToken"].asText()

        // Refresh
        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "refreshToken": "$refreshToken"
                    }
                """.trimIndent())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tokens.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.tokens.refreshToken").isNotEmpty)
    }

    @Test
    fun `refresh returns 401 for invalid token`() {
        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "refreshToken": "invalidtoken${"x".repeat(52)}"
                    }
                """.trimIndent())
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("INVALID_TOKEN"))
    }

    @Test
    fun `refresh returns 400 for missing token`() {
        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "refreshToken": ""
                    }
                """.trimIndent())
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("MISSING_TOKEN"))
    }

    // =========================================================================
    // POST /api/v1/auth/logout tests
    // =========================================================================

    @Test
    fun `logout returns 204`() {
        // Register to get tokens
        val registerResult = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "logout@example.com",
                        "password": "password123",
                        "displayName": "Logout User"
                    }
                """.trimIndent())
        ).andReturn()

        val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
        val tokens = objectMapper.readTree(registerResult.response.contentAsString)["tokens"]
        val accessToken = tokens["accessToken"].asText()
        val refreshToken = tokens["refreshToken"].asText()

        // Logout
        mockMvc.perform(
            post("/api/v1/auth/logout")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "refreshToken": "$refreshToken"
                    }
                """.trimIndent())
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `logout requires authentication`() {
        mockMvc.perform(
            post("/api/v1/auth/logout")
                .header("X-Test-No-Auth", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "refreshToken": "sometoken"
                    }
                """.trimIndent())
        )
            .andExpect(status().isUnauthorized)
    }

    // =========================================================================
    // Security tests
    // =========================================================================

    @Test
    fun `protected endpoints require authentication`() {
        mockMvc.perform(
            get("/api/v1/users/me")
                .header("X-Test-No-Auth", "true")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `protected endpoints work with valid token`() {
        // Register to get tokens
        val registerResult = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "protected@example.com",
                        "password": "password123",
                        "displayName": "Protected User"
                    }
                """.trimIndent())
        ).andReturn()

        val accessToken = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(registerResult.response.contentAsString)["tokens"]["accessToken"].asText()

        // Access protected endpoint
        mockMvc.perform(
            get("/api/v1/users/me")
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isOk)
    }
}
