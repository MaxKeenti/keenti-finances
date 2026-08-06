package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CreditStatement(
    Long id, Long accountId, LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate,
    BigDecimal estimatedBalance, BigDecimal officialBalance, BigDecimal officialMinimumPayment,
    BigDecimal officialAvoidInterest, String officialNote, LocalDateTime confirmedAt,
    BigDecimal paidAmount
) {}
