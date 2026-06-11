package com.keenti.finances.application.service;

import com.keenti.finances.infrastructure.adapter.out.persistence.ContactEntity;
import com.keenti.finances.infrastructure.adapter.out.persistence.PaymentRecordEntity;
import com.keenti.finances.infrastructure.adapter.out.persistence.SubscriptionEntity;
import com.keenti.finances.infrastructure.adapter.out.persistence.SubscriptionMemberEntity;
import com.keenti.finances.infrastructure.adapter.out.persistence.UserEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class BillingServiceTest {

    @Inject
    BillingService billingService;

    @Inject
    EntityManager em;

    /**
     * The manual per-subscription trigger has no lead-time window (ADR-0019):
     * generating must work even when the next billing date is far in the future.
     * This is the exact case the old scheduler skipped. A record is created and
     * nextBillingDate advances by one cycle.
     */
    @Test
    @Transactional
    void generatesForSubscriptionBeyondLeadWindow() {
        UserEntity user = ensureUser("test-billing-beyond-window");
        SubscriptionEntity sub = personalSubscriptionDueIn(user, 30);
        LocalDate originalDate = sub.nextBillingDate;

        OptionalInt created = billingService.generateForSubscription(sub.id);

        assertEquals(OptionalInt.of(1), created, "one record generated regardless of date");
        assertEquals(1, PaymentRecordEntity.count("subscription.id = ?1", sub.id));
        assertEquals(originalDate.plusMonths(1), reload(sub).nextBillingDate,
            "nextBillingDate advances one monthly cycle after generation");
    }

    /**
     * Idempotent per period: re-triggering a period whose Payment Record already
     * exists creates nothing and does NOT advance nextBillingDate — so a second
     * click is a safe no-op rather than a runaway that keeps rolling the date.
     */
    @Test
    @Transactional
    void idempotentWhenPeriodAlreadyGenerated() {
        UserEntity user = ensureUser("test-billing-idempotent");
        SubscriptionEntity sub = personalSubscriptionDueIn(user, 3);
        LocalDate originalDate = sub.nextBillingDate;
        existingRecord(sub);

        OptionalInt created = billingService.generateForSubscription(sub.id);

        assertEquals(OptionalInt.of(0), created, "already-generated period creates nothing");
        assertEquals(1, PaymentRecordEntity.count("subscription.id = ?1", sub.id),
            "no duplicate Payment Record");
        assertEquals(originalDate, reload(sub).nextBillingDate,
            "nextBillingDate must not advance when nothing was generated");
    }

    /**
     * A SHARED subscription generates one PENDING record per member, each for
     * that member's share.
     */
    @Test
    @Transactional
    void generatesOneRecordPerMemberForSharedSubscription() {
        UserEntity user = ensureUser("test-billing-shared");
        SubscriptionEntity sub = sharedSubscriptionDueIn(user, 5);
        member(sub, contact(user, "Member A"), "50.00");
        member(sub, contact(user, "Member B"), "50.00");

        OptionalInt created = billingService.generateForSubscription(sub.id);

        assertEquals(OptionalInt.of(2), created, "one record per member");
        assertEquals(2, PaymentRecordEntity.count("subscription.id = ?1", sub.id));
    }

    /**
     * An unknown (or foreign/soft-deleted) Subscription resolves to empty so the
     * resource can return 404.
     */
    @Test
    @Transactional
    void returnsEmptyForUnknownSubscription() {
        assertEquals(OptionalInt.empty(), billingService.generateForSubscription(999_999_999L));
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
        return subscriptionDueIn(user, days, "PERSONAL");
    }

    private SubscriptionEntity sharedSubscriptionDueIn(UserEntity user, int days) {
        return subscriptionDueIn(user, days, "SHARED");
    }

    private SubscriptionEntity subscriptionDueIn(UserEntity user, int days, String type) {
        SubscriptionEntity s = new SubscriptionEntity();
        s.name = "Test sub " + UUID.randomUUID();
        s.cost = new BigDecimal("100.00");
        s.billingCycle = "MONTHLY";
        s.type = type;
        s.nextBillingDate = LocalDate.now().plusDays(days);
        s.tokenUuid = UUID.randomUUID().toString();
        s.createdAt = LocalDateTime.now();
        s.ownerParticipates = true;
        s.user = user;
        s.persist();
        em.flush();
        return s;
    }

    private ContactEntity contact(UserEntity user, String name) {
        ContactEntity c = new ContactEntity();
        c.name = name;
        c.user = user;
        c.persist();
        em.flush();
        return c;
    }

    private SubscriptionMemberEntity member(SubscriptionEntity sub, ContactEntity contact, String share) {
        SubscriptionMemberEntity m = new SubscriptionMemberEntity();
        m.subscription = sub;
        m.contact = contact;
        m.shareAmount = new BigDecimal(share);
        m.createdAt = LocalDateTime.now();
        m.persist();
        em.flush();
        return m;
    }

    private PaymentRecordEntity existingRecord(SubscriptionEntity sub) {
        PaymentRecordEntity pr = new PaymentRecordEntity();
        pr.subscription = sub;
        pr.member = null;
        pr.billingDate = sub.nextBillingDate;
        pr.amount = sub.cost;
        pr.status = "PENDING";
        pr.createdAt = LocalDateTime.now();
        pr.persist();
        em.flush();
        return pr;
    }

    private SubscriptionEntity reload(SubscriptionEntity sub) {
        em.flush();
        em.clear();
        return SubscriptionEntity.findById(sub.id);
    }
}
