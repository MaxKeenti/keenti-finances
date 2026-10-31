package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FinancialAccountTransferRequest(
    @NotNull Long sourceAccountId,
    @NotNull Long destinationAccountId,
    /** What left the source Account, in its currency. */
    @NotNull @Positive BigDecimal amount,
    /**
     * What arrived in the destination Account, in its currency. Required when
     * the Accounts use different currencies; defaults to {@code amount} otherwise.
     */
    @Positive BigDecimal destinationAmount,
    @NotNull LocalDate transferDate,
    String notes
) {
    public FinancialAccountTransferRequest(Long sourceAccountId, Long destinationAccountId,
                                           BigDecimal amount, LocalDate transferDate, String notes) {
        this(sourceAccountId, destinationAccountId, amount, null, transferDate, notes);
    }
}
