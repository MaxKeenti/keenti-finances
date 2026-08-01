package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class SavingGoalCalculator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private SavingGoalCalculator() {}

    public record ProgressAllocation(
        BigDecimal requiredAmount,
        BigDecimal arrearsCovered,
        BigDecimal regularProgress,
        BigDecimal extraProgress,
        BigDecimal shortfall,
        SavingGoalPeriod.Status status
    ) {}

    public static BigDecimal defaultCommitment(BigDecimal remainingAmount,
                                               int remainingPeriods) {
        BigDecimal remaining = money(remainingAmount.max(BigDecimal.ZERO));
        if (remaining.signum() == 0) {
            return money(BigDecimal.ZERO);
        }
        if (remainingPeriods <= 0) {
            throw new IllegalArgumentException("At least one saving period is required");
        }
        return remaining.divide(
            BigDecimal.valueOf(remainingPeriods), 2, RoundingMode.CEILING);
    }

    public static ProgressAllocation allocate(BigDecimal netProgress,
                                              BigDecimal regularCommitment,
                                              BigDecimal openingArrears) {
        BigDecimal progress = money(netProgress);
        BigDecimal regular = money(regularCommitment);
        BigDecimal arrears = money(openingArrears);
        BigDecimal required = money(regular.add(arrears));
        BigDecimal positiveProgress = progress.max(BigDecimal.ZERO);
        BigDecimal arrearsCovered = money(positiveProgress.min(arrears));
        BigDecimal afterArrears = positiveProgress.subtract(arrearsCovered);
        BigDecimal regularProgress = money(afterArrears.min(regular));
        BigDecimal extraProgress = money(positiveProgress.subtract(required)
            .max(BigDecimal.ZERO));
        BigDecimal shortfall = money(required.subtract(progress)
            .max(BigDecimal.ZERO));
        SavingGoalPeriod.Status status = progress.compareTo(required) >= 0
            ? SavingGoalPeriod.Status.ACHIEVED
            : SavingGoalPeriod.Status.MISSED;
        return new ProgressAllocation(
            required, arrearsCovered, regularProgress,
            extraProgress, shortfall, status);
    }

    public static int periodsNeeded(BigDecimal remainingAmount,
                                    BigDecimal regularCommitment) {
        BigDecimal remaining = money(remainingAmount.max(BigDecimal.ZERO));
        if (remaining.signum() == 0) {
            return 0;
        }
        BigDecimal regular = money(regularCommitment);
        if (regular.signum() <= 0) {
            return 0;
        }
        return remaining.divide(regular, 0, RoundingMode.CEILING).intValueExact();
    }

    public static BigDecimal progressPercent(BigDecimal balance,
                                             BigDecimal targetAmount) {
        if (targetAmount == null || targetAmount.signum() <= 0) {
            return money(BigDecimal.ZERO);
        }
        return balance.max(BigDecimal.ZERO)
            .multiply(ONE_HUNDRED)
            .divide(targetAmount, 2, RoundingMode.HALF_UP)
            .min(ONE_HUNDRED)
            .setScale(2, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }
}
