package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Box {

    private final Long id;
    private final String name;
    private final int hue;
    private final String icon;
    private final String description;
    private final int displayOrder;
    private final BigDecimal balance;
    private final boolean archived;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final long version;

    public Box(Long id, String name, int hue, String icon, String description,
               int displayOrder, BigDecimal balance, boolean archived,
               LocalDateTime createdAt, LocalDateTime updatedAt, long version) {
        this.id = id;
        this.name = name;
        this.hue = hue;
        this.icon = icon;
        this.description = description;
        this.displayOrder = displayOrder;
        this.balance = balance;
        this.archived = archived;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public int getHue() { return hue; }
    public String getIcon() { return icon; }
    public String getDescription() { return description; }
    public int getDisplayOrder() { return displayOrder; }
    public BigDecimal getBalance() { return balance; }
    public boolean isArchived() { return archived; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
