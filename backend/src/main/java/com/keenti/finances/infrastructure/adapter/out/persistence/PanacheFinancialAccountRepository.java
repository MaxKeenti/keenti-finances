package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.FinancialAccount;
import com.keenti.finances.domain.port.out.FinancialAccountRepository;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import com.keenti.finances.infrastructure.adapter.in.rest.UserScoped;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@UserScoped
public class PanacheFinancialAccountRepository implements FinancialAccountRepository {

    @Inject
    EntityManager em;

    @Inject
    UserContext userContext;

    @Override
    public List<FinancialAccount> findAll(boolean archived) {
        return FinancialAccountEntity.<FinancialAccountEntity>find(
                "archived = ?1 ORDER BY name, id", archived)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public Optional<FinancialAccount> findById(Long id) {
        return FinancialAccountEntity.<FinancialAccountEntity>find("id = ?1", id)
            .firstResultOptional()
            .map(this::toDomain);
    }

    @Override
    public FinancialAccount save(FinancialAccount account) {
        LocalDateTime now = LocalDateTime.now();
        FinancialAccountEntity entity = new FinancialAccountEntity();
        entity.user = em.getReference(UserEntity.class, userContext.getUserId());
        entity.name = account.getName();
        entity.kind = account.getKind();
        entity.openingBalance = account.getOpeningBalance();
        entity.openingDate = account.getOpeningDate();
        entity.archived = false;
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.persist();
        em.flush();
        return toDomain(entity);
    }

    @Override
    public boolean existsActiveByName(String name) {
        return FinancialAccountEntity.count(
            "LOWER(name) = LOWER(?1) AND archived = false", name) > 0;
    }

    @Override
    public boolean isTrackingActive() {
        return getTrackingActivationDate().isPresent();
    }

    @Override
    public Optional<LocalDate> getTrackingActivationDate() {
        UserEntity user = em.find(UserEntity.class, userContext.getUserId());
        return user == null ? Optional.empty() : Optional.ofNullable(user.accountTrackingActivatedAt);
    }

    @Override
    public void lockTrackingScope() {
        em.find(UserEntity.class, userContext.getUserId(), LockModeType.PESSIMISTIC_WRITE);
    }

    @Override
    public void activateTracking(LocalDate activationDate) {
        UserEntity user = em.find(UserEntity.class, userContext.getUserId(), LockModeType.PESSIMISTIC_WRITE);
        user.accountTrackingActivatedAt = activationDate;
        em.flush();
    }

    @Override
    public BigDecimal getTotalBalance() {
        Object raw = em.createNativeQuery("""
                SELECT COALESCE(SUM(account.opening_balance + COALESCE((
                    SELECT SUM(CASE WHEN tx.direction = 'INGRESS' THEN tx.amount ELSE -tx.amount END)
                    FROM transaction tx
                    WHERE tx.account_id = account.id
                      AND tx.user_id = :userId
                      AND tx.deleted_at IS NULL
                ), 0)), 0)
                FROM financial_account account
                WHERE account.user_id = :userId
                  AND account.archived = FALSE
                """)
            .setParameter("userId", userContext.getUserId())
            .getSingleResult();
        return money(raw);
    }

    private FinancialAccount toDomain(FinancialAccountEntity entity) {
        return new FinancialAccount(
            entity.id, entity.name, entity.kind, entity.openingBalance, entity.openingDate,
            balance(entity.id), entity.archived, entity.createdAt, entity.updatedAt, entity.version);
    }

    private BigDecimal balance(Long accountId) {
        Object raw = em.createNativeQuery("""
                SELECT account.opening_balance + COALESCE(SUM(
                    CASE WHEN tx.direction = 'INGRESS' THEN tx.amount ELSE -tx.amount END
                ), 0) + COALESCE((
                    SELECT SUM(transfer.amount)
                    FROM financial_account_transfer transfer
                    WHERE transfer.destination_account_id = account.id
                      AND transfer.user_id = :userId
                ), 0) - COALESCE((
                    SELECT SUM(transfer.amount)
                    FROM financial_account_transfer transfer
                    WHERE transfer.source_account_id = account.id
                      AND transfer.user_id = :userId
                ), 0)
                FROM financial_account account
                LEFT JOIN transaction tx
                  ON tx.account_id = account.id
                 AND tx.user_id = :userId
                 AND tx.deleted_at IS NULL
                WHERE account.id = :accountId
                  AND account.user_id = :userId
                GROUP BY account.id, account.opening_balance
                """)
            .setParameter("accountId", accountId)
            .setParameter("userId", userContext.getUserId())
            .getSingleResult();
        return money(raw);
    }

    private static BigDecimal money(Object raw) {
        return raw instanceof BigDecimal decimal ? decimal : new BigDecimal(raw.toString());
    }
}
