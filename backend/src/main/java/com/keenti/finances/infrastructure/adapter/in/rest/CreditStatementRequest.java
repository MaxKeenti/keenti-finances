package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreditStatementRequest(@NotNull LocalDate periodStart, @NotNull LocalDate periodEnd,
    @NotNull LocalDate dueDate, @NotNull BigDecimal officialBalance,
    @NotNull BigDecimal officialMinimumPayment, @NotNull BigDecimal officialAvoidInterest,
    String officialNote) {}
