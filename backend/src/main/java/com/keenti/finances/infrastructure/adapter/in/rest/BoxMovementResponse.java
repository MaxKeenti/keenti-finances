package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BoxMovementResponse(
    Long id,
    String type,
    BigDecimal amount,
    LocalDate effectiveDate,
    LocalDateTime createdAt,
    BigDecimal runningBalance,
    Long relatedBoxId,
    String relatedBoxName,
    Long relatedTransactionId,
    String relatedTransactionDescription,
    boolean relatedTransactionChanged,
    boolean relatedTransactionRemoved
) {}
