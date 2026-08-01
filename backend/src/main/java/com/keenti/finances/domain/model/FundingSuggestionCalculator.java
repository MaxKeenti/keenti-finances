package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

public final class FundingSuggestionCalculator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private FundingSuggestionCalculator() {}

    public static Optional<BigDecimal> calculate(
            FundingTrigger trigger, BigDecimal ingressAmount,
            Optional<BigDecimal> planDerivedAmount) {
        Optional<BigDecimal> raw = switch (trigger.strategy()) {
            case FIXED_AMOUNT -> Optional.of(trigger.fixedAmount());
            case PERCENTAGE -> Optional.of(ingressAmount
                .multiply(trigger.percentage())
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP));
            case PLAN_DERIVED -> planDerivedAmount;
        };
        return raw.map(amount -> amount.setScale(2, RoundingMode.HALF_UP));
    }
}
