package com.keenti.finances.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpendingBudgetCalculatorTest {

    @Test
    void surplusRollsForwardAndReducesOnlyTheSuggestedTopUp() {
        LocalDate start = LocalDate.of(2026, 7, 27);
        SpendingBudgetCalculation result = SpendingBudgetCalculator.calculate(
            List.of(
                entry(1, BoxHistoryEntry.Type.DEPOSIT, "2000.00", start.minusWeeks(1)),
                entry(2, BoxHistoryEntry.Type.SPENDING, "1600.00", start.minusDays(1))
            ),
            start,
            start.plusWeeks(1),
            new BigDecimal("2000.00")
        );

        assertEquals(new BigDecimal("400.00"), result.openingBalance());
        assertEquals(new BigDecimal("400.00"), result.closingBalance());
        assertEquals(new BigDecimal("1600.00"), result.suggestedTopUp());
    }

    @Test
    void periodBreakdownCountsOnlyExplicitBoxHistory() {
        LocalDate start = LocalDate.of(2026, 7, 27);
        SpendingBudgetCalculation result = SpendingBudgetCalculator.calculate(
            List.of(
                entry(1, BoxHistoryEntry.Type.DEPOSIT, "400.00", start.minusDays(1)),
                entry(2, BoxHistoryEntry.Type.DEPOSIT, "1600.00", start),
                entry(3, BoxHistoryEntry.Type.TRANSFER_IN, "100.00", start.plusDays(1)),
                entry(4, BoxHistoryEntry.Type.WITHDRAWAL, "200.00", start.plusDays(2)),
                entry(5, BoxHistoryEntry.Type.TRANSFER_OUT, "300.00", start.plusDays(3)),
                entry(6, BoxHistoryEntry.Type.SPENDING, "1200.00", start.plusDays(4)),
                entry(7, BoxHistoryEntry.Type.DEPOSIT, "999.00", start.plusWeeks(1))
            ),
            start,
            start.plusWeeks(1),
            new BigDecimal("2000.00")
        );

        assertEquals(new BigDecimal("400.00"), result.openingBalance());
        assertEquals(new BigDecimal("1600.00"), result.deposits());
        assertEquals(new BigDecimal("100.00"), result.transfersIn());
        assertEquals(new BigDecimal("200.00"), result.withdrawals());
        assertEquals(new BigDecimal("300.00"), result.transfersOut());
        assertEquals(new BigDecimal("1200.00"), result.fundedSpending());
        assertEquals(new BigDecimal("400.00"), result.closingBalance());
        assertEquals(new BigDecimal("1600.00"), result.suggestedTopUp());
    }

    @Test
    void overTargetBalanceNeverSuggestsAWithdrawalOrNegativeTopUp() {
        LocalDate start = LocalDate.of(2026, 7, 27);
        SpendingBudgetCalculation result = SpendingBudgetCalculator.calculate(
            List.of(entry(1, BoxHistoryEntry.Type.DEPOSIT, "2500.00", start.minusDays(1))),
            start,
            start.plusWeeks(1),
            new BigDecimal("2000.00")
        );

        assertEquals(new BigDecimal("2500.00"), result.closingBalance());
        assertEquals(BigDecimal.ZERO, result.suggestedTopUp());
    }

    private static BoxHistoryEntry entry(long id, BoxHistoryEntry.Type type,
                                         String amount, LocalDate effectiveDate) {
        return new BoxHistoryEntry(
            id,
            type,
            new BigDecimal(amount),
            effectiveDate,
            LocalDateTime.of(2026, 7, 31, 12, 0).plusSeconds(id),
            null,
            null,
            null,
            null,
            null
        );
    }
}
