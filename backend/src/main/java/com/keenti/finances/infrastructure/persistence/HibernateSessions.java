package com.keenti.finances.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;

public final class HibernateSessions {

    private HibernateSessions() {
    }

    public static Session unwrap(EntityManager entityManager) {
        Object unwrapped = entityManager.unwrap(Session.class);
        if (unwrapped instanceof Session session) {
            return session;
        }
        throw new IllegalStateException("EntityManager did not unwrap to a Hibernate Session");
    }
}
