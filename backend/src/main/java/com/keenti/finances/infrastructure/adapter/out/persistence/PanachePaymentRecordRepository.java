package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.PaymentRecord;
import com.keenti.finances.domain.port.out.PaymentRecordRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanachePaymentRecordRepository implements PaymentRecordRepository {

    @Override
    public List<PaymentRecord> findBySubscriptionId(Long subscriptionId) {
        return PaymentRecordEntity.<PaymentRecordEntity>find(
            "subscription.id = ?1 ORDER BY billingDate DESC", subscriptionId)
            .stream().map(this::toDomain).toList();
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
