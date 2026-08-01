package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;

public record BoxBalanceSummaryResponse(
    BigDecimal netBalance,
    BigDecimal inBoxes,
    BigDecimal availableToSpend
) {}
