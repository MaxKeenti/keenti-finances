package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** An independent Box deposit initially applied with an INGRESS Transaction. */
public record BoxDistribution(
    Long id,
    Long transactionId,
    Long boxId,
    String boxName,
    BigDecimal amount,
    int lineOrder,
    LocalDate effectiveDate,
    LocalDateTime createdAt
) {

    public BoxDistribution(Long boxId, BigDecimal amount, int lineOrder) {
        this(null, null, boxId, null, amount, lineOrder, null, null);
    }
}
