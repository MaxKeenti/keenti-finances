package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.CreditMsiPlan;
import com.keenti.finances.domain.port.out.CreditMsiPlanRepository;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import com.keenti.finances.infrastructure.adapter.in.rest.UserScoped;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@UserScoped
public class PanacheCreditMsiPlanRepository implements CreditMsiPlanRepository {

    @Inject EntityManager em;
    @Inject UserContext userContext;

    @Override
    public List<CreditMsiPlan> findByAccountId(Long accountId) {
        return query("account.id = :id", accountId).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean hasActiveByAccountId(Long accountId) {
        return findByAccountId(accountId).stream().anyMatch(CreditMsiPlan::active);
    }

    @Override
    public boolean existsByTransactionId(Long transactionId) {
        return em.createQuery("""
                SELECT COUNT(plan) FROM CreditMsiPlanEntity plan
                JOIN plan.account account
                WHERE plan.transaction.id = :id AND account.user.id = :userId
                """, Long.class)
            .setParameter("id", transactionId).setParameter("userId", userContext.getUserId())
            .getSingleResult() > 0;
    }

    @Override
    public CreditMsiPlan save(CreditMsiPlan plan) {
        CreditMsiPlanEntity entity = new CreditMsiPlanEntity();
        entity.account = em.getReference(FinancialAccountEntity.class, plan.accountId());
        if (plan.transactionId() != null) {
            entity.transaction = em.getReference(TransactionEntity.class, plan.transactionId());
        }
        entity.purchaseAmount = plan.purchaseAmount();
        entity.installmentCount = plan.installmentCount();
        entity.firstInstallmentDate = plan.firstInstallmentDate();
        entity.openingBalanceAmount = plan.openingBalanceAmount();
        entity.endedAt = plan.endedAt();
        entity.endReason = plan.endReason();
        em.persist(entity);
        em.flush();
        return toDomain(entity);
    }

    @Override
    public Optional<CreditMsiPlan> findById(Long id) {
        return em.createQuery("""
                SELECT plan FROM CreditMsiPlanEntity plan JOIN plan.account account
                WHERE plan.id = :id AND account.user.id = :userId
                """, CreditMsiPlanEntity.class)
            .setParameter("id", id).setParameter("userId", userContext.getUserId())
            .getResultStream().findFirst().map(this::toDomain);
    }

    @Override
    public CreditMsiPlan end(Long id, LocalDateTime endedAt, String endReason) {
        CreditMsiPlanEntity entity = entity(id);
        entity.endedAt = endedAt;
        entity.endReason = endReason;
        em.flush();
        return toDomain(entity);
    }

    @Override
    public BigDecimal statementBalanceAdjustment(Long accountId, LocalDate periodEnd) {
        return findByAccountId(accountId).stream()
            .map(plan -> adjustment(plan, periodEnd))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal adjustment(CreditMsiPlan plan, LocalDate periodEnd) {
        BigDecimal purchaseOffset = plan.openingBalanceAmount();
        if (plan.transactionId() != null) {
            CreditMsiPlanEntity entity = entity(plan.id());
            if (entity.transaction.transactionDate.isAfter(periodEnd)) {
                purchaseOffset = BigDecimal.ZERO;
            } else {
                purchaseOffset = plan.purchaseAmount();
            }
        }
        LocalDate scheduleCutoff = periodEnd;
        if (plan.endedAt() != null && plan.endedAt().toLocalDate().isBefore(scheduleCutoff)) {
            scheduleCutoff = plan.endedAt().toLocalDate();
        }
        int due = 0;
        for (int installment = 0; installment < plan.installmentCount(); installment++) {
            if (!plan.firstInstallmentDate().plusMonths(installment).isAfter(scheduleCutoff)) {
                due++;
            }
        }
        return purchaseOffset.subtract(plan.installmentAmount().multiply(BigDecimal.valueOf(due)));
    }

    private List<CreditMsiPlanEntity> query(String condition, Long accountId) {
        return em.createQuery("""
                SELECT plan FROM CreditMsiPlanEntity plan JOIN plan.account account
                WHERE """ + condition + " AND account.user.id = :userId ORDER BY plan.firstInstallmentDate, plan.id",
                CreditMsiPlanEntity.class)
            .setParameter("id", accountId).setParameter("userId", userContext.getUserId())
            .getResultList();
    }

    private CreditMsiPlanEntity entity(Long id) {
        return em.createQuery("""
                SELECT plan FROM CreditMsiPlanEntity plan JOIN plan.account account
                WHERE plan.id = :id AND account.user.id = :userId
                """, CreditMsiPlanEntity.class)
            .setParameter("id", id).setParameter("userId", userContext.getUserId())
            .getResultStream().findFirst().orElseThrow();
    }

    private CreditMsiPlan toDomain(CreditMsiPlanEntity entity) {
        return new CreditMsiPlan(entity.id, entity.account.id,
            entity.transaction == null ? null : entity.transaction.id,
            entity.purchaseAmount, entity.installmentCount, entity.firstInstallmentDate,
            entity.openingBalanceAmount, entity.endedAt, entity.endReason);
    }
}
