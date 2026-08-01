package com.keenti.finances.domain.model;

import java.math.BigDecimal;

public record BoxBalanceSummary(
    BigDecimal netBalance,
    BigDecimal inBoxes,
    BigDecimal availableToSpend
) {}
