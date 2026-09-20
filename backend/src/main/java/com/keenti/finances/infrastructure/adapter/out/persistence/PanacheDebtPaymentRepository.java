package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.DebtPayment;
import com.keenti.finances.domain.port.out.DebtPaymentRepository;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class PanacheDebtPaymentRepository implements DebtPaymentRepository {

    @Inject
    EntityManager em;

    @Inject
    UserContext userContext;

    @Override
    public List<DebtPayment> findByDebtId(Long debtId) {
        return DebtPaymentEntity.<DebtPaymentEntity>find(
                "debt.id = ?1 ORDER BY paymentDate DESC", debtId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public DebtPayment save(DebtPayment payment) {
        DebtPaymentEntity entity = toEntity(payment);
        entity.persist();
        return toDomain(entity);
    }

    @Override
    public BigDecimal sumByDebtId(Long debtId) {
        Object raw = em.createNativeQuery(
            "SELECT COALESCE(SUM(dp.amount), 0) FROM debt_payment dp " +
            "JOIN debt d ON dp.debt_id = d.id " +
            "WHERE dp.debt_id = :debtId AND d.user_id = :userId")
            .setParameter("debtId", debtId)
            .setParameter("userId", userContext.getUserId())
            .getSingleResult();
        return raw instanceof BigDecimal ? (BigDecimal) raw : new BigDecimal(raw.toString());
    }

    /**
     * Same user-scoped join as {@link #sumByDebtId}, grouped instead of filtered
     * to one Debt. The {@code user_id} predicate is what keeps another User's
     * payments out of the total even though the caller supplies the IDs.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<Long, BigDecimal> sumByDebtIds(List<Long> debtIds) {
        if (debtIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = em.createNativeQuery(
            "SELECT dp.debt_id, COALESCE(SUM(dp.amount), 0) FROM debt_payment dp " +
            "JOIN debt d ON dp.debt_id = d.id " +
            "WHERE d.user_id = :userId AND dp.debt_id IN (:debtIds) " +
            "GROUP BY dp.debt_id")
            .setParameter("userId", userContext.getUserId())
            .setParameter("debtIds", debtIds)
            .getResultList();

        Map<Long, BigDecimal> totals = new HashMap<>();
        for (Object[] row : rows) {
            totals.put(((Number) row[0]).longValue(), toBigDecimal(row[1]));
        }
        return totals;
    }

    private static BigDecimal toBigDecimal(Object raw) {
        return raw instanceof BigDecimal decimal ? decimal : new BigDecimal(raw.toString());
    }

    private DebtPaymentEntity toEntity(DebtPayment p) {
        DebtPaymentEntity e = new DebtPaymentEntity();
        e.debt = DebtEntity.findById(p.getDebtId());
        e.amount = p.getAmount();
        e.paymentDate = p.getPaymentDate();
        e.transaction = p.getTransactionId() != null
                ? TransactionEntity.findById(p.getTransactionId())
                : null;
        e.notes = p.getNotes();
        return e;
    }

    private DebtPayment toDomain(DebtPaymentEntity e) {
        return new DebtPayment(
            e.id,
            e.debt != null ? e.debt.id : null,
            e.amount,
            e.paymentDate,
            e.transaction != null ? e.transaction.id : null,
            e.notes,
            e.createdAt
        );
    }
}
