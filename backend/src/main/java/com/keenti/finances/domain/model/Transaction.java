package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class Transaction {

    private Long id;
    private BigDecimal amount;
    private String direction;
    private String description;
    private LocalDate transactionDate;
    private Long categoryId;
    private Long contactId;
    private Long subscriptionId;
    private Long accountId;
    private String currency;
    private List<BoxFunding> boxFunding;
    private List<BoxDistribution> boxDistributions;

    public Transaction(Long id, BigDecimal amount, String direction, String description,
                       LocalDate transactionDate, Long categoryId, Long contactId, Long subscriptionId) {
        this(id, amount, direction, description, transactionDate, categoryId, contactId,
            subscriptionId, null, List.of(), List.of());
    }

    public Transaction(Long id, BigDecimal amount, String direction, String description,
                       LocalDate transactionDate, Long categoryId, Long contactId, Long subscriptionId,
                       List<BoxFunding> boxFunding) {
        this(id, amount, direction, description, transactionDate, categoryId, contactId,
            subscriptionId, null, boxFunding, List.of());
    }

    public Transaction(Long id, BigDecimal amount, String direction, String description,
                       LocalDate transactionDate, Long categoryId, Long contactId, Long subscriptionId,
                       List<BoxFunding> boxFunding, List<BoxDistribution> boxDistributions) {
        this(id, amount, direction, description, transactionDate, categoryId, contactId,
            subscriptionId, null, boxFunding, boxDistributions);
    }

    public Transaction(Long id, BigDecimal amount, String direction, String description,
                       LocalDate transactionDate, Long categoryId, Long contactId, Long subscriptionId,
                       Long accountId, List<BoxFunding> boxFunding,
                       List<BoxDistribution> boxDistributions) {
        this(id, amount, direction, description, transactionDate, categoryId, contactId,
            subscriptionId, accountId, null, boxFunding, boxDistributions);
    }

    /**
     * @param currency the Transaction's currency. {@code null} on a request
     *     means "take it from the Financial Account"; a non-null value on a
     *     request is a requirement the Account must meet (ADR-0024).
     */
    public Transaction(Long id, BigDecimal amount, String direction, String description,
                       LocalDate transactionDate, Long categoryId, Long contactId, Long subscriptionId,
                       Long accountId, String currency, List<BoxFunding> boxFunding,
                       List<BoxDistribution> boxDistributions) {
        this.id = id;
        this.amount = amount;
        this.direction = direction;
        this.description = description;
        this.transactionDate = transactionDate;
        this.categoryId = categoryId;
        this.contactId = contactId;
        this.subscriptionId = subscriptionId;
        this.accountId = accountId;
        this.currency = currency;
        this.boxFunding = boxFunding == null ? List.of() : List.copyOf(boxFunding);
        this.boxDistributions = boxDistributions == null
            ? List.of()
            : List.copyOf(boxDistributions);
    }

    public Long getId() { return id; }
    public BigDecimal getAmount() { return amount; }
    public String getDirection() { return direction; }
    public String getDescription() { return description; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public Long getCategoryId() { return categoryId; }
    public Long getContactId() { return contactId; }
    public Long getSubscriptionId() { return subscriptionId; }
    public Long getAccountId() { return accountId; }
    public String getCurrency() { return currency; }
    public List<BoxFunding> getBoxFunding() { return boxFunding; }
    public List<BoxDistribution> getBoxDistributions() { return boxDistributions; }

    public BigDecimal getAvailableToSpendAmount() {
        BigDecimal boxFunded = boxFunding.stream()
            .map(BoxFunding::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return amount.subtract(boxFunded);
    }

    public Transaction withCurrency(String resolvedCurrency) {
        return new Transaction(id, amount, direction, description, transactionDate,
            categoryId, contactId, subscriptionId, accountId, resolvedCurrency, boxFunding, boxDistributions);
    }

    public Transaction withBoxFunding(List<BoxFunding> funding) {
        return new Transaction(id, amount, direction, description, transactionDate,
            categoryId, contactId, subscriptionId, accountId, currency, funding, boxDistributions);
    }

    public Transaction withBoxDistributions(List<BoxDistribution> distributions) {
        return new Transaction(id, amount, direction, description, transactionDate,
            categoryId, contactId, subscriptionId, accountId, currency, boxFunding, distributions);
    }
}
