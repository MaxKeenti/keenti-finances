-- Per-User theme + typography preferences. See CAPABILITIES.md "Per-User theme customization".
-- Defaults match the current hardcoded values in frontend/src/routes/layout.css.

ALTER TABLE app_user ADD COLUMN primary_hue   SMALLINT     NOT NULL DEFAULT 91;
ALTER TABLE app_user ADD COLUMN heading_font  VARCHAR(50)  NOT NULL DEFAULT 'Fraunces';
ALTER TABLE app_user ADD COLUMN body_font     VARCHAR(50)  NOT NULL DEFAULT 'Geist';

ALTER TABLE app_user ADD CONSTRAINT app_user_primary_hue_range CHECK (primary_hue >= 0 AND primary_hue < 360);
