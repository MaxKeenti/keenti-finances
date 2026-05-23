package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.DebtPayment;
import com.keenti.finances.domain.port.out.DebtPaymentRepository;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

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
                .collect(Collectors.toList());
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
            "INNER JOIN debt d ON dp.debt_id = d.id " +
            "WHERE dp.debt_id = :debtId AND d.user_id = :userId")
            .setParameter("debtId", debtId)
            .setParameter("userId", userContext.getUserId())
            .getSingleResult();
        return raw instanceof BigDecimal ? (BigDecimal) raw : new BigDecimal(raw.toString());
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
