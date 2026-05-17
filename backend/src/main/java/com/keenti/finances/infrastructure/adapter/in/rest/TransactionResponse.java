package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionResponse(
    Long id,
    BigDecimal amount,
    String direction,
    String description,
    LocalDate transactionDate,
    Long categoryId,
    String categoryName,
    String categoryColor,
    Long contactId,
    String contactName
) {}
