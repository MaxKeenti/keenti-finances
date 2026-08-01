package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoxLedgerTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 7, 31, 12, 0);

    @Test
    void runningBalance_appliesEveryCreditAndDebitType() {
        List<BoxHistoryEntry> history = BoxLedger.withRunningBalances(List.of(
            entry(5, BoxHistoryEntry.Type.SPENDING, "10.00", "2026-07-05"),
            entry(1, BoxHistoryEntry.Type.DEPOSIT, "100.00", "2026-07-01"),
            entry(3, BoxHistoryEntry.Type.TRANSFER_IN, "25.00", "2026-07-03"),
            entry(4, BoxHistoryEntry.Type.TRANSFER_OUT, "15.00", "2026-07-04"),
            entry(2, BoxHistoryEntry.Type.WITHDRAWAL, "20.00", "2026-07-02")
        ));

        assertEquals(List.of(
            new BigDecimal("100.00"),
            new BigDecimal("80.00"),
            new BigDecimal("105.00"),
            new BigDecimal("90.00"),
            new BigDecimal("80.00")
        ), history.stream().map(BoxHistoryEntry::runningBalance).toList());
        assertEquals(new BigDecimal("80.00"), BoxLedger.currentBalance(history));
        assertTrue(BoxLedger.remainsNonNegative(history));
    }

    @Test
    void backdatedDebit_isRejectedWhenAnIntermediateBalanceWouldBeNegative() {
        List<BoxHistoryEntry> history = List.of(
            entry(1, BoxHistoryEntry.Type.DEPOSIT, "50.00", "2026-07-01"),
            entry(2, BoxHistoryEntry.Type.DEPOSIT, "100.00", "2026-07-10")
        );

        assertFalse(BoxLedger.canApplyDebit(
            history,
            new BigDecimal("75.00"),
            LocalDate.parse("2026-07-05"),
            CREATED.plusDays(1)
        ));
        assertEquals(new BigDecimal("150.00"), BoxLedger.currentBalance(history));
    }

    @Test
    void backdatedDebit_isAcceptedWhenEveryLaterRunningBalanceStaysNonNegative() {
        List<BoxHistoryEntry> history = List.of(
            entry(1, BoxHistoryEntry.Type.DEPOSIT, "100.00", "2026-07-01"),
            entry(2, BoxHistoryEntry.Type.WITHDRAWAL, "25.00", "2026-07-10"),
            entry(3, BoxHistoryEntry.Type.DEPOSIT, "50.00", "2026-07-15")
        );

        assertTrue(BoxLedger.canApplyDebit(
            history,
            new BigDecimal("70.00"),
            LocalDate.parse("2026-07-05"),
            CREATED.plusDays(1)
        ));
    }

    @Test
    void chronologicalValidation_detectsExistingNegativeHistory() {
        List<BoxHistoryEntry> invalid = List.of(
            entry(2, BoxHistoryEntry.Type.DEPOSIT, "100.00", "2026-07-02"),
            entry(1, BoxHistoryEntry.Type.WITHDRAWAL, "1.00", "2026-07-01")
        );

        assertFalse(BoxLedger.remainsNonNegative(invalid));
    }

    private static BoxHistoryEntry entry(long id, BoxHistoryEntry.Type type,
                                         String amount, String date) {
        return new BoxHistoryEntry(
            id,
            type,
            new BigDecimal(amount),
            LocalDate.parse(date),
            CREATED.plusSeconds(id),
            null,
            null,
            null,
            null,
            null
        );
    }
}
