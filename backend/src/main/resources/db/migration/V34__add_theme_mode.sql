-- Per-User light/dark appearance preference. 'system' follows the OS setting,
-- which is the behaviour every existing row had before this column existed.
-- The default must stay in sync with UserEntity.DEFAULT_THEME_MODE, because
-- Hibernate writes every column on INSERT and so bypasses the SQL DEFAULT.

ALTER TABLE app_user ADD COLUMN theme_mode VARCHAR(6) NOT NULL DEFAULT 'system';

ALTER TABLE app_user ADD CONSTRAINT app_user_theme_mode_valid
    CHECK (theme_mode IN ('light', 'dark', 'system'));
