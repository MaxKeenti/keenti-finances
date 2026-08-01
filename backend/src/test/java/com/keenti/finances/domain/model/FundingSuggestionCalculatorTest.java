package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FundingSuggestionCalculatorTest {

    @Test
    void fixedAmount_isReturnedAtMxnPrecision() {
        FundingTrigger trigger = trigger(
            FundingTrigger.Strategy.FIXED_AMOUNT, "500.00", null);

        assertEquals(new BigDecimal("500.00"), FundingSuggestionCalculator.calculate(
            trigger, new BigDecimal("10000.00"), Optional.empty()).orElseThrow());
    }

    @Test
    void percentage_usesDeterministicHalfUpCentRounding() {
        FundingTrigger trigger = trigger(
            FundingTrigger.Strategy.PERCENTAGE, null, "12.5000");

        assertEquals(new BigDecimal("1.26"), FundingSuggestionCalculator.calculate(
            trigger, new BigDecimal("10.05"), Optional.empty()).orElseThrow());
    }

    @Test
    void planDerived_usesThePluggableAmountAndRoundsToMxnPrecision() {
        FundingTrigger trigger = trigger(
            FundingTrigger.Strategy.PLAN_DERIVED, null, null);

        assertEquals(new BigDecimal("123.46"), FundingSuggestionCalculator.calculate(
            trigger, new BigDecimal("1000.00"),
            Optional.of(new BigDecimal("123.455"))).orElseThrow());
        assertTrue(FundingSuggestionCalculator.calculate(
            trigger, new BigDecimal("1000.00"), Optional.empty()).isEmpty());
    }

    private static FundingTrigger trigger(
            FundingTrigger.Strategy strategy, String fixedAmount, String percentage) {
        return new FundingTrigger(
            1L, 2L, "Box", 3L, "Salary", strategy,
            fixedAmount == null ? null : new BigDecimal(fixedAmount),
            percentage == null ? null : new BigDecimal(percentage),
            true, null, null);
    }
}
