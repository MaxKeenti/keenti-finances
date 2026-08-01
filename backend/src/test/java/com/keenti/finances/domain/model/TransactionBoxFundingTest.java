package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionBoxFundingTest {

    @Test
    void availableToSpendAmount_isTheUnassignedRemainder() {
        Transaction transaction = new Transaction(
            7L,
            new BigDecimal("500.00"),
            "EGRESS",
            "Split expense",
            LocalDate.of(2026, 7, 31),
            3L,
            null,
            null,
            List.of(
                new BoxFunding(11L, new BigDecimal("200.00"), 0),
                new BoxFunding(12L, new BigDecimal("150.00"), 1)
            )
        );

        assertEquals(new BigDecimal("150.00"), transaction.getAvailableToSpendAmount());
        assertEquals(List.of(11L, 12L), transaction.getBoxFunding().stream()
            .map(BoxFunding::boxId)
            .toList());
    }

    @Test
    void fundingCollection_isDefensivelyCopied() {
        List<BoxFunding> mutable = new ArrayList<>();
        mutable.add(new BoxFunding(11L, new BigDecimal("10.00"), 0));
        Transaction transaction = new Transaction(
            8L,
            new BigDecimal("20.00"),
            "EGRESS",
            null,
            LocalDate.of(2026, 7, 31),
            3L,
            null,
            null,
            mutable
        );

        mutable.clear();

        assertEquals(1, transaction.getBoxFunding().size());
        assertThrows(UnsupportedOperationException.class,
            () -> transaction.getBoxFunding().clear());
    }

    @Test
    void distributionCollection_isDefensivelyCopiedAndPreservedByFundingHydration() {
        List<BoxDistribution> mutable = new ArrayList<>();
        mutable.add(new BoxDistribution(21L, new BigDecimal("30.00"), 0));
        Transaction transaction = new Transaction(
            9L,
            new BigDecimal("100.00"),
            "INGRESS",
            "Salary",
            LocalDate.of(2026, 7, 31),
            3L,
            null,
            null,
            List.of(),
            mutable
        );

        mutable.clear();
        Transaction hydrated = transaction.withBoxFunding(List.of());

        assertEquals(1, hydrated.getBoxDistributions().size());
        assertEquals(21L, hydrated.getBoxDistributions().getFirst().boxId());
        assertThrows(UnsupportedOperationException.class,
            () -> hydrated.getBoxDistributions().clear());
    }
}
