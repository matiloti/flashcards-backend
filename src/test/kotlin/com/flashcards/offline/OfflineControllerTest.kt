package com.flashcards.offline

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.flashcards.withTestUser
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
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
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class OfflineControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())

    private val sentinelUserId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val otherUserId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private lateinit var deckId: String
    private lateinit var cardId1: String
    private lateinit var cardId2: String

    @BeforeEach
    fun setup() {
        // Clean up test data
        jdbcTemplate.execute("DELETE FROM card_reviews")
        jdbcTemplate.execute("DELETE FROM card_progress")
        jdbcTemplate.execute("DELETE FROM study_sessions")
        jdbcTemplate.execute("DELETE FROM cards")
        jdbcTemplate.execute("DELETE FROM decks")
        jdbcTemplate.execute("DELETE FROM daily_study_stats")

        // Ensure sentinel user_statistics row exists
        jdbcTemplate.update(
            """INSERT INTO user_statistics (id, user_id, current_streak, longest_streak, total_cards_studied,
               total_study_time_minutes, total_sessions) VALUES (?, ?, 0, 0, 0, 0, 0)
               ON CONFLICT (user_id) DO NOTHING""",
            sentinelUserId, sentinelUserId
        )

        // Create a test deck
        val deckResult = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Test Deck", "description": "Test Description"}""")
        ).andReturn()
        deckId = objectMapper.readTree(deckResult.response.contentAsString)["id"].asText()

        // Create test cards
        val card1Result = mockMvc.perform(
            post("/api/v1/decks/$deckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "Question 1", "backText": "Answer 1"}""")
        ).andReturn()
        cardId1 = objectMapper.readTree(card1Result.response.contentAsString)["id"].asText()

        val card2Result = mockMvc.perform(
            post("/api/v1/decks/$deckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "Question 2", "backText": "Answer 2"}""")
        ).andReturn()
        cardId2 = objectMapper.readTree(card2Result.response.contentAsString)["id"].asText()
    }

    // =========================================================================
    // GET /api/v1/decks/{deckId}/download Tests
    // =========================================================================

    @Nested
    inner class DownloadDeckTests {

        @Test
        fun `download returns 404 for non-existent deck`() {
            val nonExistentDeckId = UUID.randomUUID()

            mockMvc.perform(
                get("/api/v1/decks/$nonExistentDeckId/download")
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun `download returns 404 for deck not owned by user`() {
            // Create other user first
            jdbcTemplate.update(
                """INSERT INTO users (id, email, password_hash, display_name, email_verified)
                   VALUES (?::uuid, 'other@test.com', 'hash', 'Other User', false)
                   ON CONFLICT (id) DO NOTHING""",
                otherUserId.toString()
            )

            // Create deck owned by other user
            val otherDeckId = UUID.randomUUID()
            jdbcTemplate.update(
                """INSERT INTO decks (id, name, user_id, created_at, updated_at, deck_type)
                   VALUES (?::uuid, 'Other User Deck', ?::uuid, NOW(), NOW(), 'STUDY')""",
                otherDeckId.toString(), otherUserId.toString()
            )

            mockMvc.perform(
                get("/api/v1/decks/$otherDeckId/download")
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun `download returns full deck with all cards`() {
            // Set description directly in DB (CreateDeckRequest doesn't support description yet)
            jdbcTemplate.update(
                "UPDATE decks SET description = 'Test Description' WHERE id = ?::uuid",
                deckId
            )

            mockMvc.perform(
                get("/api/v1/decks/$deckId/download")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.deck.id").value(deckId))
                .andExpect(jsonPath("$.deck.name").value("Test Deck"))
                .andExpect(jsonPath("$.deck.description").value("Test Description"))
                .andExpect(jsonPath("$.deck.cardCount").value(2))
                .andExpect(jsonPath("$.cards").isArray)
                .andExpect(jsonPath("$.cards.length()").value(2))
                .andExpect(jsonPath("$.downloadedAt").isNotEmpty)
        }

        @Test
        fun `download includes card progress when cards have been reviewed`() {
            // Study the deck to create progress
            val sessionResult = mockMvc.perform(
                post("/api/v1/decks/$deckId/study")
            ).andReturn()
            val sessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

            // Submit review for card1
            mockMvc.perform(
                post("/api/v1/study/$sessionId/reviews")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"cardId": "$cardId1", "rating": "EASY"}""")
            )

            // Complete session
            mockMvc.perform(post("/api/v1/study/$sessionId/complete"))

            // Download and verify progress
            mockMvc.perform(
                get("/api/v1/decks/$deckId/download")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.progress.$cardId1.lastRating").value("EASY"))
                .andExpect(jsonPath("$.progress.$cardId1.totalReviews").value(1))
                .andExpect(jsonPath("$.progress.$cardId1.lastReviewedAt").isNotEmpty)
        }

        @Test
        fun `download returns empty progress for cards never reviewed`() {
            mockMvc.perform(
                get("/api/v1/decks/$deckId/download")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.progress").isEmpty)
        }

        @Test
        fun `download returns deck version as ISO8601 timestamp`() {
            mockMvc.perform(
                get("/api/v1/decks/$deckId/download")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.deck.version").isNotEmpty)
        }
    }

    // =========================================================================
    // POST /api/v1/sync/study-progress Tests
    // =========================================================================

    @Nested
    inner class SyncStudyProgressTests {

        @Test
        fun `sync returns 400 for empty sessions array`() {
            val request = SyncStudyProgressRequest(
                clientId = "test-client",
                sessions = emptyList()
            )

            mockMvc.perform(
                post("/api/v1/sync/study-progress")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("EMPTY_SESSIONS"))
        }

        @Test
        fun `sync returns 400 when exceeding max sessions (50)`() {
            val sessions = (1..51).map { i ->
                OfflineStudySession(
                    clientSessionId = "session-$i",
                    deckId = UUID.fromString(deckId),
                    sessionType = "STUDY",
                    startedAt = Instant.now().minusSeconds(3600),
                    completedAt = Instant.now().minusSeconds(3500),
                    reviews = emptyList()
                )
            }

            val request = SyncStudyProgressRequest(
                clientId = "test-client",
                sessions = sessions
            )

            mockMvc.perform(
                post("/api/v1/sync/study-progress")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("TOO_MANY_SESSIONS"))
        }

        @Test
        fun `sync returns SKIPPED for duplicate session`() {
            val clientSessionId = "unique-session-${UUID.randomUUID()}"
            val request = SyncStudyProgressRequest(
                clientId = "test-client",
                sessions = listOf(
                    OfflineStudySession(
                        clientSessionId = clientSessionId,
                        deckId = UUID.fromString(deckId),
                        sessionType = "STUDY",
                        startedAt = Instant.now().minusSeconds(3600),
                        completedAt = Instant.now().minusSeconds(3500),
                        reviews = listOf(
                            OfflineCardReview(
                                cardId = UUID.fromString(cardId1),
                                rating = "EASY",
                                reviewedAt = Instant.now().minusSeconds(3550)
                            )
                        )
                    )
                )
            )

            // First sync - should succeed
            mockMvc.perform(
                post("/api/v1/sync/study-progress")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.results[0].status").value("SYNCED"))

            // Second sync with same clientSessionId - should be SKIPPED
            mockMvc.perform(
                post("/api/v1/sync/study-progress")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.results[0].status").value("SKIPPED"))
                .andExpect(jsonPath("$.summary.skipped").value(1))
        }

        @Test
        fun `sync returns FAILED for invalid deck`() {
            val nonExistentDeckId = UUID.randomUUID()
            val request = SyncStudyProgressRequest(
                clientId = "test-client",
                sessions = listOf(
                    OfflineStudySession(
                        clientSessionId = "session-invalid-deck",
                        deckId = nonExistentDeckId,
                        sessionType = "STUDY",
                        startedAt = Instant.now().minusSeconds(3600),
                        completedAt = Instant.now().minusSeconds(3500),
                        reviews = emptyList()
                    )
                )
            )

            mockMvc.perform(
                post("/api/v1/sync/study-progress")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.results[0].status").value("FAILED"))
                .andExpect(jsonPath("$.results[0].error").isNotEmpty)
                .andExpect(jsonPath("$.summary.failed").value(1))
        }

        @Test
        fun `sync handles partial success`() {
            val nonExistentDeckId = UUID.randomUUID()
            val request = SyncStudyProgressRequest(
                clientId = "test-client",
                sessions = listOf(
                    // Valid session
                    OfflineStudySession(
                        clientSessionId = "valid-session-${UUID.randomUUID()}",
                        deckId = UUID.fromString(deckId),
                        sessionType = "STUDY",
                        startedAt = Instant.now().minusSeconds(3600),
                        completedAt = Instant.now().minusSeconds(3500),
                        reviews = listOf(
                            OfflineCardReview(
                                cardId = UUID.fromString(cardId1),
                                rating = "EASY",
                                reviewedAt = Instant.now().minusSeconds(3550)
                            )
                        )
                    ),
                    // Invalid session (deck doesn't exist)
                    OfflineStudySession(
                        clientSessionId = "invalid-session-${UUID.randomUUID()}",
                        deckId = nonExistentDeckId,
                        sessionType = "STUDY",
                        startedAt = Instant.now().minusSeconds(3600),
                        completedAt = Instant.now().minusSeconds(3500),
                        reviews = emptyList()
                    )
                )
            )

            mockMvc.perform(
                post("/api/v1/sync/study-progress")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.summary.total").value(2))
                .andExpect(jsonPath("$.summary.synced").value(1))
                .andExpect(jsonPath("$.summary.failed").value(1))
        }

        @Test
        fun `sync returns 400 for timestamps in the future`() {
            val futureTime = Instant.now().plus(1, ChronoUnit.HOURS)
            val request = SyncStudyProgressRequest(
                clientId = "test-client",
                sessions = listOf(
                    OfflineStudySession(
                        clientSessionId = "future-session",
                        deckId = UUID.fromString(deckId),
                        sessionType = "STUDY",
                        startedAt = futureTime,
                        completedAt = futureTime.plusSeconds(60),
                        reviews = emptyList()
                    )
                )
            )

            mockMvc.perform(
                post("/api/v1/sync/study-progress")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.results[0].status").value("FAILED"))
                .andExpect(jsonPath("$.results[0].error").value(org.hamcrest.Matchers.containsString("future")))
        }

        @Test
        fun `sync returns FAILED for timestamps older than 30 days`() {
            val oldTime = Instant.now().minus(31, ChronoUnit.DAYS)
            val request = SyncStudyProgressRequest(
                clientId = "test-client",
                sessions = listOf(
                    OfflineStudySession(
                        clientSessionId = "old-session",
                        deckId = UUID.fromString(deckId),
                        sessionType = "STUDY",
                        startedAt = oldTime,
                        completedAt = oldTime.plusSeconds(60),
                        reviews = emptyList()
                    )
                )
            )

            mockMvc.perform(
                post("/api/v1/sync/study-progress")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.results[0].status").value("FAILED"))
                .andExpect(jsonPath("$.results[0].error").value(org.hamcrest.Matchers.containsString("30 days")))
        }

        @Test
        fun `sync returns FAILED for invalid rating`() {
            val request = SyncStudyProgressRequest(
                clientId = "test-client",
                sessions = listOf(
                    OfflineStudySession(
                        clientSessionId = "invalid-rating-session",
                        deckId = UUID.fromString(deckId),
                        sessionType = "STUDY",
                        startedAt = Instant.now().minusSeconds(3600),
                        completedAt = Instant.now().minusSeconds(3500),
                        reviews = listOf(
                            OfflineCardReview(
                                cardId = UUID.fromString(cardId1),
                                rating = "INVALID_RATING",
                                reviewedAt = Instant.now().minusSeconds(3550)
                            )
                        )
                    )
                )
            )

            mockMvc.perform(
                post("/api/v1/sync/study-progress")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.results[0].status").value("FAILED"))
        }

        @Test
        fun `sync successfully creates study session and reviews`() {
            val clientSessionId = "sync-test-${UUID.randomUUID()}"
            val request = SyncStudyProgressRequest(
                clientId = "test-client",
                sessions = listOf(
                    OfflineStudySession(
                        clientSessionId = clientSessionId,
                        deckId = UUID.fromString(deckId),
                        sessionType = "STUDY",
                        startedAt = Instant.now().minusSeconds(3600),
                        completedAt = Instant.now().minusSeconds(3500),
                        reviews = listOf(
                            OfflineCardReview(
                                cardId = UUID.fromString(cardId1),
                                rating = "EASY",
                                reviewedAt = Instant.now().minusSeconds(3550)
                            ),
                            OfflineCardReview(
                                cardId = UUID.fromString(cardId2),
                                rating = "HARD",
                                reviewedAt = Instant.now().minusSeconds(3520)
                            )
                        )
                    )
                )
            )

            mockMvc.perform(
                post("/api/v1/sync/study-progress")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.results[0].status").value("SYNCED"))
                .andExpect(jsonPath("$.results[0].serverSessionId").isNotEmpty)
                .andExpect(jsonPath("$.results[0].reviewsSynced").value(2))
                .andExpect(jsonPath("$.summary.synced").value(1))
                .andExpect(jsonPath("$.syncedAt").isNotEmpty)
        }

        @Test
        fun `sync handles card that no longer exists gracefully`() {
            val deletedCardId = UUID.randomUUID()
            val request = SyncStudyProgressRequest(
                clientId = "test-client",
                sessions = listOf(
                    OfflineStudySession(
                        clientSessionId = "deleted-card-session-${UUID.randomUUID()}",
                        deckId = UUID.fromString(deckId),
                        sessionType = "STUDY",
                        startedAt = Instant.now().minusSeconds(3600),
                        completedAt = Instant.now().minusSeconds(3500),
                        reviews = listOf(
                            OfflineCardReview(
                                cardId = UUID.fromString(cardId1),
                                rating = "EASY",
                                reviewedAt = Instant.now().minusSeconds(3550)
                            ),
                            OfflineCardReview(
                                cardId = deletedCardId,
                                rating = "HARD",
                                reviewedAt = Instant.now().minusSeconds(3520)
                            )
                        )
                    )
                )
            )

            // Session should still sync, but only with the existing card review
            mockMvc.perform(
                post("/api/v1/sync/study-progress")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.results[0].status").value("SYNCED"))
                .andExpect(jsonPath("$.results[0].reviewsSynced").value(1))
        }

        @Test
        fun `sync updates deck lastStudiedAt`() {
            val clientSessionId = "update-laststudied-${UUID.randomUUID()}"
            val completedAt = Instant.now().minusSeconds(100)
            val request = SyncStudyProgressRequest(
                clientId = "test-client",
                sessions = listOf(
                    OfflineStudySession(
                        clientSessionId = clientSessionId,
                        deckId = UUID.fromString(deckId),
                        sessionType = "STUDY",
                        startedAt = Instant.now().minusSeconds(200),
                        completedAt = completedAt,
                        reviews = listOf(
                            OfflineCardReview(
                                cardId = UUID.fromString(cardId1),
                                rating = "EASY",
                                reviewedAt = Instant.now().minusSeconds(150)
                            )
                        )
                    )
                )
            )

            // Sync the session
            mockMvc.perform(
                post("/api/v1/sync/study-progress")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.results[0].status").value("SYNCED"))

            // Verify deck's lastStudiedAt was updated
            mockMvc.perform(get("/api/v1/decks/$deckId"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.lastStudiedAt").isNotEmpty)
        }
    }
}
