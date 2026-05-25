---
id: T01
parent: S01
milestone: M001
key_files:
  - backend/pom.xml
  - backend/src/main/resources/application.properties
  - backend/src/main/resources/db/migration/V1__create_user_table.sql
  - backend/src/main/java/com/keenti/finances/domain/model/.gitkeep
  - backend/src/main/java/com/keenti/finances/domain/port/in/.gitkeep
  - backend/src/main/java/com/keenti/finances/domain/port/out/.gitkeep
  - backend/src/main/java/com/keenti/finances/application/service/.gitkeep
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/.gitkeep
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/.gitkeep
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/security/.gitkeep
key_decisions:
  - Named the table app_user (not user) to avoid conflict with the PostgreSQL reserved word USER
  - Used bcrypt cost factor 10 for the dev seed hash — meets the ≥10 threat surface requirement
  - Retained quarkus-rest alongside quarkus-rest-jackson (jackson is a complement, not a replacement)
duration: 
verification_result: passed
completed_at: 2026-05-13T15:50:49.006Z
blocker_discovered: false
---

# T01: Hexagonal backend scaffolded with Panache/jbcrypt deps, Flyway V1 migration, and GreetingResource deleted — `./mvnw compile -q` exits 0

**Hexagonal backend scaffolded with Panache/jbcrypt deps, Flyway V1 migration, and GreetingResource deleted — `./mvnw compile -q` exits 0**

## What Happened

Updated pom.xml: renamed groupId to com.keenti.finances and artifactId to keenti-finances. Added quarkus-rest-jackson, quarkus-hibernate-orm-panache, quarkus-hibernate-validator, and org.mindrot:jbcrypt:0.4 dependencies alongside the existing quarkus-flyway and quarkus-jdbc-postgresql. Created all seven hexagonal package directories under backend/src/main/java/com/keenti/finances/ (domain/model, domain/port/in, domain/port/out, application/service, infrastructure/adapter/in/rest, infrastructure/adapter/out/persistence, infrastructure/adapter/out/security) each with a .gitkeep so git tracks the empty structure. Configured application.properties with datasource URL pointing to jdbc:postgresql://localhost:5432/keenti_finances, Hibernate ORM set to none-generation, and Flyway migrate-at-start enabled. Created V1__create_user_table.sql with an app_user table (BIGSERIAL id, UNIQUE username, password_hash) and a seeded admin user with a bcrypt cost-10 hash for the placeholder password Ch@ngeMe2025!. Deleted GreetingResource.java, GreetingResourceTest.java, and GreetingResourceIT.java. Compilation succeeded.

## Verification

Ran ./mvnw compile -q (exit 0); confirmed V1__create_user_table.sql exists; confirmed quarkus-hibernate-orm-panache present in pom.xml; confirmed org/acme/GreetingResource.java absent.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `./mvnw compile -q && echo COMPILE OK` | 0 | pass | 1945ms |
| 2 | `test -f src/main/resources/db/migration/V1__create_user_table.sql && echo MIGRATION FILE OK` | 0 | pass | 1ms |
| 3 | `grep -q 'quarkus-hibernate-orm-panache' pom.xml && echo PANACHE DEP OK` | 0 | pass | 2ms |
| 4 | `! test -f src/main/java/org/acme/GreetingResource.java && echo GREETING DELETED OK` | 0 | pass | 1ms |

## Deviations

none

## Known Issues

None.

## Files Created/Modified

- `backend/pom.xml`
- `backend/src/main/resources/application.properties`
- `backend/src/main/resources/db/migration/V1__create_user_table.sql`
- `backend/src/main/java/com/keenti/finances/domain/model/.gitkeep`
- `backend/src/main/java/com/keenti/finances/domain/port/in/.gitkeep`
- `backend/src/main/java/com/keenti/finances/domain/port/out/.gitkeep`
- `backend/src/main/java/com/keenti/finances/application/service/.gitkeep`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/.gitkeep`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/.gitkeep`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/security/.gitkeep`
