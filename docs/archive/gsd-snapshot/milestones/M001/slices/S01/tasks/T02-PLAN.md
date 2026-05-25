---
estimated_steps: 38
estimated_files: 11
skills_used: []
---

# T02: Implement auth domain model, ports, application service, and REST login endpoint

Build the auth vertical through all hexagonal layers. Domain: User model (POJO with id, username, passwordHash). Ports: AuthUseCase (inbound), UserRepository and PasswordHasher (outbound). Application: AuthService implementing AuthUseCase with login(username, password) returning Optional<User>. Infrastructure: UserEntity (Panache entity), PanacheUserRepository implementing UserRepository, BcryptPasswordHasher implementing PasswordHasher, AuthResource (JAX-RS POST /api/auth/login accepting JSON {username, password}, returning 200 with user info or 401).

---
estimated_steps: 11
estimated_files: 11
skills_used: []
---

## Steps

1. Create domain/model/User.java — plain POJO with Long id, String username, String passwordHash; no framework annotations
2. Create domain/port/in/AuthUseCase.java — interface with Optional<User> login(String username, String password)
3. Create domain/port/out/UserRepository.java — interface with Optional<User> findByUsername(String username)
4. Create domain/port/out/PasswordHasher.java — interface with boolean verify(String plaintext, String hash)
5. Create application/service/AuthService.java — @ApplicationScoped, implements AuthUseCase, injects UserRepository and PasswordHasher, implements login by finding user and verifying password
6. Create infrastructure/adapter/out/persistence/UserEntity.java — @Entity Panache entity mapping to users table with id, username, password_hash columns
7. Create infrastructure/adapter/out/persistence/PanacheUserRepository.java — @ApplicationScoped, implements UserRepository, queries UserEntity and maps to domain User
8. Create infrastructure/adapter/out/security/BcryptPasswordHasher.java — @ApplicationScoped, implements PasswordHasher using jbcrypt BCrypt.checkpw
9. Create infrastructure/adapter/in/rest/LoginRequest.java — record with @NotBlank username and password
10. Create infrastructure/adapter/in/rest/LoginResponse.java — record with username field
11. Create infrastructure/adapter/in/rest/AuthResource.java — @Path('/api/auth') with POST /login, injects AuthUseCase, returns 200 JSON LoginResponse or 401

## Must-Haves

- [ ] Domain model User is a plain POJO with no Jakarta/Quarkus imports
- [ ] Port interfaces defined in domain layer with no infrastructure dependencies
- [ ] AuthService implements login logic via ports only
- [ ] POST /api/auth/login returns 200 with username on success, 401 on failure
- [ ] BcryptPasswordHasher uses jbcrypt for password verification
- [ ] PanacheUserRepository maps between UserEntity and domain User

## Threat Surface

- **Abuse**: Timing attack on login — bcrypt's constant-time comparison mitigates
- **Data exposure**: Login response must not include password hash
- **Input trust**: Username and password from JSON body — validated via @NotBlank

## Negative Tests

- **Malformed inputs**: Empty username, empty password, missing fields → 400
- **Error paths**: Wrong password → 401, nonexistent username → 401 (same error, no enumeration)
- **Boundary conditions**: Very long username/password — bean validation handles

## Verification

- cd backend && ./mvnw compile -q exits 0
- Domain purity: no jakarta/quarkus/javax imports in domain/ (except Optional from java.util)
- grep -q 'BCrypt.checkpw' in BcryptPasswordHasher.java
- grep -q '@Path.*api/auth' in AuthResource.java

## Inputs

- `backend/pom.xml — dependencies from T01`
- `backend/src/main/resources/application.properties — DB config from T01`
- `backend/src/main/resources/db/migration/V1__create_user_table.sql — user table schema from T01`

## Expected Output

- `backend/src/main/java/com/keenti/finances/domain/model/User.java — domain user model`
- `backend/src/main/java/com/keenti/finances/domain/port/in/AuthUseCase.java — inbound auth port`
- `backend/src/main/java/com/keenti/finances/domain/port/out/UserRepository.java — outbound user repo port`
- `backend/src/main/java/com/keenti/finances/domain/port/out/PasswordHasher.java — outbound password hasher port`
- `backend/src/main/java/com/keenti/finances/application/service/AuthService.java — auth application service`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/UserEntity.java — Panache user entity`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheUserRepository.java — user repo implementation`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/security/BcryptPasswordHasher.java — bcrypt password verifier`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/AuthResource.java — login REST endpoint`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/LoginRequest.java — login request DTO`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/LoginResponse.java — login response DTO`

## Verification

cd backend && ./mvnw compile -q && echo 'COMPILE OK' && grep -q 'BCrypt.checkpw' src/main/java/com/keenti/finances/infrastructure/adapter/out/security/BcryptPasswordHasher.java && grep -q '@Path' src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/AuthResource.java && echo 'ALL CHECKS PASSED'
