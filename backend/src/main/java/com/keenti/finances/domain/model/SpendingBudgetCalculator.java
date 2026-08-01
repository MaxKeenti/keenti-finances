package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class SpendingBudgetCalculator {

    private SpendingBudgetCalculator() {}

    public static SpendingBudgetCalculation calculate(
            List<BoxHistoryEntry> history,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            BigDecimal desiredBalance) {
        return calculate(
            history, periodStart, periodEndExclusive, desiredBalance, null);
    }

    /**
     * Calculates a period and, for the plan's first period, treats movements
     * already present when the plan was created as opening funds rather than
     * new period activity.
     */
    public static SpendingBudgetCalculation calculate(
            List<BoxHistoryEntry> history,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            BigDecimal desiredBalance,
            LocalDateTime firstPeriodOpeningCutoff) {
        if (periodStart == null || periodEndExclusive == null
                || !periodEndExclusive.isAfter(periodStart)) {
            throw new IllegalArgumentException("A Spending Budget period requires a valid date range");
        }
        if (desiredBalance == null || desiredBalance.signum() <= 0) {
            throw new IllegalArgumentException("desiredBalance must be positive");
        }

        BigDecimal opening = BigDecimal.ZERO;
        BigDecimal deposits = BigDecimal.ZERO;
        BigDecimal withdrawals = BigDecimal.ZERO;
        BigDecimal transfersIn = BigDecimal.ZERO;
        BigDecimal transfersOut = BigDecimal.ZERO;
        BigDecimal spending = BigDecimal.ZERO;

        for (BoxHistoryEntry entry : history) {
            boolean existedAtFirstPeriodOpen = firstPeriodOpeningCutoff != null
                && entry.effectiveDate().equals(periodStart)
                && !entry.createdAt().isAfter(firstPeriodOpeningCutoff);
            if (entry.effectiveDate().isBefore(periodStart)
                    || existedAtFirstPeriodOpen) {
                opening = opening.add(entry.signedAmount());
                continue;
            }
            if (!entry.effectiveDate().isBefore(periodEndExclusive)) {
                continue;
            }
            switch (entry.type()) {
                case DEPOSIT -> deposits = deposits.add(entry.amount());
                case WITHDRAWAL -> withdrawals = withdrawals.add(entry.amount());
                case TRANSFER_IN -> transfersIn = transfersIn.add(entry.amount());
                case TRANSFER_OUT -> transfersOut = transfersOut.add(entry.amount());
                case SPENDING -> spending = spending.add(entry.amount());
            }
        }

        BigDecimal credits = deposits.add(transfersIn);
        BigDecimal debits = withdrawals.add(transfersOut).add(spending);
        BigDecimal netProgress = credits.subtract(debits);
        BigDecimal closing = opening.add(netProgress);
        BigDecimal suggestedTopUp = desiredBalance.subtract(closing).max(BigDecimal.ZERO);

        return new SpendingBudgetCalculation(
            opening,
            closing,
            netProgress,
            deposits,
            withdrawals,
            transfersIn,
            transfersOut,
            spending,
            suggestedTopUp
        );
    }
}
