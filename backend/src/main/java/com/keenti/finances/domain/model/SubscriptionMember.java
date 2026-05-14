package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SubscriptionMember {

    private Long id;
    private Long subscriptionId;
    private Long contactId;
    private BigDecimal shareAmount;
    private LocalDateTime createdAt;

    public SubscriptionMember(Long id, Long subscriptionId, Long contactId, BigDecimal shareAmount, LocalDateTime createdAt) {
        this.id = id;
        this.subscriptionId = subscriptionId;
        this.contactId = contactId;
        this.shareAmount = shareAmount;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getSubscriptionId() { return subscriptionId; }
    public Long getContactId() { return contactId; }
    public BigDecimal getShareAmount() { return shareAmount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
