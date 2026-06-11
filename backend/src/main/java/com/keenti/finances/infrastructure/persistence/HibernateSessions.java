package com.keenti.finances.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;

public final class HibernateSessions {

    private HibernateSessions() {
    }

    public static Session unwrap(EntityManager entityManager) {
        if (entityManager instanceof Session session) {
            return session;
        }
        Object delegate = entityManager.getDelegate();
        if (delegate instanceof Session session) {
            return session;
        }
        throw new IllegalStateException("EntityManager did not unwrap to a Hibernate Session");
    }
}
