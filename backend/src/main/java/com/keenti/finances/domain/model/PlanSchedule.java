package com.keenti.finances.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/** Date-only cadence arithmetic; safe across time zones and DST transitions. */
public final class PlanSchedule {

    private PlanSchedule() {}

    public record DateRange(LocalDate start, LocalDate endExclusive) {
        public DateRange {
            if (start == null || endExclusive == null || !endExclusive.isAfter(start)) {
                throw new IllegalArgumentException("A period end must be after its start");
            }
        }

        public LocalDate endInclusive() {
            return endExclusive.minusDays(1);
        }

        public boolean contains(LocalDate date) {
            return !date.isBefore(start) && date.isBefore(endExclusive);
        }
    }

    public static LocalDate nextBoundary(LocalDate date, PlanCadence cadence,
                                         Integer anchorWeekday,
                                         Integer anchorDayOfMonth) {
        validateAnchor(cadence, anchorWeekday, anchorDayOfMonth);
        return switch (cadence) {
            case DAILY -> date.plusDays(1);
            case WEEKLY -> {
                DayOfWeek anchor = DayOfWeek.of(anchorWeekday);
                LocalDate candidate = date.with(TemporalAdjusters.next(anchor));
                yield candidate;
            }
            case BIWEEKLY -> {
                DayOfWeek anchor = DayOfWeek.of(anchorWeekday);
                yield date.getDayOfWeek() == anchor ? date.plusWeeks(2)
                    : date.with(TemporalAdjusters.next(anchor));
            }
            case MONTHLY -> {
                YearMonth month = YearMonth.from(date);
                LocalDate candidate = anchoredDay(month, anchorDayOfMonth);
                if (!candidate.isAfter(date)) {
                    candidate = anchoredDay(month.plusMonths(1), anchorDayOfMonth);
                }
                yield candidate;
            }
        };
    }

    public static List<DateRange> rangeFrom(LocalDate start, LocalDate endInclusive,
                                            PlanCadence cadence,
                                            Integer anchorWeekday,
                                            Integer anchorDayOfMonth) {
        validateAnchor(cadence, anchorWeekday, anchorDayOfMonth);
        if (endInclusive.isBefore(start)) {
            return List.of();
        }

        LocalDate finalEndExclusive = endInclusive.plusDays(1);
        List<DateRange> periods = new ArrayList<>();
        LocalDate cursor = start;
        while (cursor.isBefore(finalEndExclusive)) {
            LocalDate scheduledEnd = nextBoundary(
                cursor, cadence, anchorWeekday, anchorDayOfMonth);
            LocalDate endExclusive = scheduledEnd.isAfter(finalEndExclusive)
                ? finalEndExclusive
                : scheduledEnd;
            periods.add(new DateRange(cursor, endExclusive));
            cursor = endExclusive;
        }
        return List.copyOf(periods);
    }

    public static DateRange periodContaining(LocalDate planStart, LocalDate date,
                                             PlanCadence cadence,
                                             Integer anchorWeekday,
                                             Integer anchorDayOfMonth) {
        if (date.isBefore(planStart)) {
            throw new IllegalArgumentException("Date cannot precede the plan start");
        }
        LocalDate cursor = planStart;
        while (true) {
            LocalDate end = nextBoundary(
                cursor, cadence, anchorWeekday, anchorDayOfMonth);
            DateRange range = new DateRange(cursor, end);
            if (range.contains(date)) {
                return range;
            }
            cursor = end;
        }
    }

    public static int countPeriods(LocalDate start, LocalDate endInclusive,
                                   PlanCadence cadence,
                                   Integer anchorWeekday,
                                   Integer anchorDayOfMonth) {
        return rangeFrom(start, endInclusive, cadence,
            anchorWeekday, anchorDayOfMonth).size();
    }

    public static void validateAnchor(PlanCadence cadence, Integer anchorWeekday,
                                      Integer anchorDayOfMonth) {
        if (cadence == null) {
            throw new IllegalArgumentException("Cadence is required");
        }
        switch (cadence) {
            case DAILY -> {
                if (anchorWeekday != null || anchorDayOfMonth != null) {
                    throw new IllegalArgumentException("Daily cadence has no anchor");
                }
            }
            case WEEKLY, BIWEEKLY -> {
                if (anchorWeekday == null || anchorWeekday < 1 || anchorWeekday > 7
                    || anchorDayOfMonth != null) {
                    throw new IllegalArgumentException(
                        "Weekly and biweekly cadences require an ISO weekday from 1 to 7");
                }
            }
            case MONTHLY -> {
                if (anchorDayOfMonth == null || anchorDayOfMonth < 1
                    || anchorDayOfMonth > 31 || anchorWeekday != null) {
                    throw new IllegalArgumentException(
                        "Monthly cadence requires a day from 1 to 31");
                }
            }
        }
    }

    private static LocalDate anchoredDay(YearMonth month, int requestedDay) {
        return month.atDay(Math.min(requestedDay, month.lengthOfMonth()));
    }
}
