package com.keenti.finances.domain.port.in;

import com.keenti.finances.domain.model.DashboardOverview;

/**
 * Composes the dashboard's five sections from existing recorded facts.
 *
 * <p>Read-only by contract: it never generates billing, never advances a
 * generation cursor, and never moves money between Boxes or Financial Accounts.
 * The one write it may cause is the idempotent lazy evaluation of Box Plan
 * periods that ADR-0021 already performs on every plan read.
 */
public interface DashboardOverviewUseCase {
    /** Null requests the current year in the User's time zone. */
    DashboardOverview getOverview(Integer year);
}
