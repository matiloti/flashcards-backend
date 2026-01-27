package com.flashcards.statistics

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Complete statistics overview response for GET /api/v1/statistics/overview
 */
data class StatisticsOverviewResponse(
    val streak: StreakStats,
    val today: TodayStats,
    val week: WeekStats,
    val allTime: AllTimeStats,
    val cardProgress: CardProgressStats,
    val accuracy: AccuracyStats,
    val topDecks: List<DeckProgressStats>
)

/**
 * Streak statistics (current and longest streaks)
 */
data class StreakStats(
    val current: Int,
    val longest: Int,
    val lastStudyDate: LocalDate?
)

/**
 * Today's study summary
 */
data class TodayStats(
    val cardsStudied: Int,
    val timeMinutes: Int,
    val sessionsCompleted: Int
)

/**
 * Weekly study statistics
 */
data class WeekStats(
    val days: List<DayStats>,
    val totalCardsStudied: Int,
    val daysStudied: Int
)

/**
 * Single day study statistics
 */
data class DayStats(
    val date: LocalDate,
    val cardsStudied: Int,
    val studied: Boolean
)

/**
 * All-time cumulative statistics
 */
data class AllTimeStats(
    val totalCardsStudied: Int,
    val totalTimeMinutes: Int,
    val totalDecks: Int,
    val totalSessions: Int
)

/**
 * Card progress breakdown (mastered/learning/new)
 */
data class CardProgressStats(
    val mastered: Int,
    val learning: Int,
    val new: Int,
    val total: Int
)

/**
 * Accuracy statistics with trend
 */
data class AccuracyStats(
    val rate: Double?,
    val trend: AccuracyTrend?,
    val periodDays: Int,
    val easyCount: Int,
    val hardCount: Int,
    val againCount: Int
)

/**
 * Accuracy trend indicator
 */
enum class AccuracyTrend {
    IMPROVING,
    STABLE,
    DECLINING
}

/**
 * Individual deck progress statistics
 */
data class DeckProgressStats(
    val id: UUID,
    val name: String,
    val masteredCards: Int,
    val totalCards: Int,
    val progressPercent: Int,
    val lastStudiedAt: Instant?
)

/**
 * Mastery level for cards
 */
enum class MasteryLevel {
    NEW,
    LEARNING,
    MASTERED
}

/**
 * Internal model for user statistics from database
 */
data class UserStatistics(
    val id: UUID,
    val currentStreak: Int,
    val longestStreak: Int,
    val lastStudyDate: LocalDate?,
    val totalCardsStudied: Int,
    val totalStudyTimeMinutes: Int,
    val totalSessions: Int
)

/**
 * Internal model for daily stats from database
 */
data class DailyStudyStats(
    val studyDate: LocalDate,
    val cardsStudied: Int,
    val timeMinutes: Int,
    val sessionsCompleted: Int,
    val easyCount: Int,
    val hardCount: Int,
    val againCount: Int
)

/**
 * Error response for invalid timezone
 */
data class InvalidTimezoneError(
    val error: String,
    val validExample: String = "America/New_York"
)
