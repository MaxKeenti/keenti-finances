package com.keenti.finances.domain.model;

import java.math.BigDecimal;

public record SpendingBudgetRevision(
    BoxPlanRevision planRevision,
    BigDecimal desiredBalance
) {}
