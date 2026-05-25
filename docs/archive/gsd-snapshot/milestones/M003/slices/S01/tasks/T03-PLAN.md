---
estimated_steps: 6
estimated_files: 6
skills_used: []
---

# T03: Repository user-scoping for writes and native SQL queries

Why: Entity @Filters scope reads, but writes must explicitly set the user FK. Three native SQL queries bypass Hibernate filters and need manual WHERE user_id clauses — missing one is a data leak.

Do:
1. Inject UserContext into each repository: PanacheCategoryRepository, PanacheContactRepository, PanacheTransactionRepository, PanacheSubscriptionRepository, PanacheDebtRepository — set entity.user in toEntity() and update() methods.
2. Fix 3 native SQL queries: PanacheTransactionRepository.findMonthlySummary() add AND user_id = :userId; PanacheTransactionRepository.getNetBalance() add WHERE user_id = :userId; PanacheDebtPaymentRepository.sumByDebtId() add subquery ensuring debt.user_id matches current user.
3. Verify PanacheDebtPaymentRepository.sumByDebtId() scoping through debt table join.

Done when: All repositories compile; save/update operations set user FK; native queries include user_id filtering; app starts in dev mode.

## Inputs

- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/UserContext.java`

## Expected Output

- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheCategoryRepository.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheContactRepository.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheTransactionRepository.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionRepository.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheDebtRepository.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheDebtPaymentRepository.java`

## Verification

./mvnw quarkus:dev -Dquarkus.http.port=0 -Dquarkus.devservices.enabled=true
