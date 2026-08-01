package com.keenti.finances.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlanScheduleTest {

    @Test
    void dailyPeriodsIncludeTheTargetDate() {
        List<PlanSchedule.DateRange> periods = PlanSchedule.rangeFrom(
            LocalDate.parse("2026-07-30"),
            LocalDate.parse("2026-08-01"),
            PlanCadence.DAILY,
            null,
            null);

        assertEquals(3, periods.size());
        assertEquals(LocalDate.parse("2026-08-02"),
            periods.get(2).endExclusive());
    }

    @Test
    void weeklyAnchorUsesIsoWeekdayAndAllowsAnInitialPartialPeriod() {
        List<PlanSchedule.DateRange> periods = PlanSchedule.rangeFrom(
            LocalDate.parse("2026-07-29"), // Wednesday
            LocalDate.parse("2026-08-10"),
            PlanCadence.WEEKLY,
            1, // Monday
            null);

        assertEquals(LocalDate.parse("2026-08-03"),
            periods.get(0).endExclusive());
        assertEquals(LocalDate.parse("2026-08-10"),
            periods.get(1).endExclusive());
        assertEquals(LocalDate.parse("2026-08-11"),
            periods.get(2).endExclusive());
    }

    @Test
    void monthEndAnchorClampsAndRecoversRequestedDay() {
        assertEquals(LocalDate.parse("2027-02-28"),
            PlanSchedule.nextBoundary(
                LocalDate.parse("2027-01-31"), PlanCadence.MONTHLY, null, 31));
        assertEquals(LocalDate.parse("2027-03-31"),
            PlanSchedule.nextBoundary(
                LocalDate.parse("2027-02-28"), PlanCadence.MONTHLY, null, 31));
    }

    @Test
    void userLocalDateKeepsDailyBoundariesStableAcrossDst() {
        ZoneId newYork = ZoneId.of("America/New_York");
        LocalDate beforeSpringForward = LocalDate.ofInstant(
            Instant.parse("2027-03-14T04:30:00Z"), newYork);
        LocalDate afterSpringForward = LocalDate.ofInstant(
            Instant.parse("2027-03-14T07:30:00Z"), newYork);

        assertEquals(LocalDate.parse("2027-03-13"), beforeSpringForward);
        assertEquals(LocalDate.parse("2027-03-14"), afterSpringForward);
        assertEquals(LocalDate.parse("2027-03-15"),
            PlanSchedule.nextBoundary(
                afterSpringForward, PlanCadence.DAILY, null, null));
    }

    @Test
    void cadenceRejectsMismatchedAnchors() {
        assertThrows(IllegalArgumentException.class, () ->
            PlanSchedule.nextBoundary(
                LocalDate.now(), PlanCadence.WEEKLY, null, null));
        assertThrows(IllegalArgumentException.class, () ->
            PlanSchedule.nextBoundary(
                LocalDate.now(), PlanCadence.DAILY, 1, null));
    }
}
