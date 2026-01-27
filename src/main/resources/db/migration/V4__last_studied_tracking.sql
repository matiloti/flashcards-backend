-- V4__last_studied_tracking.sql
-- Adds last_studied_at tracking for decks to support Home Screen Redesign
--
-- Epic: HS (Home Screen Redesign)
-- Stories: US-HS-02 (Continue Studying), US-HS-03 (Deck List sorting)
-- Author: kotlin-spring-boot-backend-developer
-- Date: 2026-01-27
--
-- Changes:
-- 1. Add last_studied_at column to decks table
-- 2. Populate existing data from completed study sessions
-- 3. Create index for efficient querying

-- ============================================================================
-- 1. ADD last_studied_at COLUMN TO DECKS TABLE
-- ============================================================================

-- Add nullable column (NULL = never studied)
ALTER TABLE decks
ADD COLUMN last_studied_at TIMESTAMP WITH TIME ZONE;

-- Note: No DEFAULT value - new decks should have NULL until first study session

COMMENT ON COLUMN decks.last_studied_at IS
'Timestamp of most recent completed study or flash review session. NULL if never studied.';

-- ============================================================================
-- 2. BACKFILL EXISTING DATA FROM STUDY SESSIONS
-- ============================================================================

-- For each deck, set last_studied_at to the most recent completed session
-- This ensures existing decks with study history show correctly in "Continue Studying"

UPDATE decks d
SET last_studied_at = (
    SELECT MAX(s.completed_at)
    FROM study_sessions s
    WHERE s.deck_id = d.id
      AND s.completed_at IS NOT NULL
);

-- ============================================================================
-- 3. CREATE INDEXES FOR QUERY PERFORMANCE
-- ============================================================================

-- Index for sorting decks by last studied (most recent first)
-- Supports: ORDER BY last_studied_at DESC NULLS LAST
-- Supports: WHERE last_studied_at IS NOT NULL (for /decks/recent endpoint)
CREATE INDEX idx_decks_last_studied_at ON decks(last_studied_at DESC NULLS LAST);

-- Partial index for recently studied decks (excludes NULL values)
-- More efficient for /decks/recent endpoint as it only indexes non-null values
CREATE INDEX idx_decks_last_studied_at_not_null
ON decks(last_studied_at DESC)
WHERE last_studied_at IS NOT NULL;

-- ============================================================================
-- VERIFICATION QUERIES (for testing, not executed in migration)
-- ============================================================================

-- Verify column was added:
-- SELECT column_name, data_type, is_nullable
-- FROM information_schema.columns
-- WHERE table_name = 'decks' AND column_name = 'last_studied_at';

-- Verify backfill worked:
-- SELECT d.id, d.name, d.last_studied_at,
--        (SELECT MAX(completed_at) FROM study_sessions WHERE deck_id = d.id) as expected
-- FROM decks d;

-- Verify index exists:
-- SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'decks';

-- ============================================================================
-- ROLLBACK SCRIPT (for reference, not executed)
-- ============================================================================
--
-- DROP INDEX IF EXISTS idx_decks_last_studied_at_not_null;
-- DROP INDEX IF EXISTS idx_decks_last_studied_at;
-- ALTER TABLE decks DROP COLUMN IF EXISTS last_studied_at;
