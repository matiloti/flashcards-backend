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
class StudyControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private val objectMapper = ObjectMapper()

    private lateinit var deckId: String
    private lateinit var cardId: String

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
                .content("""{"name": "Study Deck"}""")
        ).andReturn()
        deckId = objectMapper.readTree(deckResult.response.contentAsString)["id"].asText()

        // Create a card
        val cardResult = mockMvc.perform(
            post("/api/v1/decks/$deckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "Q1", "backText": "A1"}""")
        ).andReturn()
        cardId = objectMapper.readTree(cardResult.response.contentAsString)["id"].asText()
    }

    @Test
    fun `start session returns 201 with cards`() {
        mockMvc.perform(
            post("/api/v1/decks/$deckId/study")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.sessionId").isNotEmpty)
            .andExpect(jsonPath("$.deckName").value("Study Deck"))
            .andExpect(jsonPath("$.totalCards").value(1))
            .andExpect(jsonPath("$.cards[0].frontText").value("Q1"))
    }

    @Test
    fun `start session for non-existent deck returns 404`() {
        mockMvc.perform(
            post("/api/v1/decks/00000000-0000-0000-0000-000000000000/study")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `start session for empty deck returns 400`() {
        // Create empty deck
        val emptyDeckResult = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Empty Deck"}""")
        ).andReturn()
        val emptyDeckId = objectMapper.readTree(emptyDeckResult.response.contentAsString)["id"].asText()

        mockMvc.perform(
            post("/api/v1/decks/$emptyDeckId/study")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `submit review returns 201`() {
        // Start session
        val sessionResult = mockMvc.perform(
            post("/api/v1/decks/$deckId/study")
        ).andReturn()
        val sessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

        mockMvc.perform(
            post("/api/v1/study/$sessionId/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardId": "$cardId", "rating": "EASY"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.rating").value("EASY"))
            .andExpect(jsonPath("$.cardId").value(cardId))
    }

    @Test
    fun `submit review for non-existent session returns 404`() {
        mockMvc.perform(
            post("/api/v1/study/00000000-0000-0000-0000-000000000000/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardId": "$cardId", "rating": "HARD"}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `complete session returns summary`() {
        // Start session
        val sessionResult = mockMvc.perform(
            post("/api/v1/decks/$deckId/study")
        ).andReturn()
        val sessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

        // Submit review
        mockMvc.perform(
            post("/api/v1/study/$sessionId/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardId": "$cardId", "rating": "EASY"}""")
        )

        // Complete session
        mockMvc.perform(
            post("/api/v1/study/$sessionId/complete")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sessionId").value(sessionId))
            .andExpect(jsonPath("$.deckName").value("Study Deck"))
            .andExpect(jsonPath("$.easyCount").value(1))
            .andExpect(jsonPath("$.hardCount").value(0))
            .andExpect(jsonPath("$.againCount").value(0))
            .andExpect(jsonPath("$.totalCards").value(1))
    }

    @Test
    fun `complete non-existent session returns 404`() {
        mockMvc.perform(
            post("/api/v1/study/00000000-0000-0000-0000-000000000000/complete")
        )
            .andExpect(status().isNotFound)
    }
}
