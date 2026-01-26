package com.flashcards.card

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
class CardControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private val objectMapper = ObjectMapper()

    private lateinit var deckId: String

    @BeforeEach
    fun setup() {
        jdbcTemplate.execute("DELETE FROM card_reviews")
        jdbcTemplate.execute("DELETE FROM study_sessions")
        jdbcTemplate.execute("DELETE FROM cards")
        jdbcTemplate.execute("DELETE FROM decks")

        val result = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Test Deck"}""")
        ).andReturn()

        deckId = objectMapper.readTree(result.response.contentAsString)["id"].asText()
    }

    @Test
    fun `list cards returns empty list for new deck`() {
        mockMvc.perform(get("/api/v1/decks/$deckId/cards"))
            .andExpect(status().isOk)
            .andExpect(content().json("[]"))
    }

    @Test
    fun `list cards returns 404 for non-existent deck`() {
        mockMvc.perform(get("/api/v1/decks/00000000-0000-0000-0000-000000000000/cards"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `create card returns 201`() {
        mockMvc.perform(
            post("/api/v1/decks/$deckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "What is DNA?", "backText": "Deoxyribonucleic acid"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.frontText").value("What is DNA?"))
            .andExpect(jsonPath("$.backText").value("Deoxyribonucleic acid"))
            .andExpect(jsonPath("$.deckId").value(deckId))
    }

    @Test
    fun `create card with blank front text returns 400`() {
        mockMvc.perform(
            post("/api/v1/decks/$deckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "  ", "backText": "Answer"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `create card for non-existent deck returns 404`() {
        mockMvc.perform(
            post("/api/v1/decks/00000000-0000-0000-0000-000000000000/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "Q", "backText": "A"}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `create card with empty backText returns 400`() {
        mockMvc.perform(
            post("/api/v1/decks/$deckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "Question without answer?", "backText": ""}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `get card by id returns card`() {
        val createResult = mockMvc.perform(
            post("/api/v1/decks/$deckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "Q1", "backText": "A1"}""")
        ).andReturn()

        val cardId = objectMapper.readTree(createResult.response.contentAsString)["id"].asText()

        mockMvc.perform(get("/api/v1/decks/$deckId/cards/$cardId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.frontText").value("Q1"))
    }

    @Test
    fun `update card returns updated card`() {
        val createResult = mockMvc.perform(
            post("/api/v1/decks/$deckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "Old Q", "backText": "Old A"}""")
        ).andReturn()

        val cardId = objectMapper.readTree(createResult.response.contentAsString)["id"].asText()

        mockMvc.perform(
            put("/api/v1/decks/$deckId/cards/$cardId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "New Q", "backText": "New A"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.frontText").value("New Q"))
            .andExpect(jsonPath("$.backText").value("New A"))
    }

    @Test
    fun `update card with empty backText returns 400`() {
        val createResult = mockMvc.perform(
            post("/api/v1/decks/$deckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "Question", "backText": "Answer"}""")
        ).andReturn()

        val cardId = objectMapper.readTree(createResult.response.contentAsString)["id"].asText()

        mockMvc.perform(
            put("/api/v1/decks/$deckId/cards/$cardId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "Question", "backText": ""}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `delete card returns 204`() {
        val createResult = mockMvc.perform(
            post("/api/v1/decks/$deckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "To Delete", "backText": "Answer"}""")
        ).andReturn()

        val cardId = objectMapper.readTree(createResult.response.contentAsString)["id"].asText()

        mockMvc.perform(delete("/api/v1/decks/$deckId/cards/$cardId"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `delete non-existent card returns 404`() {
        mockMvc.perform(delete("/api/v1/decks/$deckId/cards/00000000-0000-0000-0000-000000000000"))
            .andExpect(status().isNotFound)
    }

    // ==================== Deck Type Card Validation Tests ====================

    @Test
    fun `create card in STUDY deck without backText returns 400`() {
        // Default deck is STUDY type
        mockMvc.perform(
            post("/api/v1/decks/$deckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "Question without answer"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `create card in FLASH_REVIEW deck without backText returns 201`() {
        // Create a Flash Review deck
        val flashDeckResult = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Flash Review Deck", "type": "FLASH_REVIEW"}""")
        ).andReturn()
        val flashDeckId = objectMapper.readTree(flashDeckResult.response.contentAsString)["id"].asText()

        mockMvc.perform(
            post("/api/v1/decks/$flashDeckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "Two Pointers Pattern"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.frontText").value("Two Pointers Pattern"))
            .andExpect(jsonPath("$.backText").isEmpty)
    }

    @Test
    fun `create card in FLASH_REVIEW deck with backText (notes) returns 201`() {
        // Create a Flash Review deck
        val flashDeckResult = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Flash Review Deck", "type": "FLASH_REVIEW"}""")
        ).andReturn()
        val flashDeckId = objectMapper.readTree(flashDeckResult.response.contentAsString)["id"].asText()

        mockMvc.perform(
            post("/api/v1/decks/$flashDeckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "Sliding Window", "backText": "Array technique for contiguous subarrays"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.frontText").value("Sliding Window"))
            .andExpect(jsonPath("$.backText").value("Array technique for contiguous subarrays"))
    }

    @Test
    fun `update FLASH_REVIEW card to remove backText returns 200`() {
        // Create a Flash Review deck
        val flashDeckResult = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Flash Review Deck", "type": "FLASH_REVIEW"}""")
        ).andReturn()
        val flashDeckId = objectMapper.readTree(flashDeckResult.response.contentAsString)["id"].asText()

        // Create card with notes
        val cardResult = mockMvc.perform(
            post("/api/v1/decks/$flashDeckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "BFS", "backText": "Level order traversal"}""")
        ).andReturn()
        val cardId = objectMapper.readTree(cardResult.response.contentAsString)["id"].asText()

        // Update to remove notes
        mockMvc.perform(
            put("/api/v1/decks/$flashDeckId/cards/$cardId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "BFS"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.frontText").value("BFS"))
            .andExpect(jsonPath("$.backText").isEmpty)
    }

    @Test
    fun `update STUDY card to remove backText returns 400`() {
        // Create card in default STUDY deck
        val cardResult = mockMvc.perform(
            post("/api/v1/decks/$deckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "Question", "backText": "Answer"}""")
        ).andReturn()
        val cardId = objectMapper.readTree(cardResult.response.contentAsString)["id"].asText()

        // Try to remove backText
        mockMvc.perform(
            put("/api/v1/decks/$deckId/cards/$cardId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "Question"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `list cards in FLASH_REVIEW deck includes null backText`() {
        // Create a Flash Review deck
        val flashDeckResult = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Flash Review Deck", "type": "FLASH_REVIEW"}""")
        ).andReturn()
        val flashDeckId = objectMapper.readTree(flashDeckResult.response.contentAsString)["id"].asText()

        // Create card without notes
        mockMvc.perform(
            post("/api/v1/decks/$flashDeckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "No Notes Concept"}""")
        )

        // List cards
        mockMvc.perform(get("/api/v1/decks/$flashDeckId/cards"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].frontText").value("No Notes Concept"))
            .andExpect(jsonPath("$[0].backText").isEmpty)
    }
}
