package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BoxMovement(
    Long id,
    Type type,
    Long sourceBoxId,
    Long destinationBoxId,
    BigDecimal amount,
    LocalDate effectiveDate,
    LocalDateTime createdAt,
    Long sourceTransactionId,
    Long sourceTransactionReference,
    Integer sourceTransactionOrder,
    boolean sourceTransactionChanged
) {
    public BoxMovement(Long id, Type type, Long sourceBoxId, Long destinationBoxId,
                       BigDecimal amount, LocalDate effectiveDate, LocalDateTime createdAt) {
        this(id, type, sourceBoxId, destinationBoxId, amount, effectiveDate, createdAt,
            null, null, null, false);
    }

    public enum Type {
        DEPOSIT,
        WITHDRAWAL,
        TRANSFER
    }
}
