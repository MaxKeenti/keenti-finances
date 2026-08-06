package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A real asset or credit liability owned by one User. */
public class FinancialAccount {

    private final Long id;
    private final String name;
    private final String kind;
    private final BigDecimal openingBalance;
    private final LocalDate openingDate;
    private final BigDecimal balance;
    private final boolean archived;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final long version;

    public FinancialAccount(Long id, String name, String kind, BigDecimal openingBalance,
                            LocalDate openingDate, BigDecimal balance, boolean archived,
                            LocalDateTime createdAt, LocalDateTime updatedAt, long version) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.openingBalance = openingBalance;
        this.openingDate = openingDate;
        this.balance = balance;
        this.archived = archived;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getKind() { return kind; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public LocalDate getOpeningDate() { return openingDate; }
    public BigDecimal getBalance() { return balance; }
    public boolean isArchived() { return archived; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public boolean isCredit() { return "CREDIT".equals(kind); }
}
