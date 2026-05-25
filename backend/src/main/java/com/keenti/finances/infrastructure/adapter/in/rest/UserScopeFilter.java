package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.infrastructure.adapter.out.persistence.UserEntity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.hibernate.Session;
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

        Session session = em.unwrap(Session.class);
        session.enableFilter("userScope").setParameter("userId", user.id);
        session.enableFilter("softDelete");
        LOG.infof("auth.workos.scope.enabled path=%s userId=%d", path, user.id);
    }

    private UserEntity provisionUser(String workosId) {
        UserEntity entity = new UserEntity();
        entity.username = "workos:" + workosId;
        entity.workosId = workosId;
        entity.passwordHash = null;

        try {
            em.persist(entity);
            em.flush();
            LOG.infof("auth.workos.jit_provisioned userId=%d workosId=%s", entity.id, workosId);
            defaultCategorySeeder.seedFor(entity);
            return entity;
        } catch (PersistenceException ex) {
            LOG.warnf("auth.workos.jit_race workosId=%s", workosId);
            return UserEntity.findByWorkosId(workosId)
                .orElseThrow(() -> ex);
        }
    }
}
