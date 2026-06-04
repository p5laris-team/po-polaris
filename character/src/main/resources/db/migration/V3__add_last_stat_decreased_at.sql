-- =============================================================
-- Polaris Character Domain - Add last_stat_decreased_at
-- =============================================================

ALTER TABLE user_characters
ADD COLUMN last_stat_decreased_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
