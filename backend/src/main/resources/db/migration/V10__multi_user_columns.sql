ALTER TABLE app_user ADD COLUMN workos_id VARCHAR(255) UNIQUE;

ALTER TABLE category  ADD COLUMN user_id BIGINT REFERENCES app_user(id);
ALTER TABLE contact   ADD COLUMN user_id BIGINT REFERENCES app_user(id);
ALTER TABLE transaction  ADD COLUMN user_id BIGINT REFERENCES app_user(id);
ALTER TABLE subscription ADD COLUMN user_id BIGINT REFERENCES app_user(id);
ALTER TABLE debt      ADD COLUMN user_id BIGINT REFERENCES app_user(id);

UPDATE category     SET user_id = 1;
UPDATE contact      SET user_id = 1;
UPDATE transaction  SET user_id = 1;
UPDATE subscription SET user_id = 1;
UPDATE debt         SET user_id = 1;

ALTER TABLE category     ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE contact      ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE transaction  ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE subscription ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE debt         ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE category DROP CONSTRAINT category_name_key;
ALTER TABLE category ADD CONSTRAINT category_user_name_unique UNIQUE(user_id, name);
