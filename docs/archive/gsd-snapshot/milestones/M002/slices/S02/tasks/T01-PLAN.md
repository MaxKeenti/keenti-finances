---
estimated_steps: 12
estimated_files: 8
skills_used: []
---

# T01: Add color column via Flyway migration and wire through backend stack

The category color field must persist in the database and flow through the entire hexagonal architecture so the frontend can receive and submit hue values.

Do:
1. Create V7__add_color_to_category.sql with ALTER TABLE category ADD COLUMN color VARCHAR(10)
2. Add public String color field to CategoryEntity.java
3. Add String color param to Category.java domain model (constructor, getter)
4. Update CategoryRequest record to include optional String color
5. Update CategoryResponse record to include String color
6. Update PanacheCategoryRepository toDomain() and save()/update() to map color
7. Update CategoryService create/update to pass color through
8. Add String categoryColor to TransactionResponse record
9. Update TransactionResource/mapper to include category color in transaction responses

Done when: ./mvnw compile exits 0; V7 migration file exists; CategoryResponse includes color field; TransactionResponse includes categoryColor field.

## Inputs

- `backend/src/main/java/com/keenti/finances/domain/model/Category.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/CategoryEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheCategoryRepository.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryRequest.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResponse.java`
- `backend/src/main/java/com/keenti/finances/application/service/CategoryService.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResponse.java`

## Expected Output

- `backend/src/main/resources/db/migration/V7__add_color_to_category.sql`
- `backend/src/main/java/com/keenti/finances/domain/model/Category.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/CategoryEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheCategoryRepository.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryRequest.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResponse.java`
- `backend/src/main/java/com/keenti/finances/application/service/CategoryService.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResponse.java`

## Verification

./mvnw compile -f backend/pom.xml
