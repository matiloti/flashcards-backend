-- V7__tags.sql
-- Deck Organization: Tags feature
-- Adds tags table and deck_tags junction table for many-to-many relationship

-- =============================================================================
-- TAGS TABLE
-- =============================================================================
-- Stores user-created tags for organizing decks.
-- Tags are user-scoped: each user has their own set of tags.
-- Tag names are unique per user (case-insensitive uniqueness enforced in app).

CREATE TABLE tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Tag name: 1-30 characters, trimmed, stored as-is (preserves original case)
    name VARCHAR(30) NOT NULL,

    -- User ownership (prepared for multi-user support)
    -- Currently single-user: all tags belong to the implicit default user
    -- When auth is added, this column will reference the users table
    -- For now, using a fixed UUID as placeholder for single-user mode
    user_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001',

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Unique constraint: one tag name per user (case-sensitive at DB level)
    -- Application enforces case-insensitive uniqueness via LOWER(name) check
    CONSTRAINT uq_tags_user_name UNIQUE (user_id, name)
);

-- Index for listing tags by user (sorted by name for alphabetical listing)
CREATE INDEX idx_tags_user_id ON tags(user_id);

-- Index for case-insensitive name lookup within a user's tags
-- Used when checking for duplicate names during create/update
CREATE INDEX idx_tags_user_name_lower ON tags(user_id, LOWER(name));

COMMENT ON TABLE tags IS 'User-created tags for organizing decks';
COMMENT ON COLUMN tags.user_id IS 'Owner user ID. Placeholder for single-user mode.';
COMMENT ON COLUMN tags.name IS 'Tag display name. Max 30 chars. Case preserved.';

-- =============================================================================
-- DECK_TAGS JUNCTION TABLE
-- =============================================================================
-- Many-to-many relationship between decks and tags.
-- A deck can have up to 5 tags (enforced at application level).
-- A tag can be applied to many decks.

CREATE TABLE deck_tags (
    deck_id UUID NOT NULL REFERENCES decks(id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,

    -- Timestamp when the tag was added to the deck
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Composite primary key ensures no duplicate deck-tag pairs
    PRIMARY KEY (deck_id, tag_id)
);

-- Index for finding all tags for a deck (used in deck detail, deck list)
-- Covered query: SELECT tag_id FROM deck_tags WHERE deck_id = ?
CREATE INDEX idx_deck_tags_deck_id ON deck_tags(deck_id);

-- Index for finding all decks with a specific tag (used in filtering)
-- Covered query: SELECT deck_id FROM deck_tags WHERE tag_id = ?
CREATE INDEX idx_deck_tags_tag_id ON deck_tags(tag_id);

COMMENT ON TABLE deck_tags IS 'Junction table for deck-tag many-to-many relationship';
COMMENT ON COLUMN deck_tags.created_at IS 'When the tag was added to the deck';

-- =============================================================================
-- HELPER FUNCTION: COUNT DECKS PER TAG
-- =============================================================================
-- This function can be used in queries to get deck count per tag efficiently.
-- However, for performance, we'll compute this with a JOIN in the repository.

-- Note: deck_count is computed dynamically via LEFT JOIN, not stored.
-- This avoids synchronization issues when decks are deleted or tags removed.

-- =============================================================================
-- QUERY PATTERNS
-- =============================================================================

-- List tags with deck count (for tag management screen):
-- SELECT t.id, t.name, t.created_at, COUNT(dt.deck_id) as deck_count
-- FROM tags t
-- LEFT JOIN deck_tags dt ON t.id = dt.tag_id
-- WHERE t.user_id = ?
-- GROUP BY t.id
-- ORDER BY t.name ASC;

-- List decks with their tags (for deck list screen):
-- SELECT d.*,
--        COALESCE(array_agg(t.id) FILTER (WHERE t.id IS NOT NULL), '{}') as tag_ids,
--        COALESCE(array_agg(t.name) FILTER (WHERE t.id IS NOT NULL), '{}') as tag_names
-- FROM decks d
-- LEFT JOIN deck_tags dt ON d.id = dt.deck_id
-- LEFT JOIN tags t ON dt.tag_id = t.id
-- GROUP BY d.id
-- ORDER BY d.updated_at DESC;

-- Filter decks by tag:
-- SELECT d.*,
--        COALESCE(array_agg(t.id) FILTER (WHERE t.id IS NOT NULL), '{}') as tag_ids,
--        COALESCE(array_agg(t.name) FILTER (WHERE t.id IS NOT NULL), '{}') as tag_names
-- FROM decks d
-- INNER JOIN deck_tags dt_filter ON d.id = dt_filter.deck_id AND dt_filter.tag_id = ?
-- LEFT JOIN deck_tags dt ON d.id = dt.deck_id
-- LEFT JOIN tags t ON dt.tag_id = t.id
-- GROUP BY d.id;

-- Filter untagged decks:
-- SELECT d.*
-- FROM decks d
-- LEFT JOIN deck_tags dt ON d.id = dt.deck_id
-- WHERE dt.deck_id IS NULL;

-- =============================================================================
-- PERFORMANCE NOTES
-- =============================================================================

-- 1. The idx_deck_tags_deck_id index ensures O(log n) lookup for tags per deck.
--    This is hit on every deck detail and deck list request.

-- 2. The idx_deck_tags_tag_id index ensures O(log n) lookup for decks per tag.
--    This is hit when filtering decks by tag.

-- 3. Deck count per tag is computed via COUNT aggregation, not denormalized.
--    With expected tag counts (5-20 per user) and deck counts (10-100 per user),
--    this aggregation is fast enough (<10ms) and avoids sync complexity.

-- 4. The idx_tags_user_name_lower index supports case-insensitive duplicate checks
--    during tag creation/update. Uses functional index on LOWER(name).

-- 5. CASCADE DELETE on both foreign keys ensures:
--    - When a deck is deleted, all its deck_tags rows are deleted
--    - When a tag is deleted, all its deck_tags rows are deleted
--    This maintains referential integrity without application intervention.

-- =============================================================================
-- CONSTRAINTS ENFORCED AT APPLICATION LEVEL
-- =============================================================================

-- 1. Maximum 5 tags per deck
--    Reason: Cannot be enforced with CHECK constraint on junction table.
--    Enforcement: Application validates before INSERT into deck_tags.

-- 2. Case-insensitive tag name uniqueness
--    Reason: Unique constraint is case-sensitive by default.
--    Enforcement: Application checks LOWER(name) before INSERT/UPDATE.

-- 3. Tag name trimming and validation (1-30 non-blank chars)
--    Reason: VARCHAR constraint doesn't handle trimming or blank-check.
--    Enforcement: Application trims and validates before INSERT/UPDATE.
