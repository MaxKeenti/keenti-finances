package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BoxHistoryEntry(
    Long id,
    Type type,
    BigDecimal amount,
    LocalDate effectiveDate,
    LocalDateTime createdAt,
    BigDecimal runningBalance,
    Long relatedBoxId,
    String relatedBoxName,
    Long relatedTransactionId,
    String relatedTransactionDescription,
    boolean relatedTransactionChanged,
    boolean relatedTransactionRemoved
) {
    public BoxHistoryEntry(Long id, Type type, BigDecimal amount, LocalDate effectiveDate,
                           LocalDateTime createdAt, BigDecimal runningBalance,
                           Long relatedBoxId, String relatedBoxName,
                           Long relatedTransactionId, String relatedTransactionDescription) {
        this(id, type, amount, effectiveDate, createdAt, runningBalance,
            relatedBoxId, relatedBoxName, relatedTransactionId,
            relatedTransactionDescription, false, false);
    }

    public enum Type {
        DEPOSIT,
        WITHDRAWAL,
        TRANSFER_IN,
        TRANSFER_OUT,
        SPENDING
    }

    public boolean isDebit() {
        return type == Type.WITHDRAWAL
            || type == Type.TRANSFER_OUT
            || type == Type.SPENDING;
    }

    public BigDecimal signedAmount() {
        return isDebit() ? amount.negate() : amount;
    }

    public BoxHistoryEntry withRunningBalance(BigDecimal newRunningBalance) {
        return new BoxHistoryEntry(
            id, type, amount, effectiveDate, createdAt, newRunningBalance,
            relatedBoxId, relatedBoxName,
            relatedTransactionId, relatedTransactionDescription,
            relatedTransactionChanged, relatedTransactionRemoved
        );
    }
}
