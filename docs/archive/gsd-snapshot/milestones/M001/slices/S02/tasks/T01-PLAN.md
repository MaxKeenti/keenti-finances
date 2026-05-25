---
estimated_steps: 72
estimated_files: 19
skills_used: []
---

# T01: Flyway V2 migration, Category and Contact domain models, hexagonal ports, services, and REST resources

---
estimated_steps: 8
estimated_files: 18
skills_used: []
---

# T01: Flyway V2 migration, Category and Contact domain models, hexagonal ports, services, and REST resources

**Slice:** S02 — Categories & Contacts
**Milestone:** M001

## Description

Create the full backend vertical for Category and Contact entities following the hexagonal pattern established in S01. This includes: Flyway V2 migration creating both tables, domain model POJOs (no framework imports), inbound port interfaces (CrudUseCase pattern with list/get/create/update/delete), outbound repository port interfaces, application services with Jboss Logger structured logging, Panache entities and repository adapters, and JAX-RS REST resources with bean validation.

Category has: id (BIGSERIAL), name (VARCHAR 255, UNIQUE NOT NULL), type (VARCHAR 50, NOT NULL — values INGRESS, EGRESS, BOTH).
Contact has: id (BIGSERIAL), name (VARCHAR 255, NOT NULL), phone (VARCHAR 50, nullable), email (VARCHAR 255, nullable).

## Threat Surface

- **Abuse**: Parameter tampering on PUT/DELETE (changing other records) — mitigated by single-user app, all records belong to the same user
- **Data exposure**: Contact phone/email are PII but acceptable for single-user app with auth guard
- **Input trust**: User input reaches DB via name, type, phone, email fields — validated by @NotBlank/@Size on request DTOs and DB constraints

## Negative Tests

- **Malformed inputs**: empty name, null type, invalid type value, oversized strings
- **Error paths**: create with duplicate name (409), update/delete non-existent id (404)
- **Boundary conditions**: empty list response, max length name

## Steps

1. Create `V2__create_category_and_contact_tables.sql` with category table (id BIGSERIAL PK, name VARCHAR(255) UNIQUE NOT NULL, type VARCHAR(50) NOT NULL CHECK IN ('INGRESS','EGRESS','BOTH')) and contact table (id BIGSERIAL PK, name VARCHAR(255) NOT NULL, phone VARCHAR(50), email VARCHAR(255))
2. Create domain models: `Category.java` (id, name, type as String) and `Contact.java` (id, name, phone, email) — plain POJOs, no framework imports
3. Create inbound ports: `CategoryUseCase.java` and `ContactUseCase.java` with list(), getById(Long), create(domain), update(Long, domain), delete(Long) methods
4. Create outbound ports: `CategoryRepository.java` and `ContactRepository.java` with findAll(), findById(Long), save(domain), update(domain), deleteById(Long), existsByName(String)
5. Create application services: `CategoryService.java` and `ContactService.java` implementing use case ports, injecting repository ports, with @Transactional on write methods and structured Jboss Logger audit lines
6. Create Panache entities: `CategoryEntity.java` (@Table category) and `ContactEntity.java` (@Table contact) extending PanacheEntityBase with IDENTITY generation
7. Create Panache repository adapters: `PanacheCategoryRepository.java` and `PanacheContactRepository.java` mapping between entities and domain models
8. Create REST resources: `CategoryResource.java` (@Path /api/categories) and `ContactResource.java` (@Path /api/contacts) with GET list, GET by id, POST create, PUT update, DELETE — using request/response DTOs with bean validation. Return 201 on create, 200 on update/get/list, 204 on delete, 404 on not found, 409 on duplicate name
9. Create request DTOs: `CategoryRequest.java` (name @NotBlank, type @NotBlank) and `ContactRequest.java` (name @NotBlank, phone optional, email optional)
10. Create response DTOs: `CategoryResponse.java` and `ContactResponse.java` as records

## Must-Haves

- [ ] Domain models have zero jakarta/javax/quarkus imports
- [ ] Flyway migration creates both tables with correct constraints
- [ ] REST resources return structured JSON error bodies (not plain text)
- [ ] @Transactional on service write methods
- [ ] Structured Jboss Logger lines on each CRUD operation
- [ ] Category type validated against INGRESS/EGRESS/BOTH enum values

## Verification

- `./mvnw compile -q` exits 0
- `grep -r 'import jakarta\|import javax\|import io.quarkus' backend/src/main/java/com/keenti/finances/domain/` exits 1 (no framework imports in domain)
- `test -f backend/src/main/resources/db/migration/V2__create_category_and_contact_tables.sql`
- `grep -q '@Path("/api/categories")' backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResource.java`
- `grep -q '@Path("/api/contacts")' backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/ContactResource.java`

## Inputs

- `backend/src/main/java/com/keenti/finances/domain/model/User.java` — pattern reference for domain POJO
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/AuthResource.java` — pattern reference for REST resource
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/UserEntity.java` — pattern reference for Panache entity
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheUserRepository.java` — pattern reference for repository adapter
- `backend/src/main/java/com/keenti/finances/application/service/AuthService.java` — pattern reference for application service with Jboss Logger
- `backend/src/main/resources/db/migration/V1__create_user_table.sql` — pattern reference for Flyway migration
- `backend/pom.xml` — verify dependencies

## Expected Output

- `backend/src/main/resources/db/migration/V2__create_category_and_contact_tables.sql` — Flyway migration
- `backend/src/main/java/com/keenti/finances/domain/model/Category.java` — domain model
- `backend/src/main/java/com/keenti/finances/domain/model/Contact.java` — domain model
- `backend/src/main/java/com/keenti/finances/domain/port/in/CategoryUseCase.java` — inbound port
- `backend/src/main/java/com/keenti/finances/domain/port/in/ContactUseCase.java` — inbound port
- `backend/src/main/java/com/keenti/finances/domain/port/out/CategoryRepository.java` — outbound port
- `backend/src/main/java/com/keenti/finances/domain/port/out/ContactRepository.java` — outbound port
- `backend/src/main/java/com/keenti/finances/application/service/CategoryService.java` — application service
- `backend/src/main/java/com/keenti/finances/application/service/ContactService.java` — application service
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/CategoryEntity.java` — Panache entity
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/ContactEntity.java` — Panache entity
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheCategoryRepository.java` — repository adapter
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheContactRepository.java` — repository adapter
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResource.java` — REST resource
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/ContactResource.java` — REST resource
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryRequest.java` — request DTO
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/ContactRequest.java` — request DTO
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResponse.java` — response DTO
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/ContactResponse.java` — response DTO

## Inputs

- `backend/src/main/java/com/keenti/finances/domain/model/User.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/AuthResource.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/UserEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheUserRepository.java`
- `backend/src/main/java/com/keenti/finances/application/service/AuthService.java`
- `backend/src/main/resources/db/migration/V1__create_user_table.sql`
- `backend/pom.xml`

## Expected Output

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

## Verification

./mvnw compile -q && grep -r 'import jakarta\|import javax\|import io.quarkus' backend/src/main/java/com/keenti/finances/domain/ ; test $? -eq 1
