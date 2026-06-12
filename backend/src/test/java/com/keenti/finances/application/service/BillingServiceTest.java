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
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class BillingServiceTest {

    @Inject
    BillingService billingService;

    @Inject
    EntityManager em;

    /**
     * Backfill (ADR-0019): a single click catches up every overdue period from
     * nextBillingDate through today, so past payment history is built in one
     * action. A subscription ~90 days overdue gets at least 3 monthly records and
     * nextBillingDate lands on the first period strictly after today.
     */
    @Test
    @Transactional
    void backfillsOverduePeriodsInOneClick() {
        UserEntity user = ensureUser("test-billing-backfill");
        SubscriptionEntity sub = personalSubscriptionDueIn(user, -90);

        OptionalInt created = billingService.generateForSubscription(sub.id);

        assertTrue(created.getAsInt() >= 3, "catches up at least three overdue monthly periods");
        assertEquals(created.getAsInt(), PaymentRecordEntity.count("subscription.id = ?1", sub.id),
            "one record per caught-up period");
        assertTrue(reload(sub).nextBillingDate.isAfter(LocalDate.now()),
            "nextBillingDate advances past today");
    }

    /**
     * After catching up, a second click is a safe no-op: nothing is created and
     * nextBillingDate (now in the future) does not advance.
     */
    @Test
    @Transactional
    void secondClickAfterCatchUpIsNoOp() {
        UserEntity user = ensureUser("test-billing-idempotent");
        SubscriptionEntity sub = personalSubscriptionDueIn(user, -40);
        billingService.generateForSubscription(sub.id);
        long countAfterFirst = PaymentRecordEntity.count("subscription.id = ?1", sub.id);
        LocalDate dateAfterFirst = reload(sub).nextBillingDate;

        OptionalInt created = billingService.generateForSubscription(sub.id);

        assertEquals(OptionalInt.of(0), created, "already caught up — nothing created");
        assertEquals(countAfterFirst, PaymentRecordEntity.count("subscription.id = ?1", sub.id),
            "no duplicate Payment Records");
        assertEquals(dateAfterFirst, reload(sub).nextBillingDate,
            "nextBillingDate must not advance when nothing was generated");
    }

    /**
     * A future-dated subscription generates nothing until it falls due — backfill
     * never creates records ahead of today (ADR-0019).
     */
    @Test
    @Transactional
    void futureDatedSubscriptionGeneratesNothing() {
        UserEntity user = ensureUser("test-billing-future");
        SubscriptionEntity sub = personalSubscriptionDueIn(user, 30);
        LocalDate originalDate = sub.nextBillingDate;

        OptionalInt created = billingService.generateForSubscription(sub.id);

        assertEquals(OptionalInt.of(0), created, "future period is not generated yet");
        assertEquals(0, PaymentRecordEntity.count("subscription.id = ?1", sub.id));
        assertEquals(originalDate, reload(sub).nextBillingDate, "nextBillingDate unchanged");
    }

    /**
     * A SHARED subscription due today generates one PENDING record per member,
     * each for that member's share.
     */
    @Test
    @Transactional
    void generatesOneRecordPerMemberForSharedSubscription() {
        UserEntity user = ensureUser("test-billing-shared");
        SubscriptionEntity sub = sharedSubscriptionDueIn(user, 0);
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


    private SubscriptionEntity reload(SubscriptionEntity sub) {
        em.flush();
        em.clear();
        return SubscriptionEntity.findById(sub.id);
    }
}
