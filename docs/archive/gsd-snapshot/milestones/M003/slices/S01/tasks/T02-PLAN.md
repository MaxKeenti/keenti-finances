---
estimated_steps: 6
estimated_files: 7
skills_used: []
---

# T02: UserContext bean, ContainerRequestFilter, and entity @Filter annotations

Why: Core multi-user mechanism. Hibernate @Filter on entities + JAX-RS ContainerRequestFilter provides defense-in-depth row-level isolation. This is the first proof — if @Filter doesn't work with Panache static methods, we need to know immediately.

Do:
1. Create UserContext.java (@RequestScoped CDI bean) with userId and workosId fields.
2. Create UserScopeFilter.java (JAX-RS @Provider ContainerRequestFilter): read X-WorkOS-User-Id header, resolve local user via UserEntity.findByWorkosId(), JIT provision if missing (handle race with unique constraint retry), enable Hibernate filter session.enableFilter("userScope").setParameter("userId", userId), return 401 for non-public paths without header.
3. Add @FilterDef and @Filter to CategoryEntity, ContactEntity, TransactionEntity, SubscriptionEntity, DebtEntity — each gets @FilterDef(name="userScope"), @Filter(name="userScope", condition="user_id = :userId"), @ManyToOne UserEntity user.

Done when: App compiles; ContainerRequestFilter resolves users and enables Hibernate filter; GET /api/categories with header returns only that user's categories.

## Inputs

- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/UserEntity.java`
- `backend/src/main/java/com/keenti/finances/domain/port/out/UserRepository.java`

## Expected Output

- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/UserContext.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/UserScopeFilter.java`

## Verification

./mvnw quarkus:dev -Dquarkus.http.port=0 -Dquarkus.devservices.enabled=true
