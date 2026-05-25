---
estimated_steps: 29
estimated_files: 6
skills_used: []
---

# T01: Scaffold hexagonal backend structure, add dependencies, and create Flyway user migration

Set up the hexagonal package structure under com.keenti.finances with domain/application/infrastructure layers. Add Quarkus dependencies (Panache Hibernate ORM, REST Jackson, Hibernate Validator, jbcrypt). Configure application.properties for dev PostgreSQL and Flyway. Create V1__create_user_table.sql migration with id, username, password_hash columns and a seeded bcrypt-hashed admin user. Remove the default GreetingResource and its tests.

---
estimated_steps: 7
estimated_files: 10
skills_used: []
---

## Steps

1. Rename groupId in pom.xml from org.acme to com.keenti.finances and update artifactId to keenti-finances
2. Add dependencies to pom.xml: quarkus-hibernate-orm-panache, quarkus-rest-jackson, quarkus-hibernate-validator, org.mindrot:jbcrypt:0.4
3. Create hexagonal package directories under backend/src/main/java/com/keenti/finances/: domain/model, domain/port/in, domain/port/out, application/service, infrastructure/adapter/in/rest, infrastructure/adapter/out/persistence, infrastructure/adapter/out/security
4. Configure application.properties: datasource URL (jdbc:postgresql://localhost:5432/keenti_finances), dev profile with auto-migrate, Flyway enabled
5. Create backend/src/main/resources/db/migration/V1__create_user_table.sql with user table (id BIGSERIAL PRIMARY KEY, username VARCHAR(255) UNIQUE NOT NULL, password_hash VARCHAR(255) NOT NULL) and INSERT seed with bcrypt hash
6. Delete GreetingResource.java and its test files (GreetingResourceTest.java, GreetingResourceIT.java)
7. Create placeholder .gitkeep files in each package directory so git tracks the structure

## Must-Haves

- [ ] Hexagonal package structure with domain, application, infrastructure layers
- [ ] Flyway migration V1 creates user table and seeds admin user
- [ ] pom.xml has Panache, REST Jackson, Hibernate Validator, jbcrypt dependencies
- [ ] application.properties configured for dev PostgreSQL
- [ ] GreetingResource and its tests deleted

## Threat Surface

- **Abuse**: Seeded password hash must use strong bcrypt cost factor (≥10)
- **Data exposure**: Password hash in migration file — acceptable for single-user dev seed
- **Input trust**: None at this stage (no endpoints yet)

## Verification

- cd backend && ./mvnw compile -q exits 0
- test -f backend/src/main/resources/db/migration/V1__create_user_table.sql
- grep -q 'quarkus-hibernate-orm-panache' backend/pom.xml
- ! test -f backend/src/main/java/org/acme/GreetingResource.java

## Inputs

- `backend/pom.xml — existing Maven config to extend with new dependencies`
- `backend/src/main/resources/application.properties — empty properties file to configure`
- `backend/src/main/java/org/acme/GreetingResource.java — default resource to delete`
- `backend/src/test/java/org/acme/GreetingResourceTest.java — default test to delete`
- `backend/src/test/java/org/acme/GreetingResourceIT.java — default IT test to delete`

## Expected Output

- `backend/pom.xml — updated with new dependencies and groupId`
- `backend/src/main/resources/application.properties — configured for dev PostgreSQL and Flyway`
- `backend/src/main/resources/db/migration/V1__create_user_table.sql — user table schema and seed data`

## Verification

cd backend && ./mvnw compile -q && echo 'COMPILE OK' && test -f src/main/resources/db/migration/V1__create_user_table.sql && grep -q 'quarkus-hibernate-orm-panache' pom.xml && ! test -f src/main/java/org/acme/GreetingResource.java && echo 'ALL CHECKS PASSED'
