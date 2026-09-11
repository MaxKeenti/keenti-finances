-- New rows only: preserve every existing User's navigation preferences.
ALTER TABLE app_user ALTER COLUMN mobile_pinned_nav_items SET DEFAULT '/,/transactions,/boxes';
