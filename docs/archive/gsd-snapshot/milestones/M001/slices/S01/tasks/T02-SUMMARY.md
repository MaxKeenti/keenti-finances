---
id: T02
parent: S01
milestone: M001
key_files:
  - backend/src/main/java/com/keenti/finances/domain/model/User.java
  - backend/src/main/java/com/keenti/finances/domain/port/in/AuthUseCase.java
  - backend/src/main/java/com/keenti/finances/domain/port/out/UserRepository.java
  - backend/src/main/java/com/keenti/finances/domain/port/out/PasswordHasher.java
  - backend/src/main/java/com/keenti/finances/application/service/AuthService.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/UserEntity.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheUserRepository.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/security/BcryptPasswordHasher.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/LoginRequest.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/LoginResponse.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/AuthResource.java
key_decisions:
  - Domain model User is a plain POJO with no Jakarta/Quarkus imports — only java.util types
  - AuthService uses Jboss Logger (not SLF4J) for structured login audit logs matching Quarkus idiom
  - LoginResponse record exposes only username — password hash never leaves the service layer
  - AuthResource returns a JSON string literal for 401 body to keep the response content-type consistent
duration: 
verification_result: passed
completed_at: 2026-05-13T15:52:25.809Z
blocker_discovered: false
---

# T02: Auth domain model, ports, application service, and REST login endpoint implemented across all hexagonal layers

**Auth domain model, ports, application service, and REST login endpoint implemented across all hexagonal layers**

## What Happened

Created 11 Java files spanning the full hexagonal stack. Domain layer: User POJO (id, username, passwordHash — no framework imports), AuthUseCase inbound port, UserRepository and PasswordHasher outbound ports. Application layer: AuthService (@ApplicationScoped) implements AuthUseCase via port injection, calls userRepository.findByUsername then passwordHasher.verify, and emits structured Jboss log lines on each login attempt (username, success flag). Infrastructure adapters: UserEntity (Panache @Entity on app_user table), PanacheUserRepository (maps UserEntity → domain User), BcryptPasswordHasher (delegates to BCrypt.checkpw). REST: LoginRequest record with @NotBlank validation, LoginResponse record (username only — no hash), AuthResource @Path("/api/auth") POST /login returns 200 + LoginResponse on success or 401 + JSON error on failure. ./mvnw compile -q exited 0 on first attempt.

## Verification

Ran `cd backend && ./mvnw compile -q` → exit 0. Checked domain purity with grep — no jakarta/javax/quarkus imports in domain/ classes. Verified BCrypt.checkpw present in BcryptPasswordHasher. Verified @Path annotation present in AuthResource.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `cd backend && ./mvnw compile -q` | 0 | PASS | 18000ms |
| 2 | `grep -rn 'import jakarta|import javax|import io.quarkus' src/main/java/com/keenti/finances/domain/` | 1 | PASS: domain is pure | 50ms |
| 3 | `grep -q 'BCrypt.checkpw' src/main/java/com/keenti/finances/infrastructure/adapter/out/security/BcryptPasswordHasher.java` | 0 | PASS | 20ms |
| 4 | `grep -q '@Path' src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/AuthResource.java` | 0 | PASS | 20ms |

## Deviations

none

## Known Issues

None.

## Files Created/Modified

- `backend/src/main/java/com/keenti/finances/domain/model/User.java`
- `backend/src/main/java/com/keenti/finances/domain/port/in/AuthUseCase.java`
- `backend/src/main/java/com/keenti/finances/domain/port/out/UserRepository.java`
- `backend/src/main/java/com/keenti/finances/domain/port/out/PasswordHasher.java`
- `backend/src/main/java/com/keenti/finances/application/service/AuthService.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/UserEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheUserRepository.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/security/BcryptPasswordHasher.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/LoginRequest.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/LoginResponse.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/AuthResource.java`
