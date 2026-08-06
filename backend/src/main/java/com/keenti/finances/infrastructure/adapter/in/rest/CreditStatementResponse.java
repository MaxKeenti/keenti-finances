package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CreditStatementResponse(Long id, Long accountId, LocalDate periodStart,
    LocalDate periodEnd, LocalDate dueDate, BigDecimal estimatedBalance,
    BigDecimal officialBalance, BigDecimal officialMinimumPayment,
    BigDecimal officialAvoidInterest, String officialNote, LocalDateTime confirmedAt,
    BigDecimal paidAmount, BigDecimal outstandingBalance, boolean reconciliationMismatch,
    BigDecimal mismatchAmount) {}
