package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Debt {

    private Long id;
    private Long contactId;
    private String description;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime createdAt;

    public Debt(Long id, Long contactId, String description, BigDecimal totalAmount,
                String status, LocalDateTime createdAt) {
        this.id = id;
        this.contactId = contactId;
        this.description = description;
        this.totalAmount = totalAmount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getContactId() { return contactId; }
    public String getDescription() { return description; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
