package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.User;
import com.keenti.finances.domain.port.out.UserRepository;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.hibernate.Session;

@Provider
public class UserScopeFilter implements ContainerRequestFilter {

    @Inject
    UserContext userContext;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager em;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        if (path.startsWith("/api/public") || path.startsWith("/q/")) {
            return;
        }

        String workosId = ctx.getHeaderString("X-WorkOS-User-Id");
        if (workosId == null || workosId.isBlank()) {
            ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
            return;
        }

        User user = userRepository.findByWorkosId(workosId).orElseGet(() -> {
            try {
                return userRepository.save(new User(null, workosId, null, workosId));
            } catch (Exception e) {
                return userRepository.findByWorkosId(workosId)
                        .orElseThrow(() -> new RuntimeException("Failed to provision user", e));
            }
        });

        userContext.setUserId(user.getId());

        em.unwrap(Session.class)
          .enableFilter("userScope")
          .setParameter("userId", user.getId());
    }
}
