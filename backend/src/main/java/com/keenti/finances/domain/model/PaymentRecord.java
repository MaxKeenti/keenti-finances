package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class PaymentRecord {

    private Long id;
    private Long subscriptionId;
    private Long memberId;
    private LocalDate billingDate;
    private BigDecimal amount;
    private String status;
    private LocalDate paidDate;
    private Long transactionId;
    private LocalDateTime createdAt;

    public PaymentRecord(Long id, Long subscriptionId, Long memberId, LocalDate billingDate,
                         BigDecimal amount, String status, LocalDate paidDate, Long transactionId,
                         LocalDateTime createdAt) {
        this.id = id;
        this.subscriptionId = subscriptionId;
        this.memberId = memberId;
        this.billingDate = billingDate;
        this.amount = amount;
        this.status = status;
        this.paidDate = paidDate;
        this.transactionId = transactionId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getSubscriptionId() { return subscriptionId; }
    public Long getMemberId() { return memberId; }
    public LocalDate getBillingDate() { return billingDate; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public LocalDate getPaidDate() { return paidDate; }
    public Long getTransactionId() { return transactionId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
