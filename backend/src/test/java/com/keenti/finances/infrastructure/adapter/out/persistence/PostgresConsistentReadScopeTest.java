package com.keenti.finances.infrastructure.adapter.out.persistence;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The planning snapshot's isolation on a real PostgreSQL transaction, through
 * the same {@code @Transactional(REQUIRES_NEW)} shape PlanningSnapshotReader uses.
 */
@QuarkusTest
class PostgresConsistentReadScopeTest {

    @Inject
    AbortedSnapshotProbe probe;

    @Test
    void theSnapshotIsRepeatableReadAndRefusesWrites() {
        assertEquals("repeatable read,on", probe.isolationSettings());
        assertTrue(probe.writeRefused());
    }

    @Test
    void aCommitBetweenQueriesIsOnlyVisibleInTheNextSnapshot() {
        String name = "snapshot-" + java.util.UUID.randomUUID();
        assertEquals(java.util.List.of(0L, 0L), probe.countsAcrossConcurrentCommit(name));
        assertEquals(1L, probe.countInFreshSnapshot(name));
    }

    /**
     * A failed statement aborts only its own section's transaction, which still
     * ends quietly, and the next section reads normally.
     */
    @Test
    void aFailedSectionDoesNotPoisonTheNextOne() {
        assertEquals("unavailable", probe.failingSection());
        assertEquals(42, probe.healthySection());
    }
}
