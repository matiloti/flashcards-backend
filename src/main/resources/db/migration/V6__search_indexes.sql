-- V6__search_indexes.sql
-- Search functionality indexes for deck search (Epic: SF)
-- Created: 2026-01-27

-- ============================================================================
-- Purpose: Add description column to decks table and GIN trigram indexes for
-- efficient ILIKE searches on deck name and description columns.
-- These indexes support the search functionality defined in US-SF-03 (Search
-- by Name) and US-SF-04 (Search by Description).
-- ============================================================================

-- ============================================================================
-- 1. ADD DESCRIPTION COLUMN TO DECKS TABLE
-- ============================================================================

-- Add description column (nullable, max 500 chars)
ALTER TABLE decks
ADD COLUMN description VARCHAR(500);

-- ============================================================================
-- 2. ENABLE PG_TRGM EXTENSION
-- ============================================================================

-- Enable pg_trgm extension for trigram-based indexes
-- This extension is required for gin_trgm_ops index operator class
-- Note: This is idempotent and will not fail if already enabled
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ============================================================================
-- 3. CREATE GIN TRIGRAM INDEXES
-- ============================================================================

-- Index: idx_decks_name_trgm
-- Purpose: Accelerate ILIKE searches on deck name
-- Query pattern: WHERE name ILIKE '%query%'
--
-- GIN (Generalized Inverted Index) with trigram ops splits the text into
-- 3-character sequences, allowing efficient substring matching.
--
-- Performance: Reduces ILIKE scan from O(n) to O(log n) for large datasets.
-- Without this index, searches on 1000+ decks would degrade significantly.
CREATE INDEX IF NOT EXISTS idx_decks_name_trgm
ON decks USING gin(name gin_trgm_ops);

-- Index: idx_decks_description_trgm
-- Purpose: Accelerate ILIKE searches on deck description
-- Query pattern: WHERE description ILIKE '%query%'
--
-- Note: description column may be NULL, but GIN indexes handle NULL correctly
-- (NULLs are simply not indexed, which is the desired behavior for search).
CREATE INDEX IF NOT EXISTS idx_decks_description_trgm
ON decks USING gin(description gin_trgm_ops);

-- ============================================================================
-- Index Comments (Documentation)
-- ============================================================================

COMMENT ON INDEX idx_decks_name_trgm IS 'GIN trigram index for ILIKE search on deck name (US-SF-03)';
COMMENT ON INDEX idx_decks_description_trgm IS 'GIN trigram index for ILIKE search on deck description (US-SF-04)';

-- ============================================================================
-- Verification query (for manual testing, not executed in migration):
--
-- EXPLAIN ANALYZE
-- SELECT d.id, d.name, d.description
-- FROM decks d
-- WHERE d.name ILIKE '%biology%'
--    OR d.description ILIKE '%biology%';
--
-- Expected: Should show "Bitmap Index Scan on idx_decks_name_trgm" and
-- "Bitmap Index Scan on idx_decks_description_trgm" in the query plan.
-- ============================================================================

-- ============================================================================
-- Rollback (if needed):
--
-- DROP INDEX IF EXISTS idx_decks_description_trgm;
-- DROP INDEX IF EXISTS idx_decks_name_trgm;
-- ALTER TABLE decks DROP COLUMN description;
-- -- Note: Do not drop pg_trgm extension as it may be used by other features
-- ============================================================================
