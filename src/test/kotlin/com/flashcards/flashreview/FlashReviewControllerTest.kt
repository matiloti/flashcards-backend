package com.flashcards.flashreview

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
class FlashReviewControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private val objectMapper = ObjectMapper()

    private lateinit var flashReviewDeckId: String
    private lateinit var studyDeckId: String

    @BeforeEach
    fun setup() {
        jdbcTemplate.execute("DELETE FROM card_reviews")
        jdbcTemplate.execute("DELETE FROM study_sessions")
        jdbcTemplate.execute("DELETE FROM cards")
        jdbcTemplate.execute("DELETE FROM decks")

        // Create a Flash Review deck with cards
        val flashDeckResult = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "DSA Patterns", "type": "FLASH_REVIEW"}""")
        ).andReturn()
        flashReviewDeckId = objectMapper.readTree(flashDeckResult.response.contentAsString)["id"].asText()

        // Add cards to flash review deck
        mockMvc.perform(
            post("/api/v1/decks/$flashReviewDeckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "Two Pointers", "backText": "Array technique using left/right indices"}""")
        )
        mockMvc.perform(
            post("/api/v1/decks/$flashReviewDeckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "Sliding Window"}""")
        )

        // Create a Study deck
        val studyDeckResult = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Biology 101", "type": "STUDY"}""")
        ).andReturn()
        studyDeckId = objectMapper.readTree(studyDeckResult.response.contentAsString)["id"].asText()

        // Add card to study deck
        mockMvc.perform(
            post("/api/v1/decks/$studyDeckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "What is DNA?", "backText": "Deoxyribonucleic acid"}""")
        )
    }

    @Test
    fun `start flash review on FLASH_REVIEW deck returns 201 with concepts`() {
        mockMvc.perform(
            post("/api/v1/decks/$flashReviewDeckId/flash-review")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.sessionId").isNotEmpty)
            .andExpect(jsonPath("$.deckId").value(flashReviewDeckId))
            .andExpect(jsonPath("$.deckName").value("DSA Patterns"))
            .andExpect(jsonPath("$.totalConcepts").value(2))
            .andExpect(jsonPath("$.concepts.length()").value(2))
            .andExpect(jsonPath("$.startedAt").isNotEmpty)
    }

    @Test
    fun `start flash review on STUDY deck returns 400`() {
        mockMvc.perform(
            post("/api/v1/decks/$studyDeckId/flash-review")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Cannot start flash review: deck type is STUDY. Use /study endpoint instead."))
    }

    @Test
    fun `start flash review on empty FLASH_REVIEW deck returns 400`() {
        // Create empty flash review deck
        val emptyDeckResult = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Empty Flash Deck", "type": "FLASH_REVIEW"}""")
        ).andReturn()
        val emptyDeckId = objectMapper.readTree(emptyDeckResult.response.contentAsString)["id"].asText()

        mockMvc.perform(
            post("/api/v1/decks/$emptyDeckId/flash-review")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Cannot start review: deck has no cards"))
    }

    @Test
    fun `start flash review on non-existent deck returns 404`() {
        mockMvc.perform(
            post("/api/v1/decks/00000000-0000-0000-0000-000000000000/flash-review")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `complete flash review session returns summary`() {
        // Start session
        val sessionResult = mockMvc.perform(
            post("/api/v1/decks/$flashReviewDeckId/flash-review")
        ).andReturn()
        val sessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

        // Complete session
        mockMvc.perform(
            post("/api/v1/flash-review/$sessionId/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"conceptsViewed": 2}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sessionId").value(sessionId))
            .andExpect(jsonPath("$.deckId").value(flashReviewDeckId))
            .andExpect(jsonPath("$.deckName").value("DSA Patterns"))
            .andExpect(jsonPath("$.totalConcepts").value(2))
            .andExpect(jsonPath("$.conceptsViewed").value(2))
            .andExpect(jsonPath("$.startedAt").isNotEmpty)
            .andExpect(jsonPath("$.completedAt").isNotEmpty)
            .andExpect(jsonPath("$.durationSeconds").isNumber)
    }

    @Test
    fun `complete flash review session without request body defaults to all concepts`() {
        // Start session
        val sessionResult = mockMvc.perform(
            post("/api/v1/decks/$flashReviewDeckId/flash-review")
        ).andReturn()
        val sessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

        // Complete session without body
        mockMvc.perform(
            post("/api/v1/flash-review/$sessionId/complete")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.conceptsViewed").value(2)) // defaults to totalConcepts
    }

    @Test
    fun `complete already completed session returns 400`() {
        // Start session
        val sessionResult = mockMvc.perform(
            post("/api/v1/decks/$flashReviewDeckId/flash-review")
        ).andReturn()
        val sessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

        // Complete once
        mockMvc.perform(
            post("/api/v1/flash-review/$sessionId/complete")
        )
            .andExpect(status().isOk)

        // Try to complete again
        mockMvc.perform(
            post("/api/v1/flash-review/$sessionId/complete")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Session already completed"))
    }

    @Test
    fun `complete non-existent session returns 404`() {
        mockMvc.perform(
            post("/api/v1/flash-review/00000000-0000-0000-0000-000000000000/complete")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `get flash review session returns status`() {
        // Start session
        val sessionResult = mockMvc.perform(
            post("/api/v1/decks/$flashReviewDeckId/flash-review")
        ).andReturn()
        val sessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

        // Get session status
        mockMvc.perform(
            get("/api/v1/flash-review/$sessionId")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sessionId").value(sessionId))
            .andExpect(jsonPath("$.deckId").value(flashReviewDeckId))
            .andExpect(jsonPath("$.deckName").value("DSA Patterns"))
            .andExpect(jsonPath("$.totalConcepts").value(2))
            .andExpect(jsonPath("$.isCompleted").value(false))
            .andExpect(jsonPath("$.completedAt").isEmpty)
    }

    @Test
    fun `get completed flash review session shows completion status`() {
        // Start session
        val sessionResult = mockMvc.perform(
            post("/api/v1/decks/$flashReviewDeckId/flash-review")
        ).andReturn()
        val sessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

        // Complete session
        mockMvc.perform(
            post("/api/v1/flash-review/$sessionId/complete")
        )

        // Get session status
        mockMvc.perform(
            get("/api/v1/flash-review/$sessionId")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isCompleted").value(true))
            .andExpect(jsonPath("$.completedAt").isNotEmpty)
    }

    @Test
    fun `get non-existent session returns 404`() {
        mockMvc.perform(
            get("/api/v1/flash-review/00000000-0000-0000-0000-000000000000")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `concepts include hasNotes flag`() {
        mockMvc.perform(
            post("/api/v1/decks/$flashReviewDeckId/flash-review?shuffle=false")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.concepts[0].term").value("Two Pointers"))
            .andExpect(jsonPath("$.concepts[0].notes").value("Array technique using left/right indices"))
            .andExpect(jsonPath("$.concepts[0].hasNotes").value(true))
            .andExpect(jsonPath("$.concepts[1].term").value("Sliding Window"))
            .andExpect(jsonPath("$.concepts[1].notes").isEmpty)
            .andExpect(jsonPath("$.concepts[1].hasNotes").value(false))
    }

    @Test
    fun `start flash review with shuffle disabled returns concepts in order`() {
        // Run multiple times to verify non-shuffled order
        for (i in 1..3) {
            mockMvc.perform(
                post("/api/v1/decks/$flashReviewDeckId/flash-review?shuffle=false")
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.concepts[0].term").value("Two Pointers"))
                .andExpect(jsonPath("$.concepts[1].term").value("Sliding Window"))
        }
    }

    @Test
    fun `complete flash review session updates deck lastStudiedAt`() {
        // Verify deck has null lastStudiedAt initially
        mockMvc.perform(get("/api/v1/decks/$flashReviewDeckId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.lastStudiedAt").value(null as Any?))

        // Start session
        val sessionResult = mockMvc.perform(
            post("/api/v1/decks/$flashReviewDeckId/flash-review")
        ).andReturn()
        val sessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

        // Complete session
        val completeResult = mockMvc.perform(
            post("/api/v1/flash-review/$sessionId/complete")
        )
            .andExpect(status().isOk)
            .andReturn()

        val completedAt = objectMapper.readTree(completeResult.response.contentAsString)["completedAt"].asText()

        // Verify deck now has lastStudiedAt set to completedAt
        mockMvc.perform(get("/api/v1/decks/$flashReviewDeckId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.lastStudiedAt").value(completedAt))
    }
}
