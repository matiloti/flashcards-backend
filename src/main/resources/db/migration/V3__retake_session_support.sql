-- Flashcards App Database Migration: Retake Session Support
-- Version: V3
-- Description: Add retake session tracking columns to study_sessions table
--
-- This migration supports the Retake Study Session feature (US-RS-01 through US-RS-05)
-- which allows users to retake study sessions with all cards or only missed cards.
--
-- Changes:
-- 1. Add parent_session_id column for tracking retake lineage
-- 2. Add retake_type column for distinguishing session types
-- 3. Add index on parent_session_id for efficient lookups

-- ============================================================================
-- MIGRATION: V3__retake_session_support.sql
-- ============================================================================

-- Add parent_session_id column to track retake lineage
-- - NULL: Original session (not a retake)
-- - UUID: Points to the session this was retaken from
-- ON DELETE SET NULL: If original session is deleted, retake remains but loses link
ALTER TABLE study_sessions
    ADD COLUMN parent_session_id UUID REFERENCES study_sessions(id) ON DELETE SET NULL;

-- Add retake_type column to distinguish retake types
-- - NULL: Original session (not a retake)
-- - 'ALL_CARDS': Retake with all cards from the deck
-- - 'MISSED_ONLY': Retake with only Hard/Again cards from parent session
ALTER TABLE study_sessions
    ADD COLUMN retake_type VARCHAR(20);

-- Constraint to enforce valid retake_type values
-- NULL is allowed for original sessions
ALTER TABLE study_sessions
    ADD CONSTRAINT chk_retake_type
    CHECK (retake_type IS NULL OR retake_type IN ('ALL_CARDS', 'MISSED_ONLY'));

-- Partial index for finding all retakes of a specific session
-- Useful for: analytics, session history views, cascade operations
CREATE INDEX idx_study_sessions_parent_session_id ON study_sessions(parent_session_id)
    WHERE parent_session_id IS NOT NULL;
