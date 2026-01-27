package com.flashcards.deck

import com.fasterxml.jackson.databind.ObjectMapper
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
class DeckSearchControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private val objectMapper = ObjectMapper()

    private val sentinelUserId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeEach
    fun setup() {
        jdbcTemplate.execute("DELETE FROM card_reviews")
        jdbcTemplate.execute("DELETE FROM study_sessions")
        jdbcTemplate.execute("DELETE FROM cards")
        jdbcTemplate.execute("DELETE FROM decks")
    }

    // ==================== Basic Search Tests ====================

    @Test
    fun `search decks returns empty result when no decks exist`() {
        mockMvc.perform(get("/api/v1/decks/search?q=biology"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.content.length()").value(0))
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.totalPages").value(0))
    }

    @Test
    fun `search decks by name returns matching decks`() {
        // Create test decks
        createDeck("Biology 101", "Cell biology fundamentals")
        createDeck("Chemistry Basics", "Introduction to chemistry")
        createDeck("Advanced Biology", "Genetics and evolution")

        mockMvc.perform(get("/api/v1/decks/search?q=biology"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(2))
    }

    @Test
    fun `search is case insensitive`() {
        createDeck("Biology 101", null)

        // Search with different cases
        mockMvc.perform(get("/api/v1/decks/search?q=BIOLOGY"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))

        mockMvc.perform(get("/api/v1/decks/search?q=biology"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))

        mockMvc.perform(get("/api/v1/decks/search?q=BiOlOgY"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
    }

    @Test
    fun `search supports partial matching`() {
        createDeck("Biology 101", null)

        mockMvc.perform(get("/api/v1/decks/search?q=bio"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].name").value("Biology 101"))
    }

    // ==================== Description Search Tests ====================

    @Test
    fun `search matches description when name does not match`() {
        createDeck("Chemistry Terms", "Basic biology concepts and organic chemistry")

        mockMvc.perform(get("/api/v1/decks/search?q=biology"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].name").value("Chemistry Terms"))
            .andExpect(jsonPath("$.content[0].matchedField").value("description"))
    }

    @Test
    fun `search returns name match when query matches both name and description`() {
        createDeck("Biology 101", "Introduction to biology")

        mockMvc.perform(get("/api/v1/decks/search?q=biology"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].matchedField").value("name"))
    }

    // ==================== Relevance Ranking Tests ====================

    @Test
    fun `search results rank name matches before description matches`() {
        // Deck with match in description only
        createDeck("Chemistry Terms", "Basic biology concepts")
        // Deck with match in name
        createDeck("Biology 101", "Cell fundamentals")

        mockMvc.perform(get("/api/v1/decks/search?q=biology"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].name").value("Biology 101"))
            .andExpect(jsonPath("$.content[0].matchedField").value("name"))
            .andExpect(jsonPath("$.content[1].name").value("Chemistry Terms"))
            .andExpect(jsonPath("$.content[1].matchedField").value("description"))
    }

    @Test
    fun `search results within same match type are sorted alphabetically by name`() {
        createDeck("Zoology Studies", null)
        createDeck("Anatomy Basics", null)
        createDeck("Marine Biology", null)

        mockMvc.perform(get("/api/v1/decks/search?q=ology"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].name").value("Marine Biology"))
            .andExpect(jsonPath("$.content[1].name").value("Zoology Studies"))
    }

    // ==================== Pagination Tests ====================

    @Test
    fun `search returns paginated results`() {
        // Create 5 biology decks
        for (i in 1..5) {
            createDeck("Biology $i", null)
        }

        mockMvc.perform(get("/api/v1/decks/search?q=biology&page=0&size=2"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(5))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andExpect(jsonPath("$.number").value(0))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.first").value(true))
            .andExpect(jsonPath("$.last").value(false))
    }

    @Test
    fun `search returns second page correctly`() {
        // Create 5 biology decks
        for (i in 1..5) {
            createDeck("Biology $i", null)
        }

        mockMvc.perform(get("/api/v1/decks/search?q=biology&page=1&size=2"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.number").value(1))
            .andExpect(jsonPath("$.first").value(false))
            .andExpect(jsonPath("$.last").value(false))
    }

    @Test
    fun `search returns last page correctly`() {
        // Create 5 biology decks
        for (i in 1..5) {
            createDeck("Biology $i", null)
        }

        mockMvc.perform(get("/api/v1/decks/search?q=biology&page=2&size=2"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.number").value(2))
            .andExpect(jsonPath("$.first").value(false))
            .andExpect(jsonPath("$.last").value(true))
    }

    @Test
    fun `search default pagination is page 0 size 20`() {
        createDeck("Biology 101", null)

        mockMvc.perform(get("/api/v1/decks/search?q=biology"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.number").value(0))
            .andExpect(jsonPath("$.size").value(20))
    }

    // ==================== Validation Tests ====================

    @Test
    fun `search without query parameter returns 400`() {
        mockMvc.perform(get("/api/v1/decks/search"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("MISSING_QUERY"))
            .andExpect(jsonPath("$.message").value("Search query parameter 'q' is required"))
    }

    @Test
    fun `search with empty query returns 400`() {
        mockMvc.perform(get("/api/v1/decks/search?q="))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("MISSING_QUERY"))
    }

    @Test
    fun `search with query less than 2 characters returns 400`() {
        mockMvc.perform(get("/api/v1/decks/search?q=a"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("QUERY_TOO_SHORT"))
            .andExpect(jsonPath("$.message").value("Search query must be at least 2 characters"))
    }

    @Test
    fun `search with exactly 2 characters succeeds`() {
        createDeck("Go Programming", null)

        mockMvc.perform(get("/api/v1/decks/search?q=go"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
    }

    @Test
    fun `search with negative page returns 400`() {
        mockMvc.perform(get("/api/v1/decks/search?q=biology&page=-1"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INVALID_PAGINATION"))
    }

    @Test
    fun `search with size 0 returns 400`() {
        mockMvc.perform(get("/api/v1/decks/search?q=biology&size=0"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INVALID_PAGINATION"))
    }

    @Test
    fun `search with size exceeding 50 returns 400`() {
        mockMvc.perform(get("/api/v1/decks/search?q=biology&size=51"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INVALID_PAGINATION"))
    }

    @Test
    fun `search with size 50 succeeds`() {
        createDeck("Biology 101", null)

        mockMvc.perform(get("/api/v1/decks/search?q=biology&size=50"))
            .andExpect(status().isOk)
    }

    // ==================== Response Format Tests ====================

    @Test
    fun `search returns all required fields in response`() {
        val deckId = createDeck("Biology 101", "Cell biology fundamentals")

        // Add card to deck
        jdbcTemplate.update(
            "INSERT INTO cards (id, deck_id, front_text, back_text) VALUES (gen_random_uuid(), ?::uuid, 'Q', 'A')",
            deckId
        )

        // Set lastStudiedAt
        jdbcTemplate.update(
            "UPDATE decks SET last_studied_at = ? WHERE id = ?::uuid",
            java.sql.Timestamp.valueOf("2026-01-26 10:00:00"),
            deckId
        )

        mockMvc.perform(get("/api/v1/decks/search?q=biology"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].id").value(deckId))
            .andExpect(jsonPath("$.content[0].name").value("Biology 101"))
            .andExpect(jsonPath("$.content[0].description").value("Cell biology fundamentals"))
            .andExpect(jsonPath("$.content[0].type").value("STUDY"))
            .andExpect(jsonPath("$.content[0].cardCount").value(1))
            .andExpect(jsonPath("$.content[0].lastStudiedAt").exists())
            .andExpect(jsonPath("$.content[0].matchedField").value("name"))
            .andExpect(jsonPath("$.content[0].createdAt").exists())
            .andExpect(jsonPath("$.content[0].updatedAt").exists())
    }

    @Test
    fun `search returns null description when deck has no description`() {
        createDeck("Biology 101", null)

        mockMvc.perform(get("/api/v1/decks/search?q=biology"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].description").value(null as Any?))
    }

    @Test
    fun `search returns null lastStudiedAt when deck never studied`() {
        createDeck("Biology 101", null)

        mockMvc.perform(get("/api/v1/decks/search?q=biology"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].lastStudiedAt").value(null as Any?))
    }

    // ==================== Input Sanitization Tests ====================

    @Test
    fun `search trims query whitespace`() {
        createDeck("Biology 101", null)

        mockMvc.perform(get("/api/v1/decks/search?q=  biology  "))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
    }

    @Test
    fun `search with whitespace-only query returns 400`() {
        mockMvc.perform(get("/api/v1/decks/search?q=   "))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("MISSING_QUERY"))
    }

    // ==================== Edge Cases ====================

    @Test
    fun `search with no matches returns empty content with correct pagination`() {
        createDeck("Chemistry 101", null)

        mockMvc.perform(get("/api/v1/decks/search?q=biology"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.content.length()").value(0))
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.totalPages").value(0))
            .andExpect(jsonPath("$.first").value(true))
            .andExpect(jsonPath("$.last").value(true))
    }

    @Test
    fun `search includes decks of all types`() {
        createDeck("Biology Study", null, "STUDY")
        createDeck("Biology Flash Review", null, "FLASH_REVIEW")

        mockMvc.perform(get("/api/v1/decks/search?q=biology"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
    }

    // ==================== Helper Methods ====================

    private fun createDeck(name: String, description: String?, type: String = "STUDY"): String {
        val id = java.util.UUID.randomUUID().toString()
        jdbcTemplate.update(
            """
            INSERT INTO decks (id, name, description, deck_type, user_id, created_at, updated_at)
            VALUES (?::uuid, ?, ?, ?, ?, NOW(), NOW())
            """.trimIndent(),
            id, name, description, type, sentinelUserId
        )
        return id
    }
}
