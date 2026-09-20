package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DebtResponse(
    Long id,
    Long contactId,
    String contactName,
    String direction,
    String description,
    BigDecimal totalAmount,
    BigDecimal totalPaid,
    BigDecimal remaining,
    String status,
    LocalDateTime createdAt
) {}
