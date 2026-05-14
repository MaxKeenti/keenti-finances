package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
    @NotNull BigDecimal amount,
    @NotBlank String direction,
    String description,
    @NotNull LocalDate transactionDate,
    @NotNull Long categoryId,
    Long contactId
) {}
