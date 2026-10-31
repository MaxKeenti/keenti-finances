package com.keenti.finances.domain.model;

import java.math.BigDecimal;

/** The three headline figures, all in {@code currency} (ADR-0024). */
public record BoxBalanceSummary(
    String currency,
    BigDecimal netBalance,
    BigDecimal inBoxes,
    BigDecimal availableToSpend
) {}
