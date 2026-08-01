package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Framework-free arithmetic for the dated Box ledger. */
public final class BoxLedger {

    private static final Comparator<BoxHistoryEntry> CHRONOLOGICAL = Comparator
        .comparing(BoxHistoryEntry::effectiveDate)
        .thenComparing(BoxHistoryEntry::createdAt)
        .thenComparing(entry -> entry.type().ordinal())
        .thenComparing(BoxHistoryEntry::id);

    private BoxLedger() {}

    public static List<BoxHistoryEntry> withRunningBalances(
            List<BoxHistoryEntry> unorderedEntries) {
        List<BoxHistoryEntry> ordered = new ArrayList<>(unorderedEntries);
        ordered.sort(CHRONOLOGICAL);

        List<BoxHistoryEntry> result = new ArrayList<>(ordered.size());
        BigDecimal balance = BigDecimal.ZERO;
        for (BoxHistoryEntry entry : ordered) {
            balance = balance.add(entry.signedAmount());
            result.add(entry.withRunningBalance(balance));
        }
        return List.copyOf(result);
    }

    public static BigDecimal currentBalance(List<BoxHistoryEntry> entries) {
        return entries.stream()
            .map(BoxHistoryEntry::signedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static boolean remainsNonNegative(List<BoxHistoryEntry> entries) {
        return withRunningBalances(entries).stream()
            .allMatch(entry -> entry.runningBalance().signum() >= 0);
    }

    public static boolean canApplyDebit(
            List<BoxHistoryEntry> existingEntries,
            BigDecimal amount,
            LocalDate effectiveDate,
            LocalDateTime createdAt) {
        List<BoxHistoryEntry> candidate = new ArrayList<>(existingEntries);
        candidate.add(new BoxHistoryEntry(
            Long.MAX_VALUE,
            BoxHistoryEntry.Type.WITHDRAWAL,
            amount,
            effectiveDate,
            createdAt,
            null,
            null,
            null,
            null,
            null
        ));
        return remainsNonNegative(candidate);
    }
}
