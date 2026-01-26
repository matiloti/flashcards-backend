package com.flashcards.study

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
class StudyControllerRetakeTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private val objectMapper = ObjectMapper()

    private lateinit var deckId: String
    private lateinit var card1Id: String
    private lateinit var card2Id: String
    private lateinit var card3Id: String

    @BeforeEach
    fun setup() {
        jdbcTemplate.execute("DELETE FROM card_reviews")
        jdbcTemplate.execute("DELETE FROM study_sessions")
        jdbcTemplate.execute("DELETE FROM cards")
        jdbcTemplate.execute("DELETE FROM decks")

        // Create a deck
        val deckResult = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Retake Test Deck"}""")
        ).andReturn()
        deckId = objectMapper.readTree(deckResult.response.contentAsString)["id"].asText()

        // Create cards
        val card1Result = mockMvc.perform(
            post("/api/v1/decks/$deckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "Q1", "backText": "A1"}""")
        ).andReturn()
        card1Id = objectMapper.readTree(card1Result.response.contentAsString)["id"].asText()

        val card2Result = mockMvc.perform(
            post("/api/v1/decks/$deckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "Q2", "backText": "A2"}""")
        ).andReturn()
        card2Id = objectMapper.readTree(card2Result.response.contentAsString)["id"].asText()

        val card3Result = mockMvc.perform(
            post("/api/v1/decks/$deckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "Q3", "backText": "A3"}""")
        ).andReturn()
        card3Id = objectMapper.readTree(card3Result.response.contentAsString)["id"].asText()
    }

    private fun startAndCompleteSession(vararg ratings: Pair<String, String>): String {
        // Start session
        val sessionResult = mockMvc.perform(
            post("/api/v1/decks/$deckId/study")
        ).andReturn()
        val sessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

        // Submit reviews
        for ((cardId, rating) in ratings) {
            mockMvc.perform(
                post("/api/v1/study/$sessionId/reviews")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"cardId": "$cardId", "rating": "$rating"}""")
            )
        }

        // Complete session
        mockMvc.perform(post("/api/v1/study/$sessionId/complete"))

        return sessionId
    }

    // ==================== RETAKE MISSED ENDPOINT TESTS ====================

    @Test
    fun `retake missed returns 201 with only missed cards`() {
        // Complete a session with some missed cards (HARD, AGAIN ratings)
        val sessionId = startAndCompleteSession(
            card1Id to "EASY",
            card2Id to "HARD",
            card3Id to "AGAIN"
        )

        // Call retake-missed
        mockMvc.perform(
            post("/api/v1/study/$sessionId/retake-missed")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.sessionId").isNotEmpty)
            .andExpect(jsonPath("$.deckId").value(deckId))
            .andExpect(jsonPath("$.deckName").value("Retake Test Deck"))
            .andExpect(jsonPath("$.parentSessionId").value(sessionId))
            .andExpect(jsonPath("$.retakeType").value("MISSED_ONLY"))
            .andExpect(jsonPath("$.totalCards").value(2))  // Only HARD and AGAIN cards
            .andExpect(jsonPath("$.originalSessionCards").value(3))
            .andExpect(jsonPath("$.cards.length()").value(2))
    }

    @Test
    fun `retake missed returns 400 when session not completed`() {
        // Start session but don't complete it
        val sessionResult = mockMvc.perform(
            post("/api/v1/decks/$deckId/study")
        ).andReturn()
        val sessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

        mockMvc.perform(
            post("/api/v1/study/$sessionId/retake-missed")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Cannot retake: session is not completed"))
    }

    @Test
    fun `retake missed returns 400 when no missed cards`() {
        // Complete a session with all EASY ratings
        val sessionId = startAndCompleteSession(
            card1Id to "EASY",
            card2Id to "EASY",
            card3Id to "EASY"
        )

        mockMvc.perform(
            post("/api/v1/study/$sessionId/retake-missed")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Cannot retake: no cards were rated Hard or Again in this session"))
    }

    @Test
    fun `retake missed returns 404 when session not found`() {
        mockMvc.perform(
            post("/api/v1/study/00000000-0000-0000-0000-000000000000/retake-missed")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("Session not found"))
    }

    @Test
    fun `retake missed returns 404 when deck deleted`() {
        // Complete a session
        val sessionId = startAndCompleteSession(
            card1Id to "HARD",
            card2Id to "HARD",
            card3Id to "EASY"
        )

        // Delete the deck (CASCADE deletes the session too)
        mockMvc.perform(delete("/api/v1/decks/$deckId"))

        // Try to retake - session no longer exists due to CASCADE
        mockMvc.perform(
            post("/api/v1/study/$sessionId/retake-missed")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("Session not found"))
    }

    @Test
    fun `retake missed returns 400 when all missed cards deleted`() {
        // Complete a session
        val sessionId = startAndCompleteSession(
            card1Id to "EASY",
            card2Id to "HARD",   // This will be deleted
            card3Id to "EASY"
        )

        // Delete the missed card (CASCADE also deletes the card_review)
        mockMvc.perform(delete("/api/v1/decks/$deckId/cards/$card2Id"))

        // Try to retake - since card_reviews uses ON DELETE CASCADE,
        // the review for the deleted card is also removed, making it
        // indistinguishable from "no missed cards originally"
        mockMvc.perform(
            post("/api/v1/study/$sessionId/retake-missed")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Cannot retake: no cards were rated Hard or Again in this session"))
    }

    @Test
    fun `retake missed excludes deleted cards from session`() {
        // Complete a session with multiple missed cards
        val sessionId = startAndCompleteSession(
            card1Id to "EASY",
            card2Id to "HARD",   // This will be deleted
            card3Id to "AGAIN"   // This will remain
        )

        // Delete one of the missed cards
        mockMvc.perform(delete("/api/v1/decks/$deckId/cards/$card2Id"))

        // Retake - should only include card3
        mockMvc.perform(
            post("/api/v1/study/$sessionId/retake-missed")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.totalCards").value(1))
            .andExpect(jsonPath("$.cards.length()").value(1))
    }

    @Test
    fun `retake of a retake session works`() {
        // First session
        val sessionId1 = startAndCompleteSession(
            card1Id to "EASY",
            card2Id to "HARD",
            card3Id to "AGAIN"
        )

        // First retake
        val retake1Result = mockMvc.perform(
            post("/api/v1/study/$sessionId1/retake-missed")
        ).andReturn()
        val retakeSessionId = objectMapper.readTree(retake1Result.response.contentAsString)["sessionId"].asText()

        // Complete the retake session with one missed
        mockMvc.perform(
            post("/api/v1/study/$retakeSessionId/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardId": "$card2Id", "rating": "EASY"}""")
        )
        mockMvc.perform(
            post("/api/v1/study/$retakeSessionId/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardId": "$card3Id", "rating": "HARD"}""")
        )
        mockMvc.perform(post("/api/v1/study/$retakeSessionId/complete"))

        // Second retake (retake of retake)
        mockMvc.perform(
            post("/api/v1/study/$retakeSessionId/retake-missed")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.parentSessionId").value(retakeSessionId))
            .andExpect(jsonPath("$.totalCards").value(1))  // Only card3 rated HARD in retake
    }

    // ==================== ENHANCED SESSION COMPLETE TESTS ====================

    @Test
    fun `complete session returns missedCount`() {
        val sessionId = startAndCompleteSession(
            card1Id to "EASY",
            card2Id to "HARD",
            card3Id to "AGAIN"
        )

        // Complete returns the summary with missedCount
        // Since we completed in the helper, let's create a new session and complete it manually
        val sessionResult = mockMvc.perform(
            post("/api/v1/decks/$deckId/study")
        ).andReturn()
        val newSessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

        mockMvc.perform(
            post("/api/v1/study/$newSessionId/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardId": "$card1Id", "rating": "EASY"}""")
        )
        mockMvc.perform(
            post("/api/v1/study/$newSessionId/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardId": "$card2Id", "rating": "HARD"}""")
        )
        mockMvc.perform(
            post("/api/v1/study/$newSessionId/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardId": "$card3Id", "rating": "AGAIN"}""")
        )

        mockMvc.perform(
            post("/api/v1/study/$newSessionId/complete")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.missedCount").value(2))  // HARD + AGAIN
            .andExpect(jsonPath("$.easyCount").value(1))
            .andExpect(jsonPath("$.hardCount").value(1))
            .andExpect(jsonPath("$.againCount").value(1))
    }

    @Test
    fun `complete session returns parentSessionId and retakeType for retake sessions`() {
        // Complete original session
        val originalSessionId = startAndCompleteSession(
            card1Id to "EASY",
            card2Id to "HARD",
            card3Id to "AGAIN"
        )

        // Start a retake session
        val retakeResult = mockMvc.perform(
            post("/api/v1/study/$originalSessionId/retake-missed")
        ).andReturn()
        val retakeSessionId = objectMapper.readTree(retakeResult.response.contentAsString)["sessionId"].asText()

        // Review the cards in retake
        mockMvc.perform(
            post("/api/v1/study/$retakeSessionId/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardId": "$card2Id", "rating": "EASY"}""")
        )
        mockMvc.perform(
            post("/api/v1/study/$retakeSessionId/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardId": "$card3Id", "rating": "EASY"}""")
        )

        // Complete the retake session
        mockMvc.perform(
            post("/api/v1/study/$retakeSessionId/complete")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.parentSessionId").value(originalSessionId))
            .andExpect(jsonPath("$.retakeType").value("MISSED_ONLY"))
            .andExpect(jsonPath("$.missedCount").value(0))
    }

    @Test
    fun `complete session returns null for parentSessionId and retakeType for original sessions`() {
        val sessionResult = mockMvc.perform(
            post("/api/v1/decks/$deckId/study")
        ).andReturn()
        val sessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

        mockMvc.perform(
            post("/api/v1/study/$sessionId/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardId": "$card1Id", "rating": "EASY"}""")
        )

        mockMvc.perform(
            post("/api/v1/study/$sessionId/complete")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.parentSessionId").doesNotExist())
            .andExpect(jsonPath("$.retakeType").doesNotExist())
    }
}
