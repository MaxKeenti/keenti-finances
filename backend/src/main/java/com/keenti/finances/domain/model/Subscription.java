package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Subscription {

    private Long id;
    private String name;
    private BigDecimal cost;
    private String currency;
    private String billingCycle;
    private String type;
    private Long categoryId;
    private LocalDate nextBillingDate;
    private String tokenUuid;
    private LocalDateTime createdAt;
    private boolean ownerParticipates;

    public Subscription(Long id, String name, BigDecimal cost, String billingCycle, String type,
                        Long categoryId, LocalDate nextBillingDate, String tokenUuid, LocalDateTime createdAt,
                        boolean ownerParticipates) {
        this(id, name, cost, Currencies.DEFAULT, billingCycle, type, categoryId, nextBillingDate,
            tokenUuid, createdAt, ownerParticipates);
    }

    public Subscription(Long id, String name, BigDecimal cost, String currency, String billingCycle,
                        String type, Long categoryId, LocalDate nextBillingDate, String tokenUuid,
                        LocalDateTime createdAt, boolean ownerParticipates) {
        this.id = id;
        this.name = name;
        this.cost = cost;
        this.currency = currency;
        this.billingCycle = billingCycle;
        this.type = type;
        this.categoryId = categoryId;
        this.nextBillingDate = nextBillingDate;
        this.tokenUuid = tokenUuid;
        this.createdAt = createdAt;
        this.ownerParticipates = ownerParticipates;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getCost() { return cost; }
    /** Member shares and Payment Records are in it; frozen once a Payment Record exists. */
    public String getCurrency() { return currency; }
    public String getBillingCycle() { return billingCycle; }
    public String getType() { return type; }
    public Long getCategoryId() { return categoryId; }
    public LocalDate getNextBillingDate() { return nextBillingDate; }
    public String getTokenUuid() { return tokenUuid; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isOwnerParticipates() { return ownerParticipates; }
}
