package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.CreditStatement;
import com.keenti.finances.domain.port.out.CreditStatementRepository;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import com.keenti.finances.infrastructure.adapter.in.rest.UserScoped;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@UserScoped
public class PanacheCreditStatementRepository implements CreditStatementRepository {

    @Inject EntityManager em;
    @Inject UserContext userContext;

    @Override
    public List<CreditStatement> findByAccountId(Long accountId) {
        return em.createQuery("""
                SELECT statement FROM CreditStatementEntity statement
                JOIN statement.account account
                WHERE account.id = :accountId AND account.user.id = :userId
                ORDER BY statement.periodEnd DESC, statement.id DESC
            """, CreditStatementEntity.class)
            .setParameter("accountId", accountId).setParameter("userId", userContext.getUserId())
            .getResultList().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<CreditStatement> findByAccountIdAndPeriod(Long accountId, LocalDate periodStart,
                                                               LocalDate periodEnd) {
        return em.createQuery("""
                SELECT statement FROM CreditStatementEntity statement
                JOIN statement.account account
                WHERE account.id = :accountId AND account.user.id = :userId
                  AND statement.periodStart = :periodStart AND statement.periodEnd = :periodEnd
                """, CreditStatementEntity.class)
            .setParameter("accountId", accountId).setParameter("userId", userContext.getUserId())
            .setParameter("periodStart", periodStart).setParameter("periodEnd", periodEnd)
            .getResultList().stream().findFirst().map(this::toDomain);
    }

    @Override
    public CreditStatement save(CreditStatement statement) {
        CreditStatementEntity entity = new CreditStatementEntity();
        entity.account = em.getReference(FinancialAccountEntity.class, statement.accountId());
        entity.periodStart = statement.periodStart();
        entity.periodEnd = statement.periodEnd();
        entity.dueDate = statement.dueDate();
        entity.estimatedBalance = statement.estimatedBalance();
        entity.officialBalance = statement.officialBalance();
        entity.officialMinimumPayment = statement.officialMinimumPayment();
        entity.officialAvoidInterest = statement.officialAvoidInterest();
        entity.officialNote = statement.officialNote();
        entity.confirmedAt = statement.confirmedAt();
        em.persist(entity);
        em.flush();
        return toDomain(entity);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void allocateOldestOutstanding(Long accountId, Long transferId, LocalDate paymentDate,
                                          BigDecimal amount) {
        BigDecimal remainingPayment = amount;
        List<Object[]> rows = em.createNativeQuery("""
                SELECT statement.id, statement.official_balance - COALESCE(SUM(payment.amount), 0)
                FROM credit_statement statement
                LEFT JOIN credit_statement_payment payment ON payment.statement_id = statement.id
                JOIN financial_account account ON account.id = statement.account_id
                WHERE statement.account_id = :accountId
                  AND account.user_id = :userId
                  AND statement.period_end <= :paymentDate
                  AND statement.official_balance IS NOT NULL
                GROUP BY statement.id, statement.official_balance, statement.period_end
                HAVING statement.official_balance - COALESCE(SUM(payment.amount), 0) > 0
                ORDER BY statement.period_end, statement.id
                """)
            .setParameter("accountId", accountId).setParameter("userId", userContext.getUserId())
            .setParameter("paymentDate", paymentDate).getResultList();

        for (Object[] row : rows) {
            if (remainingPayment.signum() <= 0) break;
            BigDecimal outstanding = decimal(row[1]);
            BigDecimal allocated = remainingPayment.min(outstanding);
            CreditStatementPaymentEntity payment = new CreditStatementPaymentEntity();
            payment.statement = em.getReference(CreditStatementEntity.class, ((Number) row[0]).longValue());
            payment.transfer = em.getReference(FinancialAccountTransferEntity.class, transferId);
            payment.amount = allocated;
            payment.createdAt = LocalDateTime.now();
            em.persist(payment);
            remainingPayment = remainingPayment.subtract(allocated);
        }
        em.flush();
    }

    private CreditStatement toDomain(CreditStatementEntity entity) {
        return new CreditStatement(entity.id, entity.account.id, entity.periodStart, entity.periodEnd,
            entity.dueDate, entity.estimatedBalance, entity.officialBalance,
            entity.officialMinimumPayment, entity.officialAvoidInterest, entity.officialNote,
            entity.confirmedAt, paidAmount(entity.id));
    }

    private BigDecimal paidAmount(Long statementId) {
        Object raw = em.createNativeQuery("""
                SELECT COALESCE(SUM(payment.amount), 0)
                FROM credit_statement_payment payment
                WHERE payment.statement_id = :statementId
                """).setParameter("statementId", statementId).getSingleResult();
        return decimal(raw);
    }

    private static BigDecimal decimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }
}
