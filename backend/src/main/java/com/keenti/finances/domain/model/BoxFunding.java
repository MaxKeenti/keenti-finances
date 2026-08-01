package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One ordered allocation of an EGRESS Transaction to a Box.
 *
 * <p>The remaining Transaction amount is funded from Available to Spend. A
 * funding line is part of the Transaction lifecycle rather than a standalone
 * Box Movement.</p>
 */
public record BoxFunding(
    Long id,
    Long transactionId,
    Long boxId,
    String boxName,
    BigDecimal amount,
    int lineOrder,
    LocalDate effectiveDate,
    LocalDateTime createdAt
) {

    public BoxFunding(Long boxId, BigDecimal amount, int lineOrder) {
        this(null, null, boxId, null, amount, lineOrder, null, null);
    }
}
