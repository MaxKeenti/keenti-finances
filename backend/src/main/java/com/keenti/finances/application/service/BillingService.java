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

    /**
     * Generate the Payment Record(s) for a single Subscription's current billing
     * period, on demand. Triggered manually from the Subscription detail page
     * (there is no scheduler — see ADR-0019).
     *
     * <p>Runs inside the HTTP request, so {@code UserScopedInterceptor} has
     * already engaged the {@code userScope} + {@code softDelete} Hibernate
     * filters: {@code findById} only resolves a non-deleted Subscription owned
     * by the caller. An unknown/foreign/trashed id yields {@link OptionalInt#empty()}.
     *
     * <p>Idempotent per period: an existing Payment Record for the
     * {@code (subscription, billingDate, member)} tuple is never duplicated, and
     * {@code nextBillingDate} only advances when at least one record was created
     * — so re-triggering an already-generated period is a no-op rather than a
     * runaway that keeps rolling the date forward. Unlike the former scheduler
     * there is no lead-time window: the next period is generated regardless of
     * how far off the billing date is.
     *
     * @return the number of records created, or empty if no such Subscription
     */
    @Transactional
    public OptionalInt generateForSubscription(Long subscriptionId) {
        Optional<Subscription> found = subscriptionRepository.findById(subscriptionId);
        if (found.isEmpty()) {
            return OptionalInt.empty();
        }
        Subscription sub = found.get();
        LocalDate billingDate = sub.getNextBillingDate();
        int created = 0;

        if ("SHARED".equals(sub.getType())) {
            List<SubscriptionMember> members = subscriptionMemberRepository.findBySubscriptionId(sub.getId());
            for (SubscriptionMember member : members) {
                if (!paymentRecordRepository.existsBySubscriptionIdAndBillingDateAndMemberId(
                        sub.getId(), billingDate, member.getId())) {
                    paymentRecordRepository.save(new PaymentRecord(
                        null, sub.getId(), member.getId(), billingDate,
                        member.getShareAmount(), "PENDING", null, LocalDateTime.now()
                    ));
                    created++;
                }
            }
        } else {
            if (!paymentRecordRepository.existsBySubscriptionIdAndBillingDateAndMemberId(
                    sub.getId(), billingDate, null)) {
                paymentRecordRepository.save(new PaymentRecord(
                    null, sub.getId(), null, billingDate,
                    sub.getCost(), "PENDING", null, LocalDateTime.now()
                ));
                created++;
            }
        }

        if (created > 0) {
            LocalDate nextDate = "MONTHLY".equals(sub.getBillingCycle())
                ? billingDate.plusMonths(1)
                : billingDate.plusYears(1);
            subscriptionRepository.update(new Subscription(
                sub.getId(), sub.getName(), sub.getCost(), sub.getBillingCycle(), sub.getType(),
                sub.getCategoryId(), nextDate, sub.getTokenUuid(), sub.getCreatedAt(),
                sub.isOwnerParticipates()
            ));
        }

        LOG.infof("billing.generate.subscription id=%d recordsCreated=%d", subscriptionId, created);
        return OptionalInt.of(created);
    }
}
