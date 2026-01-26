-- V2__flash_review_decks.sql
-- Adds support for Flash Review Decks feature
--
-- Changes:
-- 1. Add deck_type column to decks table
-- 2. Make back_text nullable in cards table (for Flash Review cards without notes)
-- 3. Add session_type column to study_sessions table
-- 4. Add concepts_viewed column to study_sessions for flash review tracking
-- 5. Create indexes for new columns used in WHERE clauses

-- ============================================================================
-- 1. ADD DECK TYPE TO DECKS TABLE
-- ============================================================================

-- Add deck_type column with default 'STUDY' (backwards compatible)
-- Existing decks will automatically become STUDY type
ALTER TABLE decks
ADD COLUMN deck_type VARCHAR(20) NOT NULL DEFAULT 'STUDY';

-- Add CHECK constraint to enforce valid values
ALTER TABLE decks
ADD CONSTRAINT chk_deck_type CHECK (deck_type IN ('STUDY', 'FLASH_REVIEW'));

-- Add index for filtering by deck_type (common query pattern)
CREATE INDEX idx_decks_deck_type ON decks(deck_type);

-- ============================================================================
-- 2. MAKE BACK_TEXT NULLABLE FOR FLASH REVIEW CARDS
-- ============================================================================

-- Flash Review cards may not have notes (backText is optional)
-- This allows NULL in back_text column
-- Study decks enforce back_text NOT NULL at application layer
ALTER TABLE cards
ALTER COLUMN back_text DROP NOT NULL;

-- ============================================================================
-- 3. ADD SESSION TYPE TO STUDY_SESSIONS TABLE
-- ============================================================================

-- Add session_type to distinguish STUDY sessions from FLASH_REVIEW sessions
-- Existing sessions will become 'STUDY' type (backwards compatible)
ALTER TABLE study_sessions
ADD COLUMN session_type VARCHAR(20) NOT NULL DEFAULT 'STUDY';

-- Add CHECK constraint to enforce valid values
ALTER TABLE study_sessions
ADD CONSTRAINT chk_session_type CHECK (session_type IN ('STUDY', 'FLASH_REVIEW'));

-- ============================================================================
-- 4. ADD CONCEPTS_VIEWED FOR FLASH REVIEW SESSION TRACKING
-- ============================================================================

-- Track how many concepts were viewed in flash review sessions
-- NULL for study sessions (they track individual card_reviews instead)
ALTER TABLE study_sessions
ADD COLUMN concepts_viewed INTEGER;

-- Add CHECK constraint: concepts_viewed must be non-negative if set
ALTER TABLE study_sessions
ADD CONSTRAINT chk_concepts_viewed_non_negative CHECK (concepts_viewed IS NULL OR concepts_viewed >= 0);

-- ============================================================================
-- 5. CREATE INDEXES FOR PERFORMANCE
-- ============================================================================

-- Index for filtering sessions by type (common query pattern)
CREATE INDEX idx_study_sessions_session_type ON study_sessions(session_type);

-- Composite index for finding sessions by deck and type
CREATE INDEX idx_study_sessions_deck_type ON study_sessions(deck_id, session_type);
