---
id: T04
parent: S05
milestone: M002
key_files:
  - backend/src/main/java/com/keenti/finances/domain/model/User.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/UserEntity.java
  - backend/src/main/resources/db/migration/V9__make_password_hash_nullable.sql
key_decisions:
  - New migration is V9 (not V7) because V7__add_color_to_category.sql already exists in the migrations directory
  - User domain model retains the passwordHash field for backwards compatibility with existing rows but gained a two-arg constructor for WorkOS-created users that have no password
duration: 
verification_result: passed
completed_at: 2026-05-17T23:06:13.650Z
blocker_discovered: false
---

# T04: Deleted 5 dead backend auth files, made passwordHash nullable in User/UserEntity, and added V9 Flyway migration; backend compiles cleanly

**Deleted 5 dead backend auth files, made passwordHash nullable in User/UserEntity, and added V9 Flyway migration; backend compiles cleanly**

## What Happened

All five dead auth files were deleted: AuthResource.java, AuthService.java, BcryptPasswordHasher.java, AuthUseCase.java, and PasswordHasher.java. A grep confirmed no external references to any of these classes in the remaining source tree. User.java received a convenience constructor `User(Long id, String username)` that delegates to the existing three-arg constructor with null for passwordHash. UserEntity.java had its @Column annotation updated from `nullable = false` to `nullable = true`. Since V7 was already taken by the color migration, the new Flyway migration was written as V9__make_password_hash_nullable.sql with a single `ALTER TABLE app_user ALTER COLUMN password_hash DROP NOT NULL`. The backend compiled with ./mvnw compile -q exiting 0.

## Verification

Ran `./mvnw compile -q` in backend/ — exit 0. Ran grep for all 5 deleted class names across backend/src/main/java — exit 1 (no matches). Both confirm clean removal with no dangling references.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `cd backend && ./mvnw compile -q` | 0 | pass | 8200ms |
| 2 | `grep -r 'AuthResource|AuthService|BcryptPasswordHasher|AuthUseCase|PasswordHasher' backend/src/main/java --include='*.java'` | 1 | pass — no matches | 120ms |

## Deviations

Migration numbered V9 instead of V7 as the task plan suggested — V7 was already taken by a prior migration (add_color_to_category). Plan guidance was adapted to the actual state.

## Known Issues

None.

## Files Created/Modified

- `backend/src/main/java/com/keenti/finances/domain/model/User.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/UserEntity.java`
- `backend/src/main/resources/db/migration/V9__make_password_hash_nullable.sql`
