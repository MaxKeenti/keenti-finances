---
id: T01
parent: S02
milestone: M001
key_files:
  - backend/src/main/resources/db/migration/V2__create_category_and_contact_tables.sql
  - backend/src/main/java/com/keenti/finances/domain/model/Category.java
  - backend/src/main/java/com/keenti/finances/domain/model/Contact.java
  - backend/src/main/java/com/keenti/finances/domain/port/in/CategoryUseCase.java
  - backend/src/main/java/com/keenti/finances/domain/port/in/ContactUseCase.java
  - backend/src/main/java/com/keenti/finances/domain/port/out/CategoryRepository.java
  - backend/src/main/java/com/keenti/finances/domain/port/out/ContactRepository.java
  - backend/src/main/java/com/keenti/finances/application/service/CategoryService.java
  - backend/src/main/java/com/keenti/finances/application/service/ContactService.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/CategoryEntity.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/ContactEntity.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheCategoryRepository.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheContactRepository.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResource.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/ContactResource.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryRequest.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/ContactRequest.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResponse.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/ContactResponse.java
key_decisions:
  - Category type validation (INGRESS/EGRESS/BOTH) is enforced in the application service rather than via a DB-only constraint, providing a structured 400 error body before hitting the DB
  - ContactRepository has no existsByName — contacts allow duplicate names per domain requirements
  - Duplicate category name on update checks for name change before throwing 409, allowing a PUT that keeps the same name
duration: 
verification_result: passed
completed_at: 2026-05-13T20:46:06.177Z
blocker_discovered: false
---

# T01: Flyway V2 migration, Category and Contact domain models, hexagonal ports, application services, Panache adapters, and JAX-RS REST resources all created and compiling clean

**Flyway V2 migration, Category and Contact domain models, hexagonal ports, application services, Panache adapters, and JAX-RS REST resources all created and compiling clean**

## What Happened

Created the full backend vertical for Category and Contact following the hexagonal pattern from S01. Began by reading all six reference files (User.java, AuthResource.java, UserEntity.java, PanacheUserRepository.java, AuthService.java, V1 migration) to extract patterns before writing a single line.

Created in order:
1. Flyway V2 migration with `category` (BIGSERIAL PK, name VARCHAR(255) UNIQUE NOT NULL, type VARCHAR(50) NOT NULL CHECK IN ('INGRESS','EGRESS','BOTH')) and `contact` (BIGSERIAL PK, name VARCHAR(255) NOT NULL, phone VARCHAR(50), email VARCHAR(255)) tables.
2. Domain POJOs `Category.java` and `Contact.java` — zero framework imports, plain constructors + getters.
3. Inbound ports `CategoryUseCase` and `ContactUseCase` with list/getById/create/update/delete.
4. Outbound ports `CategoryRepository` and `ContactRepository` with findAll/findById/save/update/deleteById, plus `existsByName` on CategoryRepository.
5. Application services `CategoryService` and `ContactService`: @ApplicationScoped, @Inject repository port, @Transactional on write methods, structured Jboss Logger infof lines on each operation. CategoryService validates type against INGRESS/EGRESS/BOTH set and throws 409 WebApplicationException on duplicate name. ContactService throws NotFoundException on missing id.
6. Panache entities `CategoryEntity` (@Table category) and `ContactEntity` (@Table contact) extending PanacheEntityBase with IDENTITY generation.
7. Repository adapters `PanacheCategoryRepository` and `PanacheContactRepository` mapping entity↔domain in toDomain helpers.
8. Record DTOs: `CategoryRequest` (@NotBlank name+type, @Size), `ContactRequest` (@NotBlank name, optional phone/email with @Size), `CategoryResponse`, `ContactResponse`.
9. JAX-RS resources `CategoryResource` (@Path /api/categories) and `ContactResource` (@Path /api/contacts) with GET list, GET /{id}, POST (201), PUT /{id} (200), DELETE /{id} (204). Structured JSON error bodies on 404/409.

## Verification

Ran `./mvnw compile -q` — exit 0. Confirmed no framework imports in domain layer via `grep -r 'import jakarta|import javax|import io.quarkus' backend/src/main/java/com/keenti/finances/domain/` — exit 1 (no matches). Confirmed migration file exists. Confirmed both @Path annotations present in REST resources.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `./mvnw compile -q` | 0 | pass | 8200ms |
| 2 | `grep -r 'import jakarta|import javax|import io.quarkus' backend/src/main/java/com/keenti/finances/domain/` | 1 | pass — no framework imports in domain | 50ms |
| 3 | `test -f backend/src/main/resources/db/migration/V2__create_category_and_contact_tables.sql` | 0 | pass | 10ms |
| 4 | `grep -q '@Path("/api/categories")' CategoryResource.java && grep -q '@Path("/api/contacts")' ContactResource.java` | 0 | pass | 20ms |

## Deviations

none

## Known Issues

none

## Files Created/Modified

- `backend/src/main/resources/db/migration/V2__create_category_and_contact_tables.sql`
- `backend/src/main/java/com/keenti/finances/domain/model/Category.java`
- `backend/src/main/java/com/keenti/finances/domain/model/Contact.java`
- `backend/src/main/java/com/keenti/finances/domain/port/in/CategoryUseCase.java`
- `backend/src/main/java/com/keenti/finances/domain/port/in/ContactUseCase.java`
- `backend/src/main/java/com/keenti/finances/domain/port/out/CategoryRepository.java`
- `backend/src/main/java/com/keenti/finances/domain/port/out/ContactRepository.java`
- `backend/src/main/java/com/keenti/finances/application/service/CategoryService.java`
- `backend/src/main/java/com/keenti/finances/application/service/ContactService.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/CategoryEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/ContactEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheCategoryRepository.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheContactRepository.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResource.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/ContactResource.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryRequest.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/ContactRequest.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResponse.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/ContactResponse.java`
