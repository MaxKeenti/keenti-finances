package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.PaymentRecord;
import com.keenti.finances.domain.model.Subscription;
import com.keenti.finances.domain.model.SubscriptionMember;
import com.keenti.finances.domain.port.out.PaymentRecordRepository;
import com.keenti.finances.domain.port.out.SubscriptionMemberRepository;
import com.keenti.finances.domain.port.out.SubscriptionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.jboss.logging.Logger;

@ApplicationScoped
public class BillingService {

    private static final Logger LOG = Logger.getLogger(BillingService.class);

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    SubscriptionMemberRepository subscriptionMemberRepository;

    @Inject
    PaymentRecordRepository paymentRecordRepository;

    /** Safety bound: never roll through more periods than this in one click,
     * in case a corrupted {@code nextBillingDate} sits far in the past. */
    private static final int MAX_CATCH_UP_PERIODS = 600;

    /**
     * Generate the Payment Record(s) for a Subscription, catching up to the
     * current period on demand. Triggered manually from the Subscription detail
     * page (there is no scheduler — see ADR-0019).
     *
     * <p>Runs inside the HTTP request, so {@code UserScopedInterceptor} has
     * already engaged the {@code userScope} + {@code softDelete} Hibernate
     * filters: {@code findById} only resolves a non-deleted Subscription owned
     * by the caller. An unknown/foreign/trashed id yields {@link OptionalInt#empty()}.
     *
     * <p><b>Backfill semantics.</b> A single click generates a record set for
     * every period from {@code nextBillingDate} up to and including the current
     * period (any {@code billingDate <= today}), advancing {@code nextBillingDate}
     * to the first period strictly after today. This builds the full payment
     * history in one action so past periods are browsable. A Subscription whose
     * {@code nextBillingDate} is in the future generates nothing until it falls due.
     *
     * <p>Idempotent per period: an existing Payment Record for the
     * {@code (subscription, billingDate, member)} tuple is never duplicated, and
     * {@code nextBillingDate} only advances when the catch-up loop actually ran —
     * so re-triggering an already-caught-up Subscription is a no-op.
     *
     * @return the number of records created, or empty if no such Subscription
     */
    @Transactional
    public OptionalInt generateForSubscription(Long subscriptionId) {
        // Serialize billing generation per subscription. The database uniqueness
        // constraints are the final guard, while this lock keeps concurrent
        // requests idempotent instead of surfacing a constraint violation.
        Optional<Subscription> found = subscriptionRepository.findByIdForUpdate(subscriptionId);
        if (found.isEmpty()) {
            return OptionalInt.empty();
        }
        Subscription sub = found.get();
        LocalDate today = LocalDate.now();
        LocalDate billingDate = sub.getNextBillingDate();
        int created = 0;
        int periods = 0;

        while (!billingDate.isAfter(today) && periods < MAX_CATCH_UP_PERIODS) {
            created += generatePeriod(sub, billingDate);
            billingDate = "MONTHLY".equals(sub.getBillingCycle())
                ? billingDate.plusMonths(1)
                : billingDate.plusYears(1);
            periods++;
        }

        if (periods > 0 && !billingDate.equals(sub.getNextBillingDate())) {
            subscriptionRepository.update(new Subscription(
                sub.getId(), sub.getName(), sub.getCost(), sub.getBillingCycle(), sub.getType(),
                sub.getCategoryId(), billingDate, sub.getTokenUuid(), sub.getCreatedAt(),
                sub.isOwnerParticipates()
            ));
        }

        LOG.infof("billing.generate.subscription id=%d periods=%d recordsCreated=%d",
            subscriptionId, periods, created);
        return OptionalInt.of(created);
    }

    /** Idempotently create the Payment Record(s) for one billing period. */
    private int generatePeriod(Subscription sub, LocalDate billingDate) {
        int created = 0;
        if ("SHARED".equals(sub.getType())) {
            List<SubscriptionMember> members = subscriptionMemberRepository.findBySubscriptionId(sub.getId());
            for (SubscriptionMember member : members) {
                if (!paymentRecordRepository.existsBySubscriptionIdAndBillingDateAndMemberId(
                        sub.getId(), billingDate, member.getId())) {
                    paymentRecordRepository.save(new PaymentRecord(
                        null, sub.getId(), member.getId(), billingDate,
                        member.getShareAmount(), "PENDING", null, null, LocalDateTime.now()
                    ));
                    created++;
                }
            }
        } else {
            if (!paymentRecordRepository.existsBySubscriptionIdAndBillingDateAndMemberId(
                    sub.getId(), billingDate, null)) {
                paymentRecordRepository.save(new PaymentRecord(
                    null, sub.getId(), null, billingDate,
                    sub.getCost(), "PENDING", null, null, LocalDateTime.now()
                ));
                created++;
            }
        }
        return created;
    }
}
