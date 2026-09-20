package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.PaymentRecord;
import com.keenti.finances.domain.model.PendingContribution;
import com.keenti.finances.domain.port.out.PaymentRecordRepository;
import com.keenti.finances.infrastructure.adapter.in.rest.UserContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanachePaymentRecordRepository implements PaymentRecordRepository {

    @Inject
    EntityManager em;

    @Inject
    UserContext userContext;

    @Override
    public List<PaymentRecord> findBySubscriptionId(Long subscriptionId) {
        return PaymentRecordEntity.<PaymentRecordEntity>find(
            "subscription.id = ?1 ORDER BY billingDate DESC", subscriptionId)
            .stream().map(this::toDomain).toList();
    }

    /**
     * Payment Records carry no {@code user_id} of their own, so the owning
     * Subscription's is the predicate — spelled out here rather than left to a
     * Hibernate filter, because this is a native query and no filter applies to
     * one. The Contact is joined only while it is not in the trash, which keeps
     * the money owed visible without naming a Contact the User has deleted.
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<PendingContribution> findPendingContributions() {
        List<Object[]> rows = em.createNativeQuery(
            "SELECT pr.id, s.id, s.name, m.id, c.id, c.name, pr.amount, pr.billing_date "
            + "FROM payment_record pr "
            + "JOIN subscription s ON pr.subscription_id = s.id "
            + "JOIN subscription_member m ON pr.member_id = m.id "
            + "LEFT JOIN contact c ON m.contact_id = c.id AND c.deleted_at IS NULL "
            + "WHERE s.user_id = :userId AND s.deleted_at IS NULL "
            + "AND pr.status = 'PENDING' AND pr.amount > 0 "
            + "ORDER BY pr.billing_date, pr.id")
            .setParameter("userId", userContext.getUserId())
            .getResultList();

        return rows.stream()
            .map(row -> new PendingContribution(
                toLong(row[0]), toLong(row[1]), (String) row[2], toLong(row[3]),
                toLong(row[4]), (String) row[5],
                toBigDecimal(row[6]), toLocalDate(row[7])))
            .toList();
    }

    private static Long toLong(Object raw) {
        return raw == null ? null : ((Number) raw).longValue();
    }

    private static BigDecimal toBigDecimal(Object raw) {
        return raw instanceof BigDecimal decimal ? decimal : new BigDecimal(raw.toString());
    }

    private static LocalDate toLocalDate(Object raw) {
        if (raw == null) return null;
        if (raw instanceof LocalDate date) return date;
        if (raw instanceof Date date) return date.toLocalDate();
        return LocalDate.parse(raw.toString());
    }

    @Override
    public List<PaymentRecord> findBySubscriptionIdAndBillingDateForUpdate(
            Long subscriptionId, LocalDate billingDate) {
        return PaymentRecordEntity.<PaymentRecordEntity>find(
            "subscription.id = ?1 AND billingDate = ?2", subscriptionId, billingDate)
            .withLock(LockModeType.PESSIMISTIC_WRITE)
            .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<PaymentRecord> findById(Long id) {
        return PaymentRecordEntity.<PaymentRecordEntity>findByIdOptional(id).map(this::toDomain);
    }

    @Override
    public PaymentRecord save(PaymentRecord record) {
        PaymentRecordEntity entity = toEntity(record);
        entity.persist();
        return toDomain(entity);
    }

    @Override
    public PaymentRecord update(PaymentRecord record) {
        PaymentRecordEntity entity = PaymentRecordEntity.findById(record.getId());
        entity.status = record.getStatus();
        entity.paidDate = record.getPaidDate();
        entity.transaction = record.getTransactionId() != null
            ? TransactionEntity.findById(record.getTransactionId()) : null;
        return toDomain(entity);
    }

    @Override
    public boolean existsBySubscriptionIdAndBillingDateAndMemberId(Long subscriptionId, LocalDate billingDate, Long memberId) {
        if (memberId == null) {
            return PaymentRecordEntity.count(
                "subscription.id = ?1 AND billingDate = ?2 AND member IS NULL",
                subscriptionId, billingDate) > 0;
        }
        return PaymentRecordEntity.count(
            "subscription.id = ?1 AND billingDate = ?2 AND member.id = ?3",
            subscriptionId, billingDate, memberId) > 0;
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (!ids.isEmpty()) {
            PaymentRecordEntity.delete("id IN ?1", ids);
        }
    }

    private PaymentRecordEntity toEntity(PaymentRecord r) {
        PaymentRecordEntity e = new PaymentRecordEntity();
        e.subscription = SubscriptionEntity.findById(r.getSubscriptionId());
        e.member = r.getMemberId() != null ? SubscriptionMemberEntity.findById(r.getMemberId()) : null;
        e.billingDate = r.getBillingDate();
        e.amount = r.getAmount();
        e.status = r.getStatus();
        e.paidDate = r.getPaidDate();
        e.transaction = r.getTransactionId() != null
            ? TransactionEntity.findById(r.getTransactionId()) : null;
        e.createdAt = r.getCreatedAt();
        return e;
    }

    private PaymentRecord toDomain(PaymentRecordEntity e) {
        return new PaymentRecord(
            e.id,
            e.subscription != null ? e.subscription.id : null,
            e.member != null ? e.member.id : null,
            e.billingDate,
            e.amount,
            e.status,
            e.paidDate,
            e.transaction != null ? e.transaction.id : null,
            e.createdAt
        );
    }
}
