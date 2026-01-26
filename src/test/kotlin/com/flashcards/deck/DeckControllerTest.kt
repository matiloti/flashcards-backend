package com.flashcards.deck

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
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
class DeckControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setup() {
        jdbcTemplate.execute("DELETE FROM card_reviews")
        jdbcTemplate.execute("DELETE FROM study_sessions")
        jdbcTemplate.execute("DELETE FROM cards")
        jdbcTemplate.execute("DELETE FROM decks")
    }

    @Test
    fun `list decks returns empty list when no decks exist`() {
        mockMvc.perform(get("/api/v1/decks"))
            .andExpect(status().isOk)
            .andExpect(content().json("[]"))
    }

    @Test
    fun `create deck returns 201 with deck`() {
        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Biology 101"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Biology 101"))
            .andExpect(jsonPath("$.cardCount").value(0))
            .andExpect(jsonPath("$.id").isNotEmpty)
    }

    @Test
    fun `create deck with blank name returns 400`() {
        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "   "}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `create deck with name exceeding 100 chars returns 400`() {
        val longName = "a".repeat(101)
        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "$longName"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `get deck by id returns deck`() {
        val createResult = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Test Deck"}""")
        )
            .andExpect(status().isCreated)
            .andReturn()

        val id = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(createResult.response.contentAsString)["id"].asText()

        mockMvc.perform(get("/api/v1/decks/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Test Deck"))
    }

    @Test
    fun `get non-existent deck returns 404`() {
        mockMvc.perform(get("/api/v1/decks/00000000-0000-0000-0000-000000000000"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `update deck returns updated deck`() {
        val createResult = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Old Name"}""")
        )
            .andExpect(status().isCreated)
            .andReturn()

        val id = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(createResult.response.contentAsString)["id"].asText()

        mockMvc.perform(
            put("/api/v1/decks/$id")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "New Name"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("New Name"))
    }

    @Test
    fun `delete deck returns 204`() {
        val createResult = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "To Delete"}""")
        )
            .andExpect(status().isCreated)
            .andReturn()

        val id = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(createResult.response.contentAsString)["id"].asText()

        mockMvc.perform(delete("/api/v1/decks/$id"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `delete non-existent deck returns 404`() {
        mockMvc.perform(delete("/api/v1/decks/00000000-0000-0000-0000-000000000000"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `list decks returns created decks`() {
        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Deck 1"}""")
        )
        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Deck 2"}""")
        )

        mockMvc.perform(get("/api/v1/decks"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }
}
