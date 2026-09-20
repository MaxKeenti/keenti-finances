package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.PaymentRecord;
import com.keenti.finances.domain.model.PendingContribution;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaymentRecordRepository {
    List<PaymentRecord> findBySubscriptionId(Long subscriptionId);

    /**
     * Every {@code PENDING} Member contribution the current User is owed, in one
     * query.
     *
     * <p>Walking Subscriptions and loading each one's full payment history to
     * keep the pending rows reads every {@code PAID} record a User has ever
     * accumulated — a list that only grows — to answer a question about the few
     * that are still open.
     *
     * <p>What the query excludes, and why each exclusion is the domain's own:
     * another User's Subscriptions; a Subscription in the trash; the Personal
     * Subscription's own charge ({@code member_id IS NULL}), which is the
     * Owner's price rather than money another person owes; and anything not
     * {@code PENDING} — a {@code PAID} record is received whether or not a
     * Transaction was ever linked to it. Zero-amount records are also excluded:
     * this projection counts positive amounts owed, not all open payment statuses.
     */
    List<PendingContribution> findPendingContributions();
    List<PaymentRecord> findBySubscriptionIdAndBillingDateForUpdate(
        Long subscriptionId, LocalDate billingDate);
    Optional<PaymentRecord> findById(Long id);
    PaymentRecord save(PaymentRecord record);
    PaymentRecord update(PaymentRecord record);
    boolean existsBySubscriptionIdAndBillingDateAndMemberId(Long subscriptionId, LocalDate billingDate, Long memberId);
    void deleteByIds(List<Long> ids);
}
