package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.infrastructure.persistence.HibernateSessions;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;

/**
 * Activates the {@code userScope} and {@code softDelete} Hibernate filters on
 * the active session before the intercepted method runs. Priority is set to
 * {@link Interceptor.Priority#APPLICATION}, which is higher than Quarkus's
 * {@code @Transactional} interceptor — so by the time this fires the request's
 * transaction (and its session) is already open. Idempotent: if a filter is
 * already enabled (multiple intercepted calls within the same session, or the
 * cron path which enables its own softDelete), we skip.
 */
@Interceptor
@UserScoped
@Priority(Interceptor.Priority.APPLICATION)
public class UserScopedInterceptor {

    @Inject
    EntityManager em;

    @Inject
    UserContext userContext;

    @AroundInvoke
    public Object intercept(InvocationContext ctx) throws Exception {
        Session session = HibernateSessions.unwrap(em);

        if (userContext.getUserId() != null && session.getEnabledFilter("userScope") == null) {
            session.enableFilter("userScope").setParameter("userId", userContext.getUserId());
        }
        if (session.getEnabledFilter("softDelete") == null) {
            session.enableFilter("softDelete");
        }

        return ctx.proceed();
    }
}
