package com.flashcards.statistics

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Repository for statistics-related database operations.
 */
@Repository
class StatisticsRepository(private val jdbcTemplate: JdbcTemplate) {

    /**
     * Get cumulative user statistics from the denormalized user_statistics table.
     */
    fun getUserStats(userId: UUID): UserStatistics {
        val results = jdbcTemplate.query(
            """SELECT id, user_id, current_streak, longest_streak, last_study_date,
               total_cards_studied, total_study_time_minutes, total_sessions
               FROM user_statistics WHERE user_id = ?""",
            { rs, _ ->
                UserStatistics(
                    id = UUID.fromString(rs.getString("id")),
                    currentStreak = rs.getInt("current_streak"),
                    longestStreak = rs.getInt("longest_streak"),
                    lastStudyDate = rs.getDate("last_study_date")?.toLocalDate(),
                    totalCardsStudied = rs.getInt("total_cards_studied"),
                    totalStudyTimeMinutes = rs.getInt("total_study_time_minutes"),
                    totalSessions = rs.getInt("total_sessions")
                )
            },
            userId
        )

        return results.firstOrNull() ?: UserStatistics(
            id = userId,
            currentStreak = 0,
            longestStreak = 0,
            lastStudyDate = null,
            totalCardsStudied = 0,
            totalStudyTimeMinutes = 0,
            totalSessions = 0
        )
    }

    /**
     * Get daily study stats for a date range.
     * Returns stats ordered by study_date ASC.
     */
    fun getDailyStats(userId: UUID, startDate: LocalDate, endDate: LocalDate, zoneId: ZoneId): List<DailyStudyStats> {
        return jdbcTemplate.query(
            """SELECT study_date, cards_studied, time_minutes, sessions_completed,
               easy_count, hard_count, again_count
               FROM daily_study_stats
               WHERE user_id = ? AND study_date >= ? AND study_date <= ?
               ORDER BY study_date ASC""",
            { rs, _ ->
                DailyStudyStats(
                    studyDate = rs.getDate("study_date").toLocalDate(),
                    cardsStudied = rs.getInt("cards_studied"),
                    timeMinutes = rs.getInt("time_minutes"),
                    sessionsCompleted = rs.getInt("sessions_completed"),
                    easyCount = rs.getInt("easy_count"),
                    hardCount = rs.getInt("hard_count"),
                    againCount = rs.getInt("again_count")
                )
            },
            userId,
            Date.valueOf(startDate),
            Date.valueOf(endDate)
        )
    }

    /**
     * Get card progress counts aggregated by mastery level for a user's cards.
     * Counts cards with no card_progress entry as NEW.
     */
    fun getCardProgressCounts(userId: UUID): CardProgressStats {
        // Get total cards count for user's decks
        val totalCards = jdbcTemplate.queryForObject(
            """SELECT COUNT(*) FROM cards c
               JOIN decks d ON c.deck_id = d.id
               WHERE d.user_id = ?""",
            Int::class.java,
            userId
        ) ?: 0

        // Get counts by mastery level from card_progress for user's cards
        val masteredCount = jdbcTemplate.queryForObject(
            """SELECT COUNT(*) FROM card_progress cp
               JOIN cards c ON cp.card_id = c.id
               JOIN decks d ON c.deck_id = d.id
               WHERE d.user_id = ? AND cp.mastery_level = 'MASTERED'""",
            Int::class.java,
            userId
        ) ?: 0

        val learningCount = jdbcTemplate.queryForObject(
            """SELECT COUNT(*) FROM card_progress cp
               JOIN cards c ON cp.card_id = c.id
               JOIN decks d ON c.deck_id = d.id
               WHERE d.user_id = ? AND cp.mastery_level = 'LEARNING'""",
            Int::class.java,
            userId
        ) ?: 0

        // NEW = total cards - cards with card_progress entries
        val cardsWithProgress = masteredCount + learningCount
        val newCount = totalCards - cardsWithProgress

        return CardProgressStats(
            mastered = masteredCount,
            learning = learningCount,
            new = newCount,
            total = totalCards
        )
    }

    /**
     * Get accuracy statistics for the specified period.
     * Calculates accuracy as (easy + hard) / (easy + hard + again).
     */
    fun getAccuracyStats(userId: UUID, periodDays: Int, zoneId: ZoneId): AccuracyStats {
        val cutoffDate = LocalDate.now(zoneId).minusDays(periodDays.toLong())

        val counts = jdbcTemplate.query(
            """SELECT cr.rating, COUNT(*) as cnt
               FROM card_reviews cr
               JOIN study_sessions ss ON cr.session_id = ss.id
               JOIN decks d ON ss.deck_id = d.id
               WHERE d.user_id = ? AND DATE(cr.reviewed_at AT TIME ZONE 'UTC') >= ?
               GROUP BY cr.rating""",
            { rs, _ ->
                rs.getString("rating") to rs.getInt("cnt")
            },
            userId,
            Date.valueOf(cutoffDate)
        ).toMap()

        val easyCount = counts["EASY"] ?: 0
        val hardCount = counts["HARD"] ?: 0
        val againCount = counts["AGAIN"] ?: 0
        val total = easyCount + hardCount + againCount

        val rate: Double? = if (total > 0) {
            (easyCount + hardCount).toDouble() / total
        } else {
            null
        }

        return AccuracyStats(
            rate = rate,
            trend = null, // Trend calculated by service layer
            periodDays = periodDays,
            easyCount = easyCount,
            hardCount = hardCount,
            againCount = againCount
        )
    }

    /**
     * Get accuracy stats for a specific date range (used for trend calculation).
     */
    fun getAccuracyStatsForRange(userId: UUID, startDate: LocalDate, endDate: LocalDate): AccuracyStats {
        val counts = jdbcTemplate.query(
            """SELECT cr.rating, COUNT(*) as cnt
               FROM card_reviews cr
               JOIN study_sessions ss ON cr.session_id = ss.id
               JOIN decks d ON ss.deck_id = d.id
               WHERE d.user_id = ?
                 AND DATE(cr.reviewed_at AT TIME ZONE 'UTC') >= ?
                 AND DATE(cr.reviewed_at AT TIME ZONE 'UTC') <= ?
               GROUP BY cr.rating""",
            { rs, _ ->
                rs.getString("rating") to rs.getInt("cnt")
            },
            userId,
            Date.valueOf(startDate),
            Date.valueOf(endDate)
        ).toMap()

        val easyCount = counts["EASY"] ?: 0
        val hardCount = counts["HARD"] ?: 0
        val againCount = counts["AGAIN"] ?: 0
        val total = easyCount + hardCount + againCount

        val rate: Double? = if (total > 0) {
            (easyCount + hardCount).toDouble() / total
        } else {
            null
        }

        return AccuracyStats(
            rate = rate,
            trend = null,
            periodDays = 0,
            easyCount = easyCount,
            hardCount = hardCount,
            againCount = againCount
        )
    }

    /**
     * Get top decks by most recent study activity for a user.
     * Only returns decks that have been studied (last_studied_at IS NOT NULL).
     */
    fun getTopDecks(userId: UUID, limit: Int): List<DeckProgressStats> {
        return jdbcTemplate.query(
            """SELECT d.id, d.name, d.last_studied_at,
                      COUNT(DISTINCT c.id) as total_cards,
                      COUNT(DISTINCT CASE WHEN cp.mastery_level = 'MASTERED' THEN c.id END) as mastered_cards
               FROM decks d
               LEFT JOIN cards c ON c.deck_id = d.id
               LEFT JOIN card_progress cp ON cp.card_id = c.id
               WHERE d.user_id = ? AND d.last_studied_at IS NOT NULL
               GROUP BY d.id, d.name, d.last_studied_at
               ORDER BY d.last_studied_at DESC
               LIMIT ?""",
            { rs, _ ->
                val totalCards = rs.getInt("total_cards")
                val masteredCards = rs.getInt("mastered_cards")
                val progressPercent = if (totalCards > 0) {
                    (masteredCards * 100) / totalCards
                } else {
                    0
                }

                DeckProgressStats(
                    id = UUID.fromString(rs.getString("id")),
                    name = rs.getString("name"),
                    masteredCards = masteredCards,
                    totalCards = totalCards,
                    progressPercent = progressPercent,
                    lastStudiedAt = rs.getTimestamp("last_studied_at")?.toInstant()
                )
            },
            userId, limit
        )
    }

    /**
     * Get total number of decks for a user.
     */
    fun getDeckCount(userId: UUID): Int {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM decks WHERE user_id = ?",
            Int::class.java,
            userId
        ) ?: 0
    }

    /**
     * Update cumulative user statistics.
     * Increments totals and updates streak if necessary.
     */
    fun updateUserStats(
        userId: UUID,
        cardsStudied: Int,
        studyTimeMinutes: Int,
        sessionsCompleted: Int,
        studyDate: LocalDate
    ) {
        jdbcTemplate.update(
            """UPDATE user_statistics
               SET total_cards_studied = total_cards_studied + ?,
                   total_study_time_minutes = total_study_time_minutes + ?,
                   total_sessions = total_sessions + ?,
                   last_study_date = ?,
                   updated_at = NOW()
               WHERE user_id = ?""",
            cardsStudied,
            studyTimeMinutes,
            sessionsCompleted,
            Date.valueOf(studyDate),
            userId
        )
    }

    /**
     * Update streak in user_statistics.
     */
    fun updateStreak(userId: UUID, currentStreak: Int, longestStreak: Int) {
        jdbcTemplate.update(
            """UPDATE user_statistics
               SET current_streak = ?,
                   longest_streak = GREATEST(longest_streak, ?),
                   updated_at = NOW()
               WHERE user_id = ?""",
            currentStreak,
            longestStreak,
            userId
        )
    }

    /**
     * Insert or update daily study stats for a given date.
     * Increments counts if entry already exists for the date.
     */
    fun upsertDailyStats(
        userId: UUID,
        studyDate: LocalDate,
        cardsStudied: Int,
        timeMinutes: Int,
        easyCount: Int,
        hardCount: Int,
        againCount: Int
    ) {
        jdbcTemplate.update(
            """INSERT INTO daily_study_stats
               (user_id, study_date, cards_studied, time_minutes, sessions_completed, easy_count, hard_count, again_count)
               VALUES (?, ?, ?, ?, 1, ?, ?, ?)
               ON CONFLICT (user_id, study_date) DO UPDATE SET
                   cards_studied = daily_study_stats.cards_studied + EXCLUDED.cards_studied,
                   time_minutes = daily_study_stats.time_minutes + EXCLUDED.time_minutes,
                   sessions_completed = daily_study_stats.sessions_completed + 1,
                   easy_count = daily_study_stats.easy_count + EXCLUDED.easy_count,
                   hard_count = daily_study_stats.hard_count + EXCLUDED.hard_count,
                   again_count = daily_study_stats.again_count + EXCLUDED.again_count,
                   updated_at = NOW()""",
            userId,
            Date.valueOf(studyDate),
            cardsStudied,
            timeMinutes,
            easyCount,
            hardCount,
            againCount
        )
    }

    /**
     * Update or create card progress entry for a card.
     */
    fun upsertCardProgress(
        cardId: UUID,
        rating: String,
        reviewedAt: Instant
    ) {
        // Determine new consecutive_easy_count and mastery_level
        val isEasy = rating == "EASY"

        jdbcTemplate.update(
            """INSERT INTO card_progress
               (card_id, consecutive_easy_count, total_reviews, total_easy, total_hard, total_again,
                last_rating, last_reviewed_at, mastery_level)
               VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?)
               ON CONFLICT (card_id) DO UPDATE SET
                   consecutive_easy_count = CASE
                       WHEN EXCLUDED.last_rating = 'EASY'
                       THEN card_progress.consecutive_easy_count + 1
                       ELSE 0
                   END,
                   total_reviews = card_progress.total_reviews + 1,
                   total_easy = card_progress.total_easy + EXCLUDED.total_easy,
                   total_hard = card_progress.total_hard + EXCLUDED.total_hard,
                   total_again = card_progress.total_again + EXCLUDED.total_again,
                   last_rating = EXCLUDED.last_rating,
                   last_reviewed_at = EXCLUDED.last_reviewed_at,
                   mastery_level = CASE
                       WHEN EXCLUDED.last_rating = 'EASY' AND card_progress.consecutive_easy_count + 1 >= 3
                       THEN 'MASTERED'
                       WHEN card_progress.total_reviews > 0 OR EXCLUDED.total_reviews > 0
                       THEN 'LEARNING'
                       ELSE 'NEW'
                   END,
                   updated_at = NOW()""",
            cardId,
            if (isEasy) 1 else 0,
            if (rating == "EASY") 1 else 0,
            if (rating == "HARD") 1 else 0,
            if (rating == "AGAIN") 1 else 0,
            rating,
            Timestamp.from(reviewedAt),
            "LEARNING"
        )
    }

    /**
     * Get all study dates in descending order (for streak calculation).
     */
    fun getStudyDates(userId: UUID): List<LocalDate> {
        return jdbcTemplate.query(
            "SELECT study_date FROM daily_study_stats WHERE user_id = ? ORDER BY study_date DESC",
            { rs, _ ->
                rs.getDate("study_date").toLocalDate()
            },
            userId
        )
    }

    /**
     * Ensure user_statistics row exists.
     */
    fun ensureUserStatsExists(userId: UUID) {
        jdbcTemplate.update(
            """INSERT INTO user_statistics (id, user_id, current_streak, longest_streak,
               total_cards_studied, total_study_time_minutes, total_sessions)
               VALUES (gen_random_uuid(), ?, 0, 0, 0, 0, 0)
               ON CONFLICT (user_id) DO NOTHING""",
            userId
        )
    }
}
