package com.flashcards.user

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
class UserControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setup() {
        jdbcTemplate.execute("DELETE FROM refresh_tokens WHERE user_id != '00000000-0000-0000-0000-000000000001'")
        jdbcTemplate.execute("DELETE FROM deck_tags")
        jdbcTemplate.execute("DELETE FROM tags WHERE user_id != '00000000-0000-0000-0000-000000000001'")
        jdbcTemplate.execute("DELETE FROM study_sessions")
        jdbcTemplate.execute("DELETE FROM cards")
        jdbcTemplate.execute("DELETE FROM decks WHERE user_id != '00000000-0000-0000-0000-000000000001'")
        jdbcTemplate.execute("DELETE FROM users WHERE id != '00000000-0000-0000-0000-000000000001'")
    }

    private fun registerAndGetToken(email: String, displayName: String): String {
        val result = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "$email",
                        "password": "password123",
                        "displayName": "$displayName"
                    }
                """.trimIndent())
        ).andReturn()

        return com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(result.response.contentAsString)["tokens"]["accessToken"].asText()
    }

    // =========================================================================
    // GET /api/v1/users/me tests
    // =========================================================================

    @Test
    fun `get profile returns user profile`() {
        val token = registerAndGetToken("profile@example.com", "Profile User")

        mockMvc.perform(
            get("/api/v1/users/me")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("profile@example.com"))
            .andExpect(jsonPath("$.displayName").value("Profile User"))
            .andExpect(jsonPath("$.emailVerified").value(false))
            .andExpect(jsonPath("$.createdAt").isNotEmpty)
            .andExpect(jsonPath("$.stats.deckCount").value(0))
            .andExpect(jsonPath("$.stats.cardCount").value(0))
            .andExpect(jsonPath("$.stats.totalStudyTimeMinutes").value(0))
            .andExpect(jsonPath("$.stats.currentStreak").value(0))
    }

    @Test
    fun `get profile returns 401 without token`() {
        mockMvc.perform(
            get("/api/v1/users/me")
                .header("X-Test-No-Auth", "true")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `get profile returns 401 with invalid token`() {
        mockMvc.perform(
            get("/api/v1/users/me")
                .header("Authorization", "Bearer invalid.jwt.token")
        )
            .andExpect(status().isUnauthorized)
    }

    // =========================================================================
    // PATCH /api/v1/users/me tests
    // =========================================================================

    @Test
    fun `update profile updates display name`() {
        val token = registerAndGetToken("update@example.com", "Original Name")

        mockMvc.perform(
            patch("/api/v1/users/me")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "displayName": "New Name"
                    }
                """.trimIndent())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value("New Name"))
    }

    @Test
    fun `update profile trims display name`() {
        val token = registerAndGetToken("updatetrim@example.com", "Original Name")

        mockMvc.perform(
            patch("/api/v1/users/me")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "displayName": "  Trimmed Name  "
                    }
                """.trimIndent())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value("Trimmed Name"))
    }

    @Test
    fun `update profile returns 400 for short display name`() {
        val token = registerAndGetToken("shortname@example.com", "Original Name")

        mockMvc.perform(
            patch("/api/v1/users/me")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "displayName": "X"
                    }
                """.trimIndent())
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INVALID_DISPLAY_NAME"))
    }

    @Test
    fun `update profile returns 400 for long display name`() {
        val token = registerAndGetToken("longname@example.com", "Original Name")
        val longName = "a".repeat(51)

        mockMvc.perform(
            patch("/api/v1/users/me")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "displayName": "$longName"
                    }
                """.trimIndent())
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INVALID_DISPLAY_NAME"))
    }

    @Test
    fun `update profile returns 400 for no updates`() {
        val token = registerAndGetToken("noupdate@example.com", "Original Name")

        mockMvc.perform(
            patch("/api/v1/users/me")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("NO_UPDATES"))
    }

    @Test
    fun `update profile returns 401 without token`() {
        mockMvc.perform(
            patch("/api/v1/users/me")
                .header("X-Test-No-Auth", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "displayName": "New Name"
                    }
                """.trimIndent())
        )
            .andExpect(status().isUnauthorized)
    }
}
