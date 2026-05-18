package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Transaction {

    private Long id;
    private BigDecimal amount;
    private String direction;
    private String description;
    private LocalDate transactionDate;
    private Long categoryId;
    private Long contactId;
    private Long subscriptionId;

    public Transaction(Long id, BigDecimal amount, String direction, String description,
                       LocalDate transactionDate, Long categoryId, Long contactId, Long subscriptionId) {
        this.id = id;
        this.amount = amount;
        this.direction = direction;
        this.description = description;
        this.transactionDate = transactionDate;
        this.categoryId = categoryId;
        this.contactId = contactId;
        this.subscriptionId = subscriptionId;
    }

    public Long getId() { return id; }
    public BigDecimal getAmount() { return amount; }
    public String getDirection() { return direction; }
    public String getDescription() { return description; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public Long getCategoryId() { return categoryId; }
    public Long getContactId() { return contactId; }
    public Long getSubscriptionId() { return subscriptionId; }
}
