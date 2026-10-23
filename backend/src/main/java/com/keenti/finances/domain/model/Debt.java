package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Debt {

    private Long id;
    private Long contactId;
    private String direction;
    private String currency;
    private String description;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime createdAt;

    public Debt(Long id, Long contactId, String direction, String description, BigDecimal totalAmount,
                String status, LocalDateTime createdAt) {
        this(id, contactId, direction, Currencies.DEFAULT, description, totalAmount, status, createdAt);
    }

    public Debt(Long id, Long contactId, String direction, String currency, String description,
                BigDecimal totalAmount, String status, LocalDateTime createdAt) {
        this.id = id;
        this.contactId = contactId;
        this.direction = direction;
        this.currency = currency;
        this.description = description;
        this.totalAmount = totalAmount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getContactId() { return contactId; }

    /**
     * {@code INGRESS} when the Contact owes the User, {@code EGRESS} when the
     * User owes the Contact. It is also the Direction of the Transaction each
     * Debt Payment creates, so no mapping is needed at payment time.
     */
    public String getDirection() { return direction; }
    /** Frozen once a Debt Payment exists, like the Direction (ADR-0024). */
    public String getCurrency() { return currency; }
    public String getDescription() { return description; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
