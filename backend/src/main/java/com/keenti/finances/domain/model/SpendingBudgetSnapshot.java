package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SpendingBudgetSnapshot(
    BoxPlan plan,
    SpendingBudgetRevision revision,
    List<SpendingBudgetRevision> revisions,
    List<SpendingBudgetPeriod> periods,
    SpendingBudgetPeriod currentPeriod,
    LocalDate currentPeriodStart,
    LocalDate currentPeriodEndExclusive,
    BigDecimal currentBalance,
    BigDecimal suggestedTopUp
) {}
