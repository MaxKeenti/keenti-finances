package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.PaymentRecord;
import com.keenti.finances.infrastructure.adapter.out.persistence.CategoryEntity;
import com.keenti.finances.infrastructure.adapter.out.persistence.PaymentRecordEntity;
import com.keenti.finances.infrastructure.adapter.out.persistence.SubscriptionEntity;
import com.keenti.finances.infrastructure.adapter.out.persistence.SubscriptionMemberEntity;
import com.keenti.finances.infrastructure.adapter.out.persistence.TransactionEntity;
import com.keenti.finances.infrastructure.adapter.out.persistence.UserEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class PaymentRecordServiceTest {

    @Inject
    PaymentRecordService paymentRecordService;

    @Inject
    EntityManager em;

    /**
     * Linking a Transaction to a PENDING Payment Record marks it PAID, dated to
     * the transaction, records which Transaction settled it, and surfaces the
     * transaction under the subscription (link-subscription side effect).
     */
    @Test
    @Transactional
    void linkTransactionMarksRecordPaidAndDatesItToTheTransaction() {
        UserEntity user = ensureUser("test-link-paid");
        SubscriptionEntity sub = sharedSubscription(user);
        SubscriptionMemberEntity member = member(sub, contact(user, "Member A"));
        PaymentRecordEntity record = pendingRecord(sub, member, "50.00");
        LocalDate txDate = LocalDate.now().minusDays(2);
        TransactionEntity tx = egressTransaction(user, "Netflix", "50.00", txDate);

        PaymentRecord updated = paymentRecordService.linkTransaction(sub.id, record.id, tx.id);

        assertEquals("PAID", updated.getStatus());
        assertEquals(txDate, updated.getPaidDate(), "paidDate follows the transaction date");
        assertEquals(tx.id, updated.getTransactionId());

        em.flush();
        em.clear();
        PaymentRecordEntity reloaded = PaymentRecordEntity.findById(record.id);
        assertEquals("PAID", reloaded.status);
        assertEquals(tx.id, reloaded.transaction.id);
        TransactionEntity reloadedTx = TransactionEntity.findById(tx.id);
        assertEquals(sub.id, reloadedTx.subscription.id, "transaction is linked to the subscription too");
    }

    /**
     * Linking is rejected on an already-PAID record (409), mirroring recordPayment.
     */
    @Test
    @Transactional
    void linkTransactionRejectsAlreadyPaidRecord() {
        UserEntity user = ensureUser("test-link-paid-conflict");
        SubscriptionEntity sub = sharedSubscription(user);
        SubscriptionMemberEntity member = member(sub, contact(user, "Member B"));
        PaymentRecordEntity record = pendingRecord(sub, member, "50.00");
        record.status = "PAID";
        em.flush();
        TransactionEntity tx = egressTransaction(user, "Spotify", "50.00", LocalDate.now());

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> paymentRecordService.linkTransaction(sub.id, record.id, tx.id));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @Transactional
    void linkTransactionRejectsIngressTransaction() {
        UserEntity user = ensureUser("test-link-ingress-rejected");
        SubscriptionEntity sub = sharedSubscription(user);
        SubscriptionMemberEntity member = member(sub, contact(user, "Member C"));
        PaymentRecordEntity record = pendingRecord(sub, member, "50.00");
        TransactionEntity tx = ingressTransaction(user, "Refund", "50.00", LocalDate.now());

        assertThrows(BadRequestException.class,
            () -> paymentRecordService.linkTransaction(sub.id, record.id, tx.id));
    }

    @Test
    @Transactional
    void deleteBillingPeriodRemovesOnlyUnlinkedRecordsAndMakesItRegenerable() {
        UserEntity user = ensureUser("test-delete-period");
        SubscriptionEntity sub = sharedSubscription(user);
        SubscriptionMemberEntity firstMember = member(sub, contact(user, "Member D"));
        SubscriptionMemberEntity secondMember = member(sub, contact(user, "Member E"));
        LocalDate billingDate = LocalDate.now().minusMonths(1);
        sub.nextBillingDate = LocalDate.now().plusMonths(1);
        PaymentRecordEntity first = pendingRecord(sub, firstMember, "50.00");
        first.billingDate = billingDate;
        PaymentRecordEntity second = pendingRecord(sub, secondMember, "50.00");
        second.billingDate = billingDate;
        em.flush();

        assertEquals(2, paymentRecordService.deleteBillingPeriod(sub.id, billingDate));

        em.flush();
        em.clear();
        assertEquals(0, PaymentRecordEntity.count("subscription.id = ?1 AND billingDate = ?2", sub.id, billingDate));
        assertEquals(billingDate, SubscriptionEntity.findById(sub.id).nextBillingDate);
        assertNull(PaymentRecordEntity.findById(first.id));
        assertNull(PaymentRecordEntity.findById(second.id));
    }

    @Test
    @Transactional
    void deleteBillingPeriodRejectsTheEntirePeriodWhenAnyRecordHasATransaction() {
        UserEntity user = ensureUser("test-delete-linked-period");
        SubscriptionEntity sub = sharedSubscription(user);
        SubscriptionMemberEntity firstMember = member(sub, contact(user, "Member F"));
        SubscriptionMemberEntity secondMember = member(sub, contact(user, "Member G"));
        PaymentRecordEntity first = pendingRecord(sub, firstMember, "50.00");
        PaymentRecordEntity second = pendingRecord(sub, secondMember, "50.00");
        first.transaction = egressTransaction(user, "Netflix", "50.00", LocalDate.now());
        em.flush();

        WebApplicationException exception = assertThrows(WebApplicationException.class,
            () -> paymentRecordService.deleteBillingPeriod(sub.id, sub.nextBillingDate));
        assertEquals(409, exception.getResponse().getStatus());
        assertEquals(2, PaymentRecordEntity.count("subscription.id = ?1", sub.id));
        assertEquals(first.id, PaymentRecordEntity.findById(first.id).id);
        assertEquals(second.id, PaymentRecordEntity.findById(second.id).id);
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

    private SubscriptionEntity sharedSubscription(UserEntity user) {
        SubscriptionEntity s = new SubscriptionEntity();
        s.name = "Test sub " + UUID.randomUUID();
        s.cost = new BigDecimal("100.00");
        s.billingCycle = "MONTHLY";
        s.type = "SHARED";
        s.nextBillingDate = LocalDate.now();
        s.tokenUuid = UUID.randomUUID().toString();
        s.createdAt = LocalDateTime.now();
        s.ownerParticipates = true;
        s.user = user;
        s.persist();
        em.flush();
        return s;
    }

    private CategoryEntity category(UserEntity user) {
        CategoryEntity c = new CategoryEntity();
        c.name = "Subs " + UUID.randomUUID();
        c.type = "EGRESS";
        c.hue = 200;
        c.user = user;
        c.persist();
        em.flush();
        return c;
    }

    private com.keenti.finances.infrastructure.adapter.out.persistence.ContactEntity contact(UserEntity user, String name) {
        var c = new com.keenti.finances.infrastructure.adapter.out.persistence.ContactEntity();
        c.name = name;
        c.user = user;
        c.persist();
        em.flush();
        return c;
    }

    private SubscriptionMemberEntity member(SubscriptionEntity sub,
            com.keenti.finances.infrastructure.adapter.out.persistence.ContactEntity contact) {
        SubscriptionMemberEntity m = new SubscriptionMemberEntity();
        m.subscription = sub;
        m.contact = contact;
        m.shareAmount = new BigDecimal("50.00");
        m.createdAt = LocalDateTime.now();
        m.persist();
        em.flush();
        return m;
    }

    private PaymentRecordEntity pendingRecord(SubscriptionEntity sub, SubscriptionMemberEntity member, String amount) {
        PaymentRecordEntity pr = new PaymentRecordEntity();
        pr.subscription = sub;
        pr.member = member;
        pr.billingDate = sub.nextBillingDate;
        pr.amount = new BigDecimal(amount);
        pr.status = "PENDING";
        pr.createdAt = LocalDateTime.now();
        pr.persist();
        em.flush();
        return pr;
    }

    private TransactionEntity egressTransaction(UserEntity user, String description, String amount, LocalDate date) {
        return transaction(user, description, amount, date, "EGRESS");
    }

    private TransactionEntity ingressTransaction(UserEntity user, String description, String amount, LocalDate date) {
        return transaction(user, description, amount, date, "INGRESS");
    }

    private TransactionEntity transaction(UserEntity user, String description, String amount, LocalDate date, String direction) {
        TransactionEntity t = new TransactionEntity();
        t.amount = new BigDecimal(amount);
        t.direction = direction;
        t.description = description;
        t.transactionDate = date;
        t.category = category(user);
        t.createdAt = LocalDateTime.now();
        t.user = user;
        t.persist();
        em.flush();
        return t;
    }
}
