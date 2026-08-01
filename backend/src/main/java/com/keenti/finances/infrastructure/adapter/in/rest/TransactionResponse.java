package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TransactionResponse(
    Long id,
    BigDecimal amount,
    String direction,
    String description,
    LocalDate transactionDate,
    Long categoryId,
    String categoryName,
    Integer categoryHue,
    Long contactId,
    String contactName,
    Long subscriptionId,
    List<BoxFundingResponse> boxFunding,
    List<BoxDistributionResponse> boxDistributions,
    BigDecimal availableToSpendAmount
) {

    public TransactionResponse(Long id, BigDecimal amount, String direction, String description,
                               LocalDate transactionDate, Long categoryId, String categoryName,
                               Integer categoryHue, Long contactId, String contactName,
                               Long subscriptionId) {
        this(id, amount, direction, description, transactionDate, categoryId, categoryName,
            categoryHue, contactId, contactName, subscriptionId,
            List.of(), List.of(), amount);
    }
}
