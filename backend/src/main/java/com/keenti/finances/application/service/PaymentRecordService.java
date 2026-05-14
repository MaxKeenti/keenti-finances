package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.PaymentRecord;
import com.keenti.finances.domain.port.in.PaymentRecordUseCase;
import com.keenti.finances.domain.port.out.PaymentRecordRepository;
import com.keenti.finances.domain.port.out.SubscriptionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
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
            record.getBillingDate(), record.getAmount(), "PAID", LocalDate.now(), record.getCreatedAt()
        ));
        LOG.infof("payment.record paymentId=%d subscriptionId=%d status=PAID", paymentId, subscriptionId);
        return updated;
    }
}
