package com.keenti.finances.infrastructure.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** A separate committed transaction interleaved with a planning snapshot. */
@ApplicationScoped
public class SnapshotCommitProbe {
    @Inject EntityManager em;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void insert(String name) {
        em.createNativeQuery("INSERT INTO app_user (username) VALUES (:name)")
            .setParameter("name", name).executeUpdate();
    }
}
