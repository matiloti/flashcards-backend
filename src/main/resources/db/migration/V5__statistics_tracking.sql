-- V5__statistics_tracking.sql
-- Adds comprehensive statistics tracking for the Statistics Page feature
--
-- Epic: ST (Statistics Page)
-- Stories: US-ST-01 through US-ST-10
-- Author: kotlin-spring-boot-backend-developer
-- Date: 2026-01-27
--
-- Changes:
-- 1. Enhance study_sessions table with card counts (session_type already exists from V2)
-- 2. Create card_progress table for mastery tracking
-- 3. Create user_statistics table for denormalized cumulative stats
-- 4. Create daily_study_stats table for efficient streak and daily queries
-- 5. Backfill existing data from card_reviews
-- 6. Create all necessary indexes

-- ============================================================================
-- 1. ENHANCE STUDY_SESSIONS TABLE
-- ============================================================================

-- Add columns for session statistics
-- These are denormalized for efficient queries without JOIN to card_reviews
-- Note: session_type already exists from V2__flash_review_decks.sql
ALTER TABLE study_sessions
ADD COLUMN cards_studied INTEGER DEFAULT 0,
ADD COLUMN cards_easy INTEGER DEFAULT 0,
ADD COLUMN cards_hard INTEGER DEFAULT 0,
ADD COLUMN cards_again INTEGER DEFAULT 0;

-- Add CHECK constraints for non-negative counts
ALTER TABLE study_sessions
ADD CONSTRAINT chk_cards_studied_non_negative CHECK (cards_studied >= 0),
ADD CONSTRAINT chk_cards_easy_non_negative CHECK (cards_easy >= 0),
ADD CONSTRAINT chk_cards_hard_non_negative CHECK (cards_hard >= 0),
ADD CONSTRAINT chk_cards_again_non_negative CHECK (cards_again >= 0);

COMMENT ON COLUMN study_sessions.cards_studied IS 'Total cards reviewed in this session';
COMMENT ON COLUMN study_sessions.cards_easy IS 'Count of EASY ratings in this session';
COMMENT ON COLUMN study_sessions.cards_hard IS 'Count of HARD ratings in this session';
COMMENT ON COLUMN study_sessions.cards_again IS 'Count of AGAIN ratings in this session';

-- ============================================================================
-- 2. CREATE CARD_PROGRESS TABLE
-- ============================================================================

-- Tracks mastery state for each card
-- One row per card, updated on each review
CREATE TABLE card_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id UUID NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
    consecutive_easy_count INTEGER DEFAULT 0 NOT NULL,
    total_reviews INTEGER DEFAULT 0 NOT NULL,
    total_easy INTEGER DEFAULT 0 NOT NULL,
    total_hard INTEGER DEFAULT 0 NOT NULL,
    total_again INTEGER DEFAULT 0 NOT NULL,
    last_rating VARCHAR(10),
    last_reviewed_at TIMESTAMP WITH TIME ZONE,
    mastery_level VARCHAR(20) DEFAULT 'NEW' NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,

    -- Ensure one entry per card
    CONSTRAINT uq_card_progress_card_id UNIQUE (card_id),

    -- Validate rating values
    CONSTRAINT chk_last_rating CHECK (last_rating IS NULL OR last_rating IN ('EASY', 'HARD', 'AGAIN')),

    -- Validate mastery level
    CONSTRAINT chk_mastery_level CHECK (mastery_level IN ('NEW', 'LEARNING', 'MASTERED')),

    -- Validate non-negative counts
    CONSTRAINT chk_consecutive_easy_non_negative CHECK (consecutive_easy_count >= 0),
    CONSTRAINT chk_total_reviews_non_negative CHECK (total_reviews >= 0),
    CONSTRAINT chk_total_easy_non_negative CHECK (total_easy >= 0),
    CONSTRAINT chk_total_hard_non_negative CHECK (total_hard >= 0),
    CONSTRAINT chk_total_again_non_negative CHECK (total_again >= 0)
);

COMMENT ON TABLE card_progress IS 'Tracks learning progress and mastery level for each flashcard';
COMMENT ON COLUMN card_progress.consecutive_easy_count IS 'Consecutive EASY ratings; resets on HARD/AGAIN';
COMMENT ON COLUMN card_progress.total_reviews IS 'Lifetime count of reviews for this card';
COMMENT ON COLUMN card_progress.mastery_level IS 'NEW=never reviewed, LEARNING=reviewed but not mastered, MASTERED=3+ consecutive EASY';
COMMENT ON COLUMN card_progress.last_rating IS 'Most recent rating for this card';
COMMENT ON COLUMN card_progress.last_reviewed_at IS 'Timestamp of most recent review';

-- Indexes for card_progress
CREATE INDEX idx_card_progress_card_id ON card_progress(card_id);
CREATE INDEX idx_card_progress_mastery_level ON card_progress(mastery_level);
CREATE INDEX idx_card_progress_last_reviewed_at ON card_progress(last_reviewed_at DESC NULLS LAST);

-- ============================================================================
-- 3. CREATE USER_STATISTICS TABLE
-- ============================================================================

-- Denormalized cumulative statistics for fast reads
-- For single-user app without auth, we use a single row with a sentinel ID
-- When auth is added, add user_id column and make it part of unique constraint
CREATE TABLE user_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- user_id UUID REFERENCES users(id), -- Future: uncomment when auth is added
    current_streak INTEGER DEFAULT 0 NOT NULL,
    longest_streak INTEGER DEFAULT 0 NOT NULL,
    last_study_date DATE,
    total_cards_studied INTEGER DEFAULT 0 NOT NULL,
    total_study_time_minutes INTEGER DEFAULT 0 NOT NULL,
    total_sessions INTEGER DEFAULT 0 NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,

    -- Validate non-negative values
    CONSTRAINT chk_current_streak_non_negative CHECK (current_streak >= 0),
    CONSTRAINT chk_longest_streak_non_negative CHECK (longest_streak >= 0),
    CONSTRAINT chk_total_cards_studied_non_negative CHECK (total_cards_studied >= 0),
    CONSTRAINT chk_total_study_time_minutes_non_negative CHECK (total_study_time_minutes >= 0),
    CONSTRAINT chk_total_sessions_non_negative CHECK (total_sessions >= 0)
);

COMMENT ON TABLE user_statistics IS 'Denormalized cumulative statistics for fast retrieval. Single row for now (no auth).';
COMMENT ON COLUMN user_statistics.current_streak IS 'Consecutive days studied up to and including last_study_date';
COMMENT ON COLUMN user_statistics.longest_streak IS 'Longest streak ever achieved';
COMMENT ON COLUMN user_statistics.last_study_date IS 'Calendar date (UTC) of most recent completed session';
COMMENT ON COLUMN user_statistics.total_cards_studied IS 'Cumulative count of card reviews';
COMMENT ON COLUMN user_statistics.total_study_time_minutes IS 'Cumulative study duration in minutes';
COMMENT ON COLUMN user_statistics.total_sessions IS 'Cumulative count of completed sessions';

-- ============================================================================
-- 4. CREATE DAILY_STUDY_STATS TABLE
-- ============================================================================

-- Daily aggregated statistics for efficient weekly queries and streak calculation
-- One row per day that has study activity
CREATE TABLE daily_study_stats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- user_id UUID REFERENCES users(id), -- Future: uncomment when auth is added
    study_date DATE NOT NULL,
    cards_studied INTEGER DEFAULT 0 NOT NULL,
    time_minutes INTEGER DEFAULT 0 NOT NULL,
    sessions_completed INTEGER DEFAULT 0 NOT NULL,
    easy_count INTEGER DEFAULT 0 NOT NULL,
    hard_count INTEGER DEFAULT 0 NOT NULL,
    again_count INTEGER DEFAULT 0 NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,

    -- One row per date (will add user_id when auth is added)
    CONSTRAINT uq_daily_study_stats_date UNIQUE (study_date),

    -- Validate non-negative values
    CONSTRAINT chk_daily_cards_studied_non_negative CHECK (cards_studied >= 0),
    CONSTRAINT chk_daily_time_minutes_non_negative CHECK (time_minutes >= 0),
    CONSTRAINT chk_daily_sessions_non_negative CHECK (sessions_completed >= 0),
    CONSTRAINT chk_daily_easy_non_negative CHECK (easy_count >= 0),
    CONSTRAINT chk_daily_hard_non_negative CHECK (hard_count >= 0),
    CONSTRAINT chk_daily_again_non_negative CHECK (again_count >= 0)
);

COMMENT ON TABLE daily_study_stats IS 'Daily aggregated statistics for streak calculation and weekly views';
COMMENT ON COLUMN daily_study_stats.study_date IS 'Calendar date in UTC';
COMMENT ON COLUMN daily_study_stats.cards_studied IS 'Total cards reviewed on this day';
COMMENT ON COLUMN daily_study_stats.time_minutes IS 'Total study time on this day in minutes';
COMMENT ON COLUMN daily_study_stats.sessions_completed IS 'Number of completed sessions on this day';

-- Indexes for daily_study_stats
CREATE INDEX idx_daily_study_stats_date ON daily_study_stats(study_date DESC);
-- Note: Partial index with CURRENT_DATE is not allowed (non-immutable function)
-- Queries for recent data will use idx_daily_study_stats_date with range condition

-- ============================================================================
-- 5. INDEXES FOR EXISTING TABLES (Statistics queries)
-- ============================================================================

-- Index for querying sessions by completion date (for weekly stats if not using daily_study_stats)
CREATE INDEX idx_study_sessions_completed_at_date ON study_sessions(DATE(completed_at AT TIME ZONE 'UTC'))
WHERE completed_at IS NOT NULL;

-- Index for card_reviews by date (for accuracy calculation)
CREATE INDEX idx_card_reviews_reviewed_at ON card_reviews(reviewed_at DESC);
CREATE INDEX idx_card_reviews_reviewed_at_date ON card_reviews(DATE(reviewed_at AT TIME ZONE 'UTC'));

-- ============================================================================
-- 6. BACKFILL EXISTING DATA
-- ============================================================================

-- 6a. Backfill study_sessions card counts from card_reviews
UPDATE study_sessions s
SET
    cards_studied = (
        SELECT COUNT(*)
        FROM card_reviews cr
        WHERE cr.session_id = s.id
    ),
    cards_easy = (
        SELECT COUNT(*)
        FROM card_reviews cr
        WHERE cr.session_id = s.id AND cr.rating = 'EASY'
    ),
    cards_hard = (
        SELECT COUNT(*)
        FROM card_reviews cr
        WHERE cr.session_id = s.id AND cr.rating = 'HARD'
    ),
    cards_again = (
        SELECT COUNT(*)
        FROM card_reviews cr
        WHERE cr.session_id = s.id AND cr.rating = 'AGAIN'
    )
WHERE s.completed_at IS NOT NULL;

-- 6b. Populate card_progress from card_reviews
-- Insert one row per card that has been reviewed
INSERT INTO card_progress (card_id, total_reviews, total_easy, total_hard, total_again, last_rating, last_reviewed_at, mastery_level, consecutive_easy_count)
SELECT
    cr.card_id,
    COUNT(*) as total_reviews,
    COUNT(*) FILTER (WHERE cr.rating = 'EASY') as total_easy,
    COUNT(*) FILTER (WHERE cr.rating = 'HARD') as total_hard,
    COUNT(*) FILTER (WHERE cr.rating = 'AGAIN') as total_again,
    (
        SELECT rating
        FROM card_reviews cr2
        WHERE cr2.card_id = cr.card_id
        ORDER BY cr2.reviewed_at DESC
        LIMIT 1
    ) as last_rating,
    MAX(cr.reviewed_at) as last_reviewed_at,
    -- Mastery level determined later
    'LEARNING' as mastery_level,
    -- Consecutive easy count requires ordered processing, default to 0 for now
    0 as consecutive_easy_count
FROM card_reviews cr
WHERE EXISTS (SELECT 1 FROM cards c WHERE c.id = cr.card_id)
GROUP BY cr.card_id
ON CONFLICT (card_id) DO NOTHING;

-- 6c. Calculate consecutive_easy_count for each card
-- This is a complex calculation requiring window functions
-- For each card, find the longest trailing streak of EASY ratings
WITH ordered_reviews AS (
    SELECT
        card_id,
        rating,
        reviewed_at,
        ROW_NUMBER() OVER (PARTITION BY card_id ORDER BY reviewed_at DESC) as rn
    FROM card_reviews
),
easy_streaks AS (
    SELECT
        card_id,
        COUNT(*) as consecutive_easy
    FROM ordered_reviews
    WHERE rating = 'EASY'
      AND rn <= (
          SELECT COALESCE(MIN(rn) - 1, (SELECT COUNT(*) FROM ordered_reviews o2 WHERE o2.card_id = ordered_reviews.card_id))
          FROM ordered_reviews o
          WHERE o.card_id = ordered_reviews.card_id
            AND o.rating != 'EASY'
      )
    GROUP BY card_id
)
UPDATE card_progress cp
SET
    consecutive_easy_count = COALESCE(es.consecutive_easy, 0),
    mastery_level = CASE
        WHEN COALESCE(es.consecutive_easy, 0) >= 3 THEN 'MASTERED'
        ELSE 'LEARNING'
    END,
    updated_at = NOW()
FROM easy_streaks es
WHERE cp.card_id = es.card_id;

-- 6d. Populate daily_study_stats from study_sessions
INSERT INTO daily_study_stats (study_date, cards_studied, time_minutes, sessions_completed, easy_count, hard_count, again_count)
SELECT
    DATE(s.completed_at AT TIME ZONE 'UTC') as study_date,
    SUM(s.cards_studied) as cards_studied,
    SUM(EXTRACT(EPOCH FROM (s.completed_at - s.started_at)) / 60)::INTEGER as time_minutes,
    COUNT(*) as sessions_completed,
    SUM(s.cards_easy) as easy_count,
    SUM(s.cards_hard) as hard_count,
    SUM(s.cards_again) as again_count
FROM study_sessions s
WHERE s.completed_at IS NOT NULL
  AND s.started_at IS NOT NULL
GROUP BY DATE(s.completed_at AT TIME ZONE 'UTC')
ON CONFLICT (study_date) DO UPDATE SET
    cards_studied = EXCLUDED.cards_studied,
    time_minutes = EXCLUDED.time_minutes,
    sessions_completed = EXCLUDED.sessions_completed,
    easy_count = EXCLUDED.easy_count,
    hard_count = EXCLUDED.hard_count,
    again_count = EXCLUDED.again_count,
    updated_at = NOW();

-- 6e. Populate user_statistics with a single row
-- Calculate all cumulative stats from existing data
INSERT INTO user_statistics (
    id,
    current_streak,
    longest_streak,
    last_study_date,
    total_cards_studied,
    total_study_time_minutes,
    total_sessions
)
SELECT
    '00000000-0000-0000-0000-000000000001'::UUID as id,
    COALESCE(streak.current_streak, 0) as current_streak,
    COALESCE(streak.longest_streak, 0) as longest_streak,
    MAX(d.study_date) as last_study_date,
    COALESCE(SUM(d.cards_studied), 0) as total_cards_studied,
    COALESCE(SUM(d.time_minutes), 0) as total_study_time_minutes,
    COALESCE(SUM(d.sessions_completed), 0) as total_sessions
FROM daily_study_stats d
CROSS JOIN LATERAL (
    -- Calculate current and longest streak
    -- This is a gaps-and-islands problem
    SELECT
        -- Current streak: count consecutive days ending today or yesterday
        (
            SELECT COUNT(*)
            FROM (
                SELECT study_date,
                       study_date - (ROW_NUMBER() OVER (ORDER BY study_date DESC))::INTEGER as grp
                FROM daily_study_stats
                WHERE study_date >= CURRENT_DATE - INTERVAL '365 days'
            ) grouped
            WHERE grp = (
                SELECT study_date - 1
                FROM daily_study_stats
                WHERE study_date <= CURRENT_DATE
                ORDER BY study_date DESC
                LIMIT 1
            ) - 1
              OR (study_date = CURRENT_DATE AND grp = CURRENT_DATE - 1)
        ) as current_streak,
        -- Longest streak (simplified: just use current for now, proper calculation in app)
        (
            SELECT COUNT(*)
            FROM (
                SELECT study_date,
                       study_date - (ROW_NUMBER() OVER (ORDER BY study_date DESC))::INTEGER as grp
                FROM daily_study_stats
            ) grouped
            GROUP BY grp
            ORDER BY COUNT(*) DESC
            LIMIT 1
        ) as longest_streak
) streak
GROUP BY streak.current_streak, streak.longest_streak
ON CONFLICT (id) DO UPDATE SET
    current_streak = EXCLUDED.current_streak,
    longest_streak = EXCLUDED.longest_streak,
    last_study_date = EXCLUDED.last_study_date,
    total_cards_studied = EXCLUDED.total_cards_studied,
    total_study_time_minutes = EXCLUDED.total_study_time_minutes,
    total_sessions = EXCLUDED.total_sessions,
    updated_at = NOW();

-- If no study history exists, ensure we still have a user_statistics row
INSERT INTO user_statistics (id)
VALUES ('00000000-0000-0000-0000-000000000001'::UUID)
ON CONFLICT (id) DO NOTHING;
