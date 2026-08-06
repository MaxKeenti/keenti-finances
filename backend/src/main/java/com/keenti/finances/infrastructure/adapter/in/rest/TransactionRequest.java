package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TransactionRequest(
    @NotNull BigDecimal amount,
    @NotBlank String direction,
    String description,
    @NotNull LocalDate transactionDate,
    @NotNull Long categoryId,
    Long contactId,
    Long accountId,
    List<@Valid BoxFundingRequest> boxFunding,
    List<@Valid BoxDistributionRequest> boxDistributions
) {

    public TransactionRequest(BigDecimal amount, String direction, String description,
                              LocalDate transactionDate, Long categoryId, Long contactId) {
        this(amount, direction, description, transactionDate, categoryId, contactId, null,
            List.of(), List.of());
    }
}
