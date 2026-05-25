package com.keenti.finances.application.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.hibernate.Session;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SubscriptionBillingScheduler {

    private static final Logger LOG = Logger.getLogger(SubscriptionBillingScheduler.class);

    @Inject
    BillingService billingService;

    @Inject
    EntityManager em;

    /**
     * Cron-driven billing run. Runs outside any HTTP request so the
     * {@link com.keenti.finances.infrastructure.adapter.in.rest.UserScopeFilter}
     * does not fire — we must enable the {@code softDelete} Hibernate filter
     * explicitly here so soft-deleted Subscriptions aren't billed. The
     * {@code userScope} filter is intentionally NOT enabled: the cron job
     * processes every active Subscription across every User.
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void generateUpcomingPaymentRecords() {
        LOG.info("scheduler.billing.start");
        Session session = em.unwrap(Session.class);
        session.enableFilter("softDelete");
        try {
            int count = billingService.generateBilling();
            LOG.infof("scheduler.billing.complete recordsCreated=%d", count);
        } finally {
            session.disableFilter("softDelete");
        }
    }
}
