package com.keenti.finances.domain.port.out;

/**
 * A read-only, single-snapshot scope for the transaction already open on the
 * caller. {@link #begin()} must run before any other statement in that
 * transaction; {@link #discard()} ensures the transaction never commits.
 */
public interface ConsistentReadScope {
    void begin();
    void discard();
}
