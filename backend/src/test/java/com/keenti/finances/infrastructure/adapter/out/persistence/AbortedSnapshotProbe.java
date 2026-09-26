package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.port.out.ConsistentReadScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

/** Test-only bean mirroring PlanningSnapshotReader's transaction shape. */
@ApplicationScoped
public class AbortedSnapshotProbe {

    @Inject
    ConsistentReadScope readScope;

    @Inject
    EntityManager em;

    @Inject SnapshotCommitProbe writer;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public java.util.List<Long> countsAcrossConcurrentCommit(String name) {
        try {
            readScope.begin();
            long before = count(name);
            writer.insert(name); // Commits while this snapshot remains open.
            return java.util.List.of(before, count(name));
        } finally {
            readScope.discard();
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public long countInFreshSnapshot(String name) {
        try {
            readScope.begin();
            return count(name);
        } finally {
            readScope.discard();
        }
    }

    private long count(String name) {
        return ((Number) em.createNativeQuery("SELECT count(*) FROM app_user WHERE username = :name")
            .setParameter("name", name).getSingleResult()).longValue();
    }

    /** A section whose read fails: caught, discarded, returned normally. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public String failingSection() {
        try {
            readScope.begin();
            em.createNativeQuery("SELECT 1 / 0").getSingleResult();
            return "read";
        } catch (PersistenceException e) {
            return "unavailable";
        } finally {
            readScope.discard();
        }
    }

    /** The next section in its own transaction. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public int healthySection() {
        try {
            readScope.begin();
            return ((Number) em.createNativeQuery("SELECT 42").getSingleResult()).intValue();
        } finally {
            readScope.discard();
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public String isolationSettings() {
        try {
            readScope.begin();
            return (String) em.createNativeQuery("SELECT current_setting('transaction_isolation')"
                + " || ',' || current_setting('transaction_read_only')").getSingleResult();
        } finally {
            readScope.discard();
        }
    }

    /** True when PostgreSQL refuses the write. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean writeRefused() {
        try {
            readScope.begin();
            em.createNativeQuery("UPDATE app_user SET time_zone = time_zone WHERE FALSE").executeUpdate();
            return false;
        } catch (PersistenceException e) {
            return true;
        } finally {
            readScope.discard();
        }
    }
}
