-- V8__users_auth.sql
-- User Authentication and Profile Support
--
-- Epic: AUTH (User Profile & Authentication)
-- Stories: US-AUTH-01 through US-AUTH-09 (MVP)
-- Author: kotlin-spring-postgres-architect
-- Date: 2026-01-27
--
-- Changes:
-- 1. Create users table for user accounts
-- 2. Create refresh_tokens table for JWT refresh token storage
-- 3. Add user_id to existing tables (decks, tags, user_statistics, daily_study_stats)
-- 4. Create default user and migrate existing data
-- 5. Add foreign key constraints
-- 6. Create all necessary indexes

-- ============================================================================
-- 1. USERS TABLE
-- ============================================================================
-- Core user account information for authentication and profile display.
-- Email serves as the unique login identifier.

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Login credentials
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,

    -- Profile information
    display_name VARCHAR(50) NOT NULL,

    -- Email verification (P3 feature, column added now for schema stability)
    email_verified BOOLEAN DEFAULT FALSE NOT NULL,

    -- Audit timestamps
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,

    -- Constraints
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_email_format CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_users_display_name_length CHECK (LENGTH(TRIM(display_name)) >= 2 AND LENGTH(display_name) <= 50)
);

-- Index for email lookup during login (already covered by unique constraint, but explicit for clarity)
CREATE INDEX idx_users_email ON users(email);

-- Index for listing users by creation date (admin/future features)
CREATE INDEX idx_users_created_at ON users(created_at DESC);

COMMENT ON TABLE users IS 'User accounts for authentication and profile management';
COMMENT ON COLUMN users.email IS 'Unique email address for login. Max 255 chars. Validated format.';
COMMENT ON COLUMN users.password_hash IS 'bcrypt hash of user password (cost factor 12)';
COMMENT ON COLUMN users.display_name IS 'User-chosen display name. 2-50 characters.';
COMMENT ON COLUMN users.email_verified IS 'Whether email has been verified (P3 feature)';

-- ============================================================================
-- 2. REFRESH_TOKENS TABLE
-- ============================================================================
-- Stores active refresh tokens for JWT session management.
-- Supports token rotation (old tokens revoked on refresh).
-- Enables logout from specific devices and "logout all" functionality.

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Token ownership
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Token value (opaque, randomly generated)
    -- Using VARCHAR(64) for a 256-bit hex-encoded token
    token VARCHAR(64) NOT NULL,

    -- Expiration and revocation
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN DEFAULT FALSE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,

    -- Device/session tracking (optional, for future "manage sessions" feature)
    device_info VARCHAR(255),
    ip_address VARCHAR(45), -- IPv6 max length

    -- Audit timestamps
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,

    -- Constraints
    CONSTRAINT uq_refresh_tokens_token UNIQUE (token)
);

-- Index for token lookup during refresh
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token) WHERE NOT revoked;

-- Index for finding user's active tokens (logout all, session management)
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id) WHERE NOT revoked;

-- Index for cleanup job to delete expired/revoked tokens
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at) WHERE NOT revoked;

-- Index for finding tokens by user that are not revoked and not expired
CREATE INDEX idx_refresh_tokens_user_active ON refresh_tokens(user_id, expires_at)
    WHERE NOT revoked;

COMMENT ON TABLE refresh_tokens IS 'Active refresh tokens for JWT session management';
COMMENT ON COLUMN refresh_tokens.token IS 'Opaque refresh token value. 64-char hex string (256-bit).';
COMMENT ON COLUMN refresh_tokens.expires_at IS 'Token expiration timestamp. Default 30 days from creation.';
COMMENT ON COLUMN refresh_tokens.revoked IS 'Whether token has been explicitly revoked (logout).';
COMMENT ON COLUMN refresh_tokens.revoked_at IS 'When the token was revoked (for audit trail).';
COMMENT ON COLUMN refresh_tokens.device_info IS 'User-Agent or device identifier (optional).';
COMMENT ON COLUMN refresh_tokens.ip_address IS 'IP address when token was created (optional).';

-- ============================================================================
-- 3. PASSWORD_RESET_TOKENS TABLE (P2 preparation)
-- ============================================================================
-- Created now for schema stability, will be used in P2 password reset feature.

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used BOOLEAN DEFAULT FALSE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,

    CONSTRAINT uq_password_reset_tokens_token UNIQUE (token)
);

CREATE INDEX idx_password_reset_tokens_token ON password_reset_tokens(token) WHERE NOT used;
CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens(user_id);

COMMENT ON TABLE password_reset_tokens IS 'Tokens for password reset flow (P2 feature)';

-- ============================================================================
-- 4. CREATE DEFAULT USER FOR EXISTING DATA
-- ============================================================================
-- All existing data needs to be associated with a user.
-- We create a "default" user that will own all pre-existing data.
-- This user uses the placeholder UUID that was used in tags table.

INSERT INTO users (
    id,
    email,
    password_hash,
    display_name,
    email_verified,
    created_at
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'default@flashcards.local',
    -- bcrypt hash of a random password (user cannot login with this)
    -- This is intentionally an invalid bcrypt hash to prevent login
    '$2a$12$INVALID.HASH.CANNOT.LOGIN.PLACEHOLDER',
    'Default User',
    FALSE,
    NOW()
) ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 5. ADD USER_ID TO DECKS TABLE
-- ============================================================================
-- Each deck belongs to exactly one user.

ALTER TABLE decks
ADD COLUMN user_id UUID REFERENCES users(id) ON DELETE CASCADE;

-- Migrate existing decks to default user
UPDATE decks SET user_id = '00000000-0000-0000-0000-000000000001' WHERE user_id IS NULL;

-- Make user_id NOT NULL after migration
ALTER TABLE decks ALTER COLUMN user_id SET NOT NULL;

-- Index for listing user's decks (most common query pattern)
CREATE INDEX idx_decks_user_id ON decks(user_id);

-- Composite index for user's decks sorted by updated_at
CREATE INDEX idx_decks_user_id_updated_at ON decks(user_id, updated_at DESC);

COMMENT ON COLUMN decks.user_id IS 'Owner of this deck. Required.';

-- ============================================================================
-- 6. UPDATE TAGS TABLE
-- ============================================================================
-- Tags table already has user_id with default placeholder.
-- Add proper foreign key constraint and update index.

-- Add foreign key constraint to existing user_id column
ALTER TABLE tags
ADD CONSTRAINT fk_tags_user_id
FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

COMMENT ON COLUMN tags.user_id IS 'Owner user ID. References users table.';

-- ============================================================================
-- 7. UPDATE USER_STATISTICS TABLE
-- ============================================================================
-- Add user_id column for multi-user support.
-- Migrate existing row to default user.

ALTER TABLE user_statistics
ADD COLUMN user_id UUID REFERENCES users(id) ON DELETE CASCADE;

-- Migrate existing statistics to default user
UPDATE user_statistics
SET user_id = '00000000-0000-0000-0000-000000000001'
WHERE user_id IS NULL;

-- Make user_id NOT NULL and add unique constraint
ALTER TABLE user_statistics ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE user_statistics ADD CONSTRAINT uq_user_statistics_user_id UNIQUE (user_id);

-- Index for looking up user's statistics
CREATE INDEX idx_user_statistics_user_id ON user_statistics(user_id);

COMMENT ON COLUMN user_statistics.user_id IS 'User who owns these statistics.';

-- ============================================================================
-- 8. UPDATE DAILY_STUDY_STATS TABLE
-- ============================================================================
-- Add user_id column for multi-user support.

ALTER TABLE daily_study_stats
ADD COLUMN user_id UUID REFERENCES users(id) ON DELETE CASCADE;

-- Migrate existing stats to default user
UPDATE daily_study_stats
SET user_id = '00000000-0000-0000-0000-000000000001'
WHERE user_id IS NULL;

-- Make user_id NOT NULL
ALTER TABLE daily_study_stats ALTER COLUMN user_id SET NOT NULL;

-- Update unique constraint to include user_id
ALTER TABLE daily_study_stats DROP CONSTRAINT uq_daily_study_stats_date;
ALTER TABLE daily_study_stats ADD CONSTRAINT uq_daily_study_stats_user_date UNIQUE (user_id, study_date);

-- Index for user's daily stats
CREATE INDEX idx_daily_study_stats_user_id ON daily_study_stats(user_id);
CREATE INDEX idx_daily_study_stats_user_date ON daily_study_stats(user_id, study_date DESC);

COMMENT ON COLUMN daily_study_stats.user_id IS 'User who owns these daily statistics.';

-- ============================================================================
-- 9. UPDATE CARD_PROGRESS TABLE (Optional: for future user isolation)
-- ============================================================================
-- Card progress is indirectly user-scoped through deck ownership.
-- No direct user_id needed since cards belong to decks which belong to users.
-- Query pattern: card_progress JOIN cards JOIN decks WHERE decks.user_id = ?

-- ============================================================================
-- 10. STUDY_SESSIONS - No changes needed
-- ============================================================================
-- Study sessions are scoped through deck_id.
-- deck_id -> decks -> user_id provides user isolation.
-- Query pattern: study_sessions JOIN decks WHERE decks.user_id = ?

-- ============================================================================
-- 11. ADDITIONAL INDEXES FOR USER-SCOPED QUERIES
-- ============================================================================

-- Composite index for finding user's recent study sessions via deck
CREATE INDEX idx_study_sessions_deck_completed ON study_sessions(deck_id, completed_at DESC)
    WHERE completed_at IS NOT NULL;

-- ============================================================================
-- MIGRATION NOTES
-- ============================================================================
--
-- 1. All existing data is migrated to the "default user" with a placeholder email.
--    This user cannot login (invalid password hash).
--
-- 2. When a real user registers, they start with no data.
--    There is no automatic migration of default user's data to new accounts.
--
-- 3. For development/testing, you may want to:
--    a) Delete the default user's data before going to production
--    b) Or provide an admin tool to transfer data to a real user
--
-- 4. All existing API endpoints need to be updated to:
--    a) Extract user_id from JWT token
--    b) Filter queries by user_id
--    c) Verify ownership before update/delete operations
--
-- 5. Performance consideration:
--    With user_id on decks, all queries that currently scan all decks
--    will now efficiently filter to only the authenticated user's decks.
--    This is actually a performance improvement for the common case.

-- ============================================================================
-- QUERY PATTERNS
-- ============================================================================
--
-- List user's decks:
-- SELECT * FROM decks WHERE user_id = ? ORDER BY updated_at DESC;
--
-- List user's tags:
-- SELECT * FROM tags WHERE user_id = ? ORDER BY name;
--
-- Get user's statistics:
-- SELECT * FROM user_statistics WHERE user_id = ?;
--
-- Get user's daily stats for past week:
-- SELECT * FROM daily_study_stats
-- WHERE user_id = ? AND study_date >= CURRENT_DATE - INTERVAL '7 days'
-- ORDER BY study_date DESC;
--
-- User's study sessions via deck:
-- SELECT ss.* FROM study_sessions ss
-- JOIN decks d ON ss.deck_id = d.id
-- WHERE d.user_id = ?
-- ORDER BY ss.completed_at DESC;
