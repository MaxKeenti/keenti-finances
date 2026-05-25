---
estimated_steps: 8
estimated_files: 6
skills_used: []
---

# T04: Remove backend password auth code and update User model

**Slice:** S05 — Passkey Auth via WorkOS
**Milestone:** M002

## Description

With WorkOS handling auth, the backend AuthResource, AuthService, BcryptPasswordHasher, and AuthUseCase are dead code. The User model's passwordHash field is no longer needed. Remove all backend auth code and make passwordHash nullable via a new Flyway migration.

## Steps

1. Delete `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/AuthResource.java`
2. Delete `backend/src/main/java/com/keenti/finances/application/service/AuthService.java`
3. Delete `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/security/BcryptPasswordHasher.java`
4. Delete `backend/src/main/java/com/keenti/finances/domain/port/in/AuthUseCase.java`
5. Delete `backend/src/main/java/com/keenti/finances/domain/port/out/PasswordHasher.java`
6. Grep for any references to deleted classes (imports, DI injection sites) and remove them
7. In `User.java` and `UserEntity.java`: make `passwordHash` field nullable. Add a new Flyway migration (V7) to ALTER COLUMN password_hash SET DEFAULT NULL / DROP NOT NULL. Do NOT modify existing migrations.
8. Verify: `./mvnw compile -f backend/pom.xml` exits 0

## Must-Haves

- [ ] AuthResource.java deleted
- [ ] AuthService.java deleted
- [ ] BcryptPasswordHasher.java deleted
- [ ] AuthUseCase.java deleted
- [ ] PasswordHasher.java deleted
- [ ] No dangling import references to deleted classes
- [ ] passwordHash nullable in User model
- [ ] New Flyway migration for schema change
- [ ] Backend compiles cleanly

## Verification

- `cd backend && ./mvnw compile` exits 0
- No grep hits for `AuthResource`, `AuthService`, `BcryptPasswordHasher`, `AuthUseCase`, `PasswordHasher` in remaining source

## Verify Rules

- Use a real executable check, not prose.
- If the check needs file-content assertions, write a `node:test` file and run it with `node --test` or a package test script.
- Do not use inline `node -e` assertions for verification.

## Inputs

- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/AuthResource.java` — dead code to remove
- `backend/src/main/java/com/keenti/finances/application/service/AuthService.java` — dead code to remove
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/security/BcryptPasswordHasher.java` — dead code to remove
- `backend/src/main/java/com/keenti/finances/domain/port/in/AuthUseCase.java` — dead code to remove
- `backend/src/main/java/com/keenti/finances/domain/port/out/PasswordHasher.java` — dead code to remove
- `backend/src/main/java/com/keenti/finances/domain/model/User.java` — needs passwordHash made nullable
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/UserEntity.java` — needs passwordHash made nullable

## Expected Output

- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/AuthResource.java` — deleted
- `backend/src/main/java/com/keenti/finances/application/service/AuthService.java` — deleted
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/security/BcryptPasswordHasher.java` — deleted
- `backend/src/main/java/com/keenti/finances/domain/port/in/AuthUseCase.java` — deleted
- `backend/src/main/java/com/keenti/finances/domain/port/out/PasswordHasher.java` — deleted
- `backend/src/main/java/com/keenti/finances/domain/model/User.java` — passwordHash nullable
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/UserEntity.java` — passwordHash nullable
- `backend/src/main/resources/db/migration/V7__make_password_hash_nullable.sql` — new migration
