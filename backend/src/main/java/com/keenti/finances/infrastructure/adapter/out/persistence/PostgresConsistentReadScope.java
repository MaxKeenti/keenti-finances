package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.port.out.ConsistentReadScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;

/**
 * PostgreSQL's default READ COMMITTED gives each statement its own snapshot, so
 * a Net Balance and a Box total read a moment apart can straddle a concurrent
 * commit. REPEATABLE READ fixes one snapshot at the transaction's first query
 * for every later statement; READ ONLY makes the database itself refuse any
 * write. SET TRANSACTION applies to the current transaction only, so nothing
 * leaks into the pooled connection.
 */
@ApplicationScoped
public class PostgresConsistentReadScope implements ConsistentReadScope {

    @Inject
    EntityManager em;

    @Inject
    TransactionManager transactionManager;

    @Override
    public void begin() {
        em.createNativeQuery("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY")
            .executeUpdate();
    }

    @Override
    public void discard() {
        try {
            // The @Transactional interceptor rolls back a transaction marked
            // rollback-only without raising an error. (QuarkusTransaction.run
            // does raise RollbackException, so callers must use the interceptor.)
            transactionManager.setRollbackOnly();
        } catch (SystemException | IllegalStateException e) {
            throw new IllegalStateException("Unable to discard read-only snapshot", e);
        }
    }
}
