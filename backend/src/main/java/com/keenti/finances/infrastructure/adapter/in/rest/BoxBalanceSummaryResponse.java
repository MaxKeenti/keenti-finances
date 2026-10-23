package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.util.List;

public record BoxBalanceSummaryResponse(
    String currency,
    BigDecimal netBalance,
    BigDecimal inBoxes,
    BigDecimal availableToSpend,
    /** Every currency the User holds, Primary Currency first; only on the summary read. */
    List<String> currencies
) {}
