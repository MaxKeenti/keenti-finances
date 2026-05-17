package com.keenti.finances.application.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SubscriptionBillingScheduler {

    private static final Logger LOG = Logger.getLogger(SubscriptionBillingScheduler.class);

    @Inject
    BillingService billingService;

    @Scheduled(cron = "0 0 1 * * ?")
    public void generateUpcomingPaymentRecords() {
        LOG.info("scheduler.billing.start");
        int count = billingService.generateBilling();
        LOG.infof("scheduler.billing.complete recordsCreated=%d", count);
    }
}
