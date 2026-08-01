package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SavingGoalCalculatorTest {

    @Test
    void defaultCommitmentRoundsUpSoFinalCentIsNeverMissing() {
        assertEquals(new BigDecimal("33.34"),
            SavingGoalCalculator.defaultCommitment(
                new BigDecimal("100.00"), 3));
    }

    @Test
    void missedProgressCarriesOnlyItsShortfallIntoTheNextPeriod() {
        var missed = SavingGoalCalculator.allocate(
            new BigDecimal("1000.00"),
            new BigDecimal("1500.00"),
            BigDecimal.ZERO.setScale(2));
        assertEquals(new BigDecimal("500.00"), missed.shortfall());
        assertEquals(SavingGoalPeriod.Status.MISSED, missed.status());

        var catchUp = SavingGoalCalculator.allocate(
            new BigDecimal("2000.00"),
            new BigDecimal("1500.00"),
            missed.shortfall());
        assertEquals(new BigDecimal("500.00"), catchUp.arrearsCovered());
        assertEquals(new BigDecimal("1500.00"), catchUp.regularProgress());
        assertEquals(new BigDecimal("0.00"), catchUp.shortfall());
        assertEquals(SavingGoalPeriod.Status.ACHIEVED, catchUp.status());
    }

    @Test
    void extraProgressDoesNotChangeTheStableRegularCommitment() {
        var result = SavingGoalCalculator.allocate(
            new BigDecimal("2200.00"),
            new BigDecimal("1500.00"),
            new BigDecimal("500.00"));

        assertEquals(new BigDecimal("200.00"), result.extraProgress());
        assertEquals(new BigDecimal("0.00"), result.shortfall());
        assertEquals(new BigDecimal("1500.00"), result.regularProgress());
    }

    @Test
    void negativeNetGrowthIncreasesTheNextPeriodsArrears() {
        var result = SavingGoalCalculator.allocate(
            new BigDecimal("-500.00"),
            new BigDecimal("1500.00"),
            BigDecimal.ZERO.setScale(2));

        assertEquals(new BigDecimal("2000.00"), result.shortfall());
        assertEquals(SavingGoalPeriod.Status.MISSED, result.status());
    }
}
