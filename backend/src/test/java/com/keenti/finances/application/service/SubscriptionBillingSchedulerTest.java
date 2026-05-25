package com.keenti.finances.application.service;

import com.keenti.finances.infrastructure.adapter.out.persistence.PaymentRecordEntity;
import com.keenti.finances.infrastructure.adapter.out.persistence.SubscriptionEntity;
import com.keenti.finances.infrastructure.adapter.out.persistence.UserEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class SubscriptionBillingSchedulerTest {

    @Inject
    SubscriptionBillingScheduler scheduler;

    @Inject
    EntityManager em;

    /**
     * Re-running the cron should not create duplicate Payment Records for the
     * same Subscription on the same billing date. ADR-0006 calls this out as a
     * core invariant; the existence check in BillingService should hold.
     */
    @Test
    @Transactional
    void cron_isIdempotent_acrossRuns() {
        UserEntity user = ensureUser("test-billing-idempotent");
        SubscriptionEntity sub = personalSubscriptionDueIn(user, 3);

        scheduler.generateUpcomingPaymentRecords();
        scheduler.generateUpcomingPaymentRecords();

        long records = PaymentRecordEntity.count("subscription.id = ?1", sub.id);
        assertEquals(1, records, "second cron run must not duplicate the Payment Record");
    }

    /**
     * Subscriptions whose nextBillingDate is more than 7 days in the future
     * must be skipped (lead-time invariant, ADR-0006).
     */
    @Test
    @Transactional
    void cron_skipsSubscriptionsBeyondLeadWindow() {
        UserEntity user = ensureUser("test-billing-lead-window");
        SubscriptionEntity inWindow = personalSubscriptionDueIn(user, 5);
        SubscriptionEntity beyondWindow = personalSubscriptionDueIn(user, 30);

        scheduler.generateUpcomingPaymentRecords();

        assertEquals(1, PaymentRecordEntity.count("subscription.id = ?1", inWindow.id),
            "Subscription within the 7-day lead window must be billed");
        assertEquals(0, PaymentRecordEntity.count("subscription.id = ?1", beyondWindow.id),
            "Subscription beyond the 7-day lead window must not be billed");
    }

    /**
     * The cron runs outside any HTTP request, so the softDelete Hibernate
     * @Filter is not auto-enabled by UserScopeFilter. The scheduler must
     * enable it explicitly — soft-deleted Subscriptions must not be billed.
     */
    @Test
    @Transactional
    void cron_skipsSoftDeletedSubscriptions() {
        UserEntity user = ensureUser("test-billing-soft-delete");
        SubscriptionEntity active = personalSubscriptionDueIn(user, 3);
        SubscriptionEntity trashed = personalSubscriptionDueIn(user, 3);
        trashed.deletedAt = LocalDateTime.now();
        em.flush();

        scheduler.generateUpcomingPaymentRecords();

        assertEquals(1, PaymentRecordEntity.count("subscription.id = ?1", active.id),
            "active Subscription must be billed");
        assertEquals(0, PaymentRecordEntity.count("subscription.id = ?1", trashed.id),
            "soft-deleted Subscription must not be billed");
    }

    // --- fixtures ---

    private UserEntity ensureUser(String workosId) {
        return UserEntity.findByWorkosId(workosId).orElseGet(() -> {
            UserEntity u = new UserEntity();
            u.username = "workos:" + workosId;
            u.workosId = workosId;
            u.passwordHash = null;
            u.persist();
            return u;
        });
    }

    private SubscriptionEntity personalSubscriptionDueIn(UserEntity user, int days) {
        SubscriptionEntity s = new SubscriptionEntity();
        s.name = "Test sub " + UUID.randomUUID();
        s.cost = new BigDecimal("100.00");
        s.billingCycle = "MONTHLY";
        s.type = "PERSONAL";
        s.nextBillingDate = LocalDate.now().plusDays(days);
        s.tokenUuid = UUID.randomUUID().toString();
        s.createdAt = LocalDateTime.now();
        s.ownerParticipates = true;
        s.user = user;
        s.persist();
        em.flush();
        return s;
    }
}
