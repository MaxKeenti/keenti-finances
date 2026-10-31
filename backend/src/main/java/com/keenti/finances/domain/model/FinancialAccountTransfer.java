package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A neutral, atomic movement between two Financial Accounts.
 *
 * <p>{@code amount} leaves the source in its currency and
 * {@code destinationAmount} arrives in the destination's. They are equal within
 * one currency; across currencies the Transfer is an exchange whose rate is
 * implied by the two amounts and never looked up (ADR-0024).
 */
public record FinancialAccountTransfer(
    Long id,
    Long sourceAccountId,
    Long destinationAccountId,
    BigDecimal amount,
    BigDecimal destinationAmount,
    LocalDate transferDate,
    String notes,
    LocalDateTime createdAt
) {}
