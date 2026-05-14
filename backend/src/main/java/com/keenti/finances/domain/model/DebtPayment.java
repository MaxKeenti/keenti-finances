package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class DebtPayment {

    private Long id;
    private Long debtId;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private Long transactionId;
    private String notes;
    private LocalDateTime createdAt;

    public DebtPayment(Long id, Long debtId, BigDecimal amount, LocalDate paymentDate,
                       Long transactionId, String notes, LocalDateTime createdAt) {
        this.id = id;
        this.debtId = debtId;
        this.amount = amount;
        this.paymentDate = paymentDate;
        this.transactionId = transactionId;
        this.notes = notes;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getDebtId() { return debtId; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public Long getTransactionId() { return transactionId; }
    public String getNotes() { return notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
