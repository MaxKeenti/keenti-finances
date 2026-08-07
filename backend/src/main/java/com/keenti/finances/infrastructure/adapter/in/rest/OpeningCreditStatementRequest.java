package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Official Credit Statement already outstanding when Account tracking begins. */
public record OpeningCreditStatementRequest(
    @NotNull LocalDate periodStart,
    @NotNull LocalDate periodEnd,
    @NotNull LocalDate dueDate,
    @NotNull BigDecimal officialBalance,
    @NotNull BigDecimal officialMinimumPayment,
    @NotNull BigDecimal officialAvoidInterest,
    String officialNote
) {}
