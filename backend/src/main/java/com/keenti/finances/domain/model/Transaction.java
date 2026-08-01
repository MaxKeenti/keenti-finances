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
    private List<BoxFunding> boxFunding;
    private List<BoxDistribution> boxDistributions;

    public Transaction(Long id, BigDecimal amount, String direction, String description,
                       LocalDate transactionDate, Long categoryId, Long contactId, Long subscriptionId) {
        this(id, amount, direction, description, transactionDate, categoryId, contactId,
            subscriptionId, List.of(), List.of());
    }

    public Transaction(Long id, BigDecimal amount, String direction, String description,
                       LocalDate transactionDate, Long categoryId, Long contactId, Long subscriptionId,
                       List<BoxFunding> boxFunding) {
        this(id, amount, direction, description, transactionDate, categoryId, contactId,
            subscriptionId, boxFunding, List.of());
    }

    public Transaction(Long id, BigDecimal amount, String direction, String description,
                       LocalDate transactionDate, Long categoryId, Long contactId, Long subscriptionId,
                       List<BoxFunding> boxFunding, List<BoxDistribution> boxDistributions) {
        this.id = id;
        this.amount = amount;
        this.direction = direction;
        this.description = description;
        this.transactionDate = transactionDate;
        this.categoryId = categoryId;
        this.contactId = contactId;
        this.subscriptionId = subscriptionId;
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
    public List<BoxFunding> getBoxFunding() { return boxFunding; }
    public List<BoxDistribution> getBoxDistributions() { return boxDistributions; }

    public BigDecimal getAvailableToSpendAmount() {
        BigDecimal boxFunded = boxFunding.stream()
            .map(BoxFunding::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return amount.subtract(boxFunded);
    }

    public Transaction withBoxFunding(List<BoxFunding> funding) {
        return new Transaction(id, amount, direction, description, transactionDate,
            categoryId, contactId, subscriptionId, funding, boxDistributions);
    }

    public Transaction withBoxDistributions(List<BoxDistribution> distributions) {
        return new Transaction(id, amount, direction, description, transactionDate,
            categoryId, contactId, subscriptionId, boxFunding, distributions);
    }
}
