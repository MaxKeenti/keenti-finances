package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.infrastructure.adapter.out.persistence.UserEntity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class UserScopeFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(UserScopeFilter.class);
    private static final String WORKOS_HEADER = "X-WorkOS-User-Id";

    @Inject
    UserContext userContext;

    @Inject
    EntityManager em;

    @Inject
    DefaultCategorySeeder defaultCategorySeeder;

    @Override
    @Transactional
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        boolean isPublic = path != null && (path.startsWith("api/public") || path.startsWith("/api/public"));
        String workosId = requestContext.getHeaderString(WORKOS_HEADER);

        if (workosId == null || workosId.isBlank()) {
            if (!isPublic) {
                LOG.warnf("auth.workos.header.missing path=%s", path);
                requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\":\"Missing WorkOS user\"}")
                    .build());
            }
            return;
        }

        userContext.setWorkosId(workosId);

        UserEntity user = UserEntity.findByWorkosId(workosId)
            .orElseGet(() -> provisionUser(workosId));

        userContext.setUserId(user.id);
        // The Hibernate userScope + softDelete filters are activated by
        // UserScopedInterceptor inside each resource's transactional scope —
        // this filter's @Transactional opens a separate session that ends
        // before the resource method runs, so enabling filters here was a
        // no-op cross-session call that caused a cross-user data leak.
        LOG.infof("auth.workos.scope.resolved path=%s userId=%d", path, user.id);
    }

    private UserEntity provisionUser(String workosId) {
        // A first page load fans out into several API requests. Use a database
        // upsert so those requests can safely race without poisoning the
        // Hibernate persistence context with a failed INSERT. PostgreSQL makes
        // competing inserts wait for the winning transaction before returning
        // zero, so the lookup below sees the committed User.
        int inserted = em.createNativeQuery("""
                INSERT INTO app_user (username, password_hash, workos_id)
                VALUES (:username, NULL, :workosId)
                ON CONFLICT DO NOTHING
                """)
            .setParameter("username", "workos:" + workosId)
            .setParameter("workosId", workosId)
            .executeUpdate();

        UserEntity entity = UserEntity.findByWorkosId(workosId)
            .orElseThrow(() -> new IllegalStateException(
                "Unable to provision WorkOS user " + workosId));

        if (inserted == 1) {
            LOG.infof("auth.workos.jit_provisioned userId=%d workosId=%s", entity.id, workosId);
            defaultCategorySeeder.seedFor(entity);
        } else {
            LOG.infof("auth.workos.jit_race_resolved userId=%d workosId=%s", entity.id, workosId);
        }
        return entity;
    }
}
