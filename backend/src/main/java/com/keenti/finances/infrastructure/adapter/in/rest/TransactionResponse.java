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
    Long accountId,
    String accountName,
    String accountKind,
    List<BoxFundingResponse> boxFunding,
    List<BoxDistributionResponse> boxDistributions,
    BigDecimal availableToSpendAmount,
    String currency
) {

    public TransactionResponse(Long id, BigDecimal amount, String direction, String description,
                               LocalDate transactionDate, Long categoryId, String categoryName,
                               Integer categoryHue, Long contactId, String contactName,
                               Long subscriptionId) {
        this(id, amount, direction, description, transactionDate, categoryId, categoryName,
            categoryHue, contactId, contactName, subscriptionId, null, null, null,
            List.of(), List.of(), amount, com.keenti.finances.domain.model.Currencies.DEFAULT);
    }

    public TransactionResponse(Long id, BigDecimal amount, String direction, String description,
                               LocalDate transactionDate, Long categoryId, String categoryName,
                               Integer categoryHue, Long contactId, String contactName,
                               Long subscriptionId, List<BoxFundingResponse> boxFunding,
                               List<BoxDistributionResponse> boxDistributions,
                               BigDecimal availableToSpendAmount) {
        this(id, amount, direction, description, transactionDate, categoryId, categoryName,
            categoryHue, contactId, contactName, subscriptionId, null, null, null,
            boxFunding, boxDistributions, availableToSpendAmount,
            com.keenti.finances.domain.model.Currencies.DEFAULT);
    }
}
