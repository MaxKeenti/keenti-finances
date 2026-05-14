package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.PaymentRecord;
import com.keenti.finances.domain.model.Subscription;
import com.keenti.finances.domain.model.SubscriptionMember;
import com.keenti.finances.domain.port.out.PaymentRecordRepository;
import com.keenti.finances.domain.port.out.SubscriptionMemberRepository;
import com.keenti.finances.domain.port.out.SubscriptionRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SubscriptionBillingScheduler {

    private static final Logger LOG = Logger.getLogger(SubscriptionBillingScheduler.class);
    private static final int LEAD_DAYS = 7;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    SubscriptionMemberRepository subscriptionMemberRepository;

    @Inject
    PaymentRecordRepository paymentRecordRepository;

    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void generateUpcomingPaymentRecords() {
        LocalDate cutoff = LocalDate.now().plusDays(LEAD_DAYS);
        List<Subscription> upcoming = subscriptionRepository.findWithNextBillingDateBefore(cutoff);
        AtomicInteger created = new AtomicInteger(0);

        for (Subscription sub : upcoming) {
            LocalDate billingDate = sub.getNextBillingDate();
            if ("SHARED".equals(sub.getType())) {
                List<SubscriptionMember> members = subscriptionMemberRepository.findBySubscriptionId(sub.getId());
                for (SubscriptionMember member : members) {
                    if (!paymentRecordRepository.existsBySubscriptionIdAndBillingDateAndMemberId(
                            sub.getId(), billingDate, member.getId())) {
                        paymentRecordRepository.save(new PaymentRecord(
                            null, sub.getId(), member.getId(), billingDate,
                            member.getShareAmount(), "PENDING", null, LocalDateTime.now()
                        ));
                        created.incrementAndGet();
                    }
                }
            } else {
                if (!paymentRecordRepository.existsBySubscriptionIdAndBillingDateAndMemberId(
                        sub.getId(), billingDate, null)) {
                    paymentRecordRepository.save(new PaymentRecord(
                        null, sub.getId(), null, billingDate,
                        sub.getCost(), "PENDING", null, LocalDateTime.now()
                    ));
                    created.incrementAndGet();
                }
            }
            // Advance nextBillingDate
            LocalDate nextDate = "MONTHLY".equals(sub.getBillingCycle())
                ? billingDate.plusMonths(1)
                : billingDate.plusYears(1);
            subscriptionRepository.update(new Subscription(
                sub.getId(), sub.getName(), sub.getCost(), sub.getBillingCycle(), sub.getType(),
                sub.getCategoryId(), nextDate, sub.getTokenUuid(), sub.getCreatedAt()
            ));
        }
        LOG.infof("scheduler.billing.run cutoff=%s subscriptions=%d recordsCreated=%d",
            cutoff, upcoming.size(), created.get());
    }
}
