package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.infrastructure.adapter.out.persistence.CategoryEntity;
import com.keenti.finances.infrastructure.adapter.out.persistence.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Seeds a fixed set of starter Categories the first time a User is provisioned
 * (see {@link UserScopeFilter#provisionUser}). The list is intentionally small
 * and aspirational — new Users land on a populated Categories page so the app
 * is usable on first login without a wizard step (see CAPABILITIES "First-login
 * onboarding wizard" Out-of-scope rationale).
 */
@ApplicationScoped
public class DefaultCategorySeeder {

    private static final Logger LOG = Logger.getLogger(DefaultCategorySeeder.class);

    private record Seed(String name, String type, int hue) {}

    private static final Seed[] STARTERS = new Seed[] {
        new Seed("Salary",                  "INGRESS", 100),
        new Seed("Other income",            "INGRESS", 120),
        new Seed("Supermarkets",            "EGRESS",   30),
        new Seed("Restaurants",             "EGRESS",    0),
        new Seed("Transportation",          "EGRESS",    0),
        new Seed("Mobile, TV and Internet", "EGRESS",   20),
        new Seed("Rent / Mortgage",         "EGRESS",   10),
        new Seed("Clothing",                "EGRESS",   30),
        new Seed("Pharmacies",              "EGRESS",   40),
        new Seed("Subscriptions",           "EGRESS",   10),
        new Seed("Entertainment",           "EGRESS",  350),
        new Seed("Transfers",               "BOTH",    270),
    };

    public void seedFor(UserEntity user) {
        for (Seed s : STARTERS) {
            CategoryEntity c = new CategoryEntity();
            c.name = s.name();
            c.type = s.type();
            c.hue = s.hue();
            c.user = user;
            c.persist();
        }
        LOG.infof("category.defaults_seeded userId=%d count=%d", user.id, STARTERS.length);
    }
}
