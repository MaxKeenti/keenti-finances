package com.keenti.finances.domain.model;

import java.math.BigDecimal;

/**
 * A Payment Record owned by the current User on a Subscription not in the
 * trash, as a planning-receipt candidate. Ownership and the trash are filtered
 * by the query, so a trashed Subscription's record is as unknown as another
 * User's (matching trashed Debts); eligibility is the caller's decision, so an
 * owned but ineligible record is distinguishable from an unknown one.
 */
public record PaymentRecordReceiptCandidate(Long id, String status, BigDecimal amount,
                                            Long memberId) {
    /** PENDING, owed by a Subscription Member, within the preview's money bound. */
    public boolean eligible() {
        return "PENDING".equals(status) && memberId != null
            && PlanningPreviewCalculator.validAmount(amount);
    }
}
