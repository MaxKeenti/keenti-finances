package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.PaymentRecord;
import com.keenti.finances.domain.model.Subscription;
import com.keenti.finances.domain.model.Transaction;
import com.keenti.finances.domain.port.in.PaymentRecordUseCase;
import com.keenti.finances.domain.port.in.TransactionUseCase;
import com.keenti.finances.domain.port.out.PaymentRecordRepository;
import com.keenti.finances.domain.port.out.SubscriptionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.time.LocalDate;
import java.util.List;
import org.jboss.logging.Logger;

@ApplicationScoped
public class PaymentRecordService implements PaymentRecordUseCase {

    private static final Logger LOG = Logger.getLogger(PaymentRecordService.class);

    @Inject
    PaymentRecordRepository paymentRecordRepository;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    TransactionUseCase transactionUseCase;

    @Override
    public List<PaymentRecord> listBySubscription(Long subscriptionId) {
        subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new NotFoundException("Subscription not found: " + subscriptionId));
        List<PaymentRecord> records = paymentRecordRepository.findBySubscriptionId(subscriptionId);
        LOG.infof("payment.list subscriptionId=%d count=%d", subscriptionId, records.size());
        return records;
    }

    @Override
    @Transactional
    public PaymentRecord recordPayment(Long subscriptionId, Long paymentId) {
        // Verify the subscription itself is visible to the current User before
        // touching any of its child Payment Records. Without this guard,
        // paymentRecordRepository.findById(paymentId) below is a primary-key
        // load that bypasses Hibernate's userScope filter — an attacker who
        // knew a valid (subscriptionId, paymentId) pair belonging to someone
        // else could mark that record PAID. The subscription repository is
        // @UserScoped so this findById correctly 404s for non-owned rows.
        subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new NotFoundException("Subscription not found: " + subscriptionId));
        PaymentRecord record = paymentRecordRepository.findById(paymentId)
            .orElseThrow(() -> new NotFoundException("Payment record not found: " + paymentId));
        if (!record.getSubscriptionId().equals(subscriptionId)) {
            throw new NotFoundException("Payment record not found for subscription: " + subscriptionId);
        }
        if ("PAID".equals(record.getStatus())) {
            throw new jakarta.ws.rs.WebApplicationException(
                jakarta.ws.rs.core.Response.status(409)
                    .entity("{\"error\":\"Payment record is already PAID\"}")
                    .build());
        }
        PaymentRecord updated = paymentRecordRepository.update(new PaymentRecord(
            record.getId(), record.getSubscriptionId(), record.getMemberId(),
            record.getBillingDate(), record.getAmount(), "PAID", LocalDate.now(),
            record.getTransactionId(), record.getCreatedAt()
        ));
        LOG.infof("payment.record paymentId=%d subscriptionId=%d status=PAID", paymentId, subscriptionId);
        return updated;
    }

    @Override
    @Transactional
    public PaymentRecord linkTransaction(Long subscriptionId, Long paymentId, Long transactionId) {
        // Same ownership guard as recordPayment: resolve the @UserScoped subscription
        // first so the by-id Payment Record load below cannot reach another User's row.
        subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new NotFoundException("Subscription not found: " + subscriptionId));
        PaymentRecord record = paymentRecordRepository.findById(paymentId)
            .orElseThrow(() -> new NotFoundException("Payment record not found: " + paymentId));
        if (!record.getSubscriptionId().equals(subscriptionId)) {
            throw new NotFoundException("Payment record not found for subscription: " + subscriptionId);
        }
        if ("PAID".equals(record.getStatus())) {
            throw new jakarta.ws.rs.WebApplicationException(
                jakarta.ws.rs.core.Response.status(409)
                    .entity("{\"error\":\"Payment record is already PAID\"}")
                    .build());
        }
        // getById is User-scoped: an unknown/foreign transaction id 404s.
        Transaction tx = transactionUseCase.getById(transactionId)
            .orElseThrow(() -> new NotFoundException("Transaction not found: " + transactionId));

        // Linking ties the real money movement to the period: mark PAID, dated to
        // the transaction, and record which Transaction settled it.
        PaymentRecord updated = paymentRecordRepository.update(new PaymentRecord(
            record.getId(), record.getSubscriptionId(), record.getMemberId(),
            record.getBillingDate(), record.getAmount(), "PAID", tx.getTransactionDate(),
            tx.getId(), record.getCreatedAt()
        ));
        // Surface the transaction under the subscription's "Linked Transactions" too.
        transactionUseCase.linkSubscription(transactionId, subscriptionId);
        LOG.infof("payment.link paymentId=%d subscriptionId=%d transactionId=%d status=PAID",
            paymentId, subscriptionId, transactionId);
        return updated;
    }

    @Override
    @Transactional
    public int deleteBillingPeriod(Long subscriptionId, LocalDate billingDate) {
        Subscription subscription = subscriptionRepository.findByIdForUpdate(subscriptionId)
            .orElseThrow(() -> new NotFoundException("Subscription not found: " + subscriptionId));
        List<PaymentRecord> records = paymentRecordRepository
            .findBySubscriptionIdAndBillingDateForUpdate(subscriptionId, billingDate);
        if (records.isEmpty()) {
            throw new NotFoundException("No payment records found for billing date: " + billingDate);
        }
        if (records.stream().anyMatch(record -> record.getTransactionId() != null)) {
            throw new WebApplicationException(Response.status(Response.Status.CONFLICT)
                .entity("{\"error\":\"Payment records with linked transactions cannot be deleted\"}")
                .build());
        }

        paymentRecordRepository.deleteByIds(records.stream().map(PaymentRecord::getId).toList());
        if (billingDate.isBefore(subscription.getNextBillingDate())) {
            subscriptionRepository.update(new Subscription(
                subscription.getId(), subscription.getName(), subscription.getCost(),
                subscription.getBillingCycle(), subscription.getType(), subscription.getCategoryId(),
                billingDate, subscription.getTokenUuid(), subscription.getCreatedAt(),
                subscription.isOwnerParticipates()));
        }
        LOG.infof("payment.delete_period subscriptionId=%d billingDate=%s count=%d",
            subscriptionId, billingDate, records.size());
        return records.size();
    }
}
