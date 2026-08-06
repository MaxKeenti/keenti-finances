package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FinancialAccountResponse(
    Long id,
    String name,
    String kind,
    BigDecimal openingBalance,
    LocalDate openingDate,
    BigDecimal balance,
    boolean archived,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    long version
) {}
