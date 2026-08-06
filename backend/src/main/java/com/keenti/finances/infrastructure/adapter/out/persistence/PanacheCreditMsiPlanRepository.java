package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.CreditMsiPlan;
import com.keenti.finances.domain.port.out.CreditMsiPlanRepository;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import com.keenti.finances.infrastructure.adapter.in.rest.UserScoped;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;

@ApplicationScoped @UserScoped
public class PanacheCreditMsiPlanRepository implements CreditMsiPlanRepository {
    @Inject EntityManager em; @Inject UserContext userContext;
    @Override public List<CreditMsiPlan> findByAccountId(Long accountId) {
        return em.createQuery("SELECT plan FROM CreditMsiPlanEntity plan JOIN plan.account account WHERE account.id = :id AND account.user.id = :userId ORDER BY plan.firstInstallmentDate", CreditMsiPlanEntity.class)
            .setParameter("id", accountId).setParameter("userId", userContext.getUserId()).getResultList().stream().map(this::toDomain).toList();
    }
    @Override public boolean hasActiveByAccountId(Long accountId) { return findByAccountId(accountId).stream().anyMatch(CreditMsiPlan::active); }
    @Override public boolean existsByTransactionId(Long transactionId) { return em.createQuery("SELECT COUNT(plan) FROM CreditMsiPlanEntity plan JOIN plan.account account WHERE plan.transaction.id = :id AND account.user.id = :userId", Long.class).setParameter("id", transactionId).setParameter("userId", userContext.getUserId()).getSingleResult() > 0; }
    @Override public CreditMsiPlan save(CreditMsiPlan plan) { CreditMsiPlanEntity entity = new CreditMsiPlanEntity(); entity.account = em.getReference(FinancialAccountEntity.class, plan.accountId()); entity.transaction = em.getReference(TransactionEntity.class, plan.transactionId()); entity.purchaseAmount = plan.purchaseAmount(); entity.installmentCount = plan.installmentCount(); entity.firstInstallmentDate = plan.firstInstallmentDate(); em.persist(entity); em.flush(); return toDomain(entity); }
    private CreditMsiPlan toDomain(CreditMsiPlanEntity entity) { return new CreditMsiPlan(entity.id, entity.account.id, entity.transaction.id, entity.purchaseAmount, entity.installmentCount, entity.firstInstallmentDate, entity.cancelledAt == null); }
}
