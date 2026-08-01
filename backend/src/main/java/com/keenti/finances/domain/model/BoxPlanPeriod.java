package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BoxPlanPeriod(
    Long id,
    Long planId,
    Long revisionId,
    LocalDate periodStart,
    LocalDate periodEndExclusive,
    BigDecimal openingBalance,
    BigDecimal closingBalance,
    BigDecimal netProgress,
    LocalDateTime evaluatedAt
) {}
