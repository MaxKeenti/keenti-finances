---
estimated_steps: 8
estimated_files: 5
skills_used: []
---

# T01: Flyway V10 migration and UserEntity workos_id column

Why: All downstream tasks depend on user_id FK columns existing on data tables and workos_id on app_user. This migration backfills existing data to user 1 and establishes the multi-user schema.

Do:
1. Create V10__multi_user_columns.sql: ALTER app_user ADD COLUMN workos_id VARCHAR(255) UNIQUE; ALTER category/contact/transaction/subscription/debt ADD COLUMN user_id BIGINT REFERENCES app_user(id); UPDATE each SET user_id = 1; ALTER each ALTER COLUMN user_id SET NOT NULL; DROP old UNIQUE on category.name; ADD UNIQUE(user_id, name) on category.
2. Update UserEntity.java: add workos_id column (nullable, unique), add static findByWorkosId method.
3. Update PanacheUserRepository.java: add findByWorkosId returning Optional<User>, add save method for JIT provisioning.
4. Update UserRepository port interface with new methods.
5. Update User domain model for workos_id field.

Done when: ./mvnw quarkus:dev starts without migration errors; V10 applied; existing data has user_id = 1; UserEntity.findByWorkosId compiles.

## Inputs

- `backend/src/main/resources/db/migration/V9__make_password_hash_nullable.sql`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/UserEntity.java`
- `backend/src/main/java/com/keenti/finances/domain/model/User.java`

## Expected Output

- `backend/src/main/resources/db/migration/V10__multi_user_columns.sql`

## Verification

./mvnw quarkus:dev -Dquarkus.http.port=0 -Dquarkus.devservices.enabled=true
