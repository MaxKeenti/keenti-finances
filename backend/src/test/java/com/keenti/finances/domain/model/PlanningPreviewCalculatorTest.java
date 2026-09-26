package com.keenti.finances.domain.model;

import static com.keenti.finances.domain.model.PlanningPreviewCalculator.*;
import static com.keenti.finances.domain.model.PlanningPreviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class PlanningPreviewCalculatorTest {
    @TestFactory
    Stream<DynamicTest> namedDecisionFixtures() {
        return SCENARIOS.stream().map(s -> DynamicTest.dynamicTest(s.id(), () -> {
            Result result = s.calculate();
            assertEquals(s.status(), result.status());
            assertEquals(new Window(LocalDate.parse("2026-09-20"), LocalDate.parse("2026-10-19")), result.window());
            assertEquals(AT, result.generatedAt());
            if (s.status() == Status.UNAVAILABLE) {
                assertNull(result.projected());
                assertEquals(List.of(new Problem(Reason.BOX_CAPACITY_EXCEEDED, null, null, 1L, d("500.00"))), result.missingInputs());
            } else check(result, s.net(), s.boxes(), s.unallocated());
        }));
    }
    @Test
    void reducingRequestedFundingPaysRemainderFromUnallocatedMoney() {
        Result result = calculate(SCENARIOS.get(2).baseline(), AT, ZONE,
            List.of(cost("2500", "2026-10-01", 1L, "2000")), List.of(), true);
        check(result, "7500", "1000", "6500");
        assertEquals(Map.of(1L, d("0.00"), 2L, d("1000.00")), result.projected().perBox());
    }
    @Test
    void newCardCostUsesFullAmountAndSharedCostsDoNotNetContributions() {
        check(run(List.of(cost("700", "2026-10-08", null, null)), List.of()), "9300", "3000", "6300");
        var shared = List.of(cost("400", "2026-10-03", null, null));
        check(run(shared, List.of()), "9600", "3000", "6600");
        check(run(shared, List.of(receipt(ReceiptKind.PAYMENT_RECORD, 1, "133.33", "2026-10-03"),
            receipt(ReceiptKind.PAYMENT_RECORD, 2, "133.33", "2026-10-04"))), "9866.66", "3000", "6866.66");
    }
    @Test
    void emptyScenarioAddsNoExpectedIncomeOrRecordedContext() {
        // D5 examples 4, 7, 8-off, 10, 11: card/MSI/future Transactions and credit
        // in favor are baseline facts, not inputs. Storage exclusion needs 5B tests.
        check(run(List.of(), List.of()), "10000", "3000", "7000");
    }
    @Test
    void aggregateFundingCannotReuseTheSameBoxBalance() {
        Result result = run(List.of(cost("1500", "2026-09-20", 1L, "1500"), cost("1500", "2026-09-21", 1L, "1500")), List.of());
        unavailable(result, Reason.BOX_CAPACITY_EXCEEDED);
        assertEquals(d("500.00"), result.missingInputs().getFirst().shortfall());
        check(run(List.of(cost("1500", "2026-09-20", 1L, "1500"), cost("1000", "2026-09-21", 1L, "1000")), List.of()), "7500", "500", "7000");
    }
    @Test
    void negativeUnallocatedAndNegativeNetRemainValid() {
        for (String net : List.of("2000", "-100")) {
            Result result = calculate(new Baseline(d(net), BASELINE.inBoxes(), BASELINE.boxBalances()), AT, ZONE,
                List.of(cost("100", "2026-09-21", null, null)), List.of(), true);
            assertEquals(Status.COMPLETE, result.status());
            assertEquals(d(net).subtract(d("3100")).setScale(2), result.projected().totals().availableToSpend());
            invariant(result);
        }
    }
    @Test
    void existingFixtureShapesPreserveNegativeAvailabilityAndNoPlanBoxes() {
        // FX-DASH-NEG-01 from frontend/tests/fixtures/scenarios.ts (0B).
        Baseline negative = new Baseline(d("4176"), d("5300"), Map.of(9216L, d("3500"), 9217L, d("1800")));
        check(calculate(negative, AT, ZONE, List.of(), List.of(), true), "4176", "5300", "-1124");
        check(calculate(negative, AT, ZONE, List.of(cost("100", "2026-09-20", null, null)), List.of(), true), "4076", "5300", "-1224");
        // FX-BOX-NOPLAN-01: both balances count even without a readable active plan.
        Baseline noPlan = new Baseline(d("2400"), d("900"), Map.of(9214L, d("600"), 9215L, d("300")));
        Result result = calculate(noPlan, AT, ZONE, List.of(cost("650", "2026-09-20", 9214L, "600")), List.of(), true);
        check(result, "1750", "300", "1450");
        assertEquals(Map.of(9214L, d("0.00"), 9215L, d("300.00")), result.projected().perBox());
    }
    @Test
    void mixedBoxesAndReceiptsPreserveIndependentFundingAndExactResiduals() {
        Result result = run(List.of(cost("2600", "2026-09-21", 1L, "2500"),
            cost("400.01", "2026-09-22", 2L, "399.99")),
            List.of(receipt(ReceiptKind.DEBT, 1, "100.01", "2026-09-23")));
        check(result, "7100", "100.01", "6999.99");
        assertEquals(Map.of(1L, d("0.00"), 2L, d("100.01")), result.projected().perBox());
    }
    @Test
    void missingReviewsProduceExplicitPartialSubtotals() {
        Cost cost = new Cost(d("20"), LocalDate.parse("2026-09-20"), null, null, false);
        Result result = calculate(BASELINE, AT, ZONE, List.of(cost), List.of(), false);
        assertEquals(Status.PARTIAL, result.status());
        check(result, "9980", "3000", "6980");
        assertEquals(List.of(Reason.ESSENTIALS_NOT_REVIEWED, Reason.COST_NOT_CONFIRMED_UNRECORDED),
            result.missingInputs().stream().map(Problem::reason).toList());
        assertEquals(0, result.missingInputs().get(1).itemIndex());
    }
    @TestFactory
    Stream<DynamicTest> invalidMoneyIsNotRoundedIntoAValidProjection() {
        return Arrays.asList(null, "0", "-1", "0.001", "10000000").stream().map(amount ->
            DynamicTest.dynamicTest("amount " + amount, () -> {
                Cost cost = new Cost(amount == null ? null : d(amount), LocalDate.parse("2026-09-20"), null, null, true);
                unavailable(run(List.of(cost), List.of()), Reason.INVALID_AMOUNT);
                unavailable(run(List.of(), List.of(new Receipt(ReceiptKind.DEBT, 1, cost.amount(), cost.date()))), Reason.INVALID_AMOUNT);
            }));
    }
    @Test
    void exactCentsAndTrailingZerosAreAcceptedWithoutLimitingAggregateTotals() {
        Result result = run(Collections.nCopies(50, cost("9999999.990", "2026-09-20", null, null)), List.of());
        check(result, "-499989999.50", "3000", "-499992999.50");
        check(run(List.of(cost("0.01", "2026-09-20", null, null)), List.of()), "9999.99", "3000", "6999.99");
    }
    @Test
    void invalidRowCannotDisappearFromSubtotal() {
        Result result = run(List.of(cost("1", "2026-09-20", null, null), cost("1", "2026-10-20", null, null)), List.of());
        unavailable(result, Reason.DATE_OUT_OF_WINDOW);
        assertEquals(1, result.missingInputs().getFirst().itemIndex());
    }
    @Test
    void fundingRequiresKnownBoxNonnegativeCentsAndNoMoreThanCost() {
        unavailable(run(List.of(cost("10", "2026-09-20", null, "1")), List.of()), Reason.BOX_REQUIRED);
        unavailable(run(List.of(cost("10", "2026-09-20", 99L, "1")), List.of()), Reason.BOX_NOT_FOUND);
        unavailable(run(List.of(cost("10", "2026-09-20", 1L, "-1")), List.of()), Reason.INVALID_FUNDING);
        unavailable(run(List.of(cost("10", "2026-09-20", 1L, "0.001")), List.of()), Reason.INVALID_FUNDING);
        unavailable(run(List.of(cost("10", "2026-09-20", 1L, "11")), List.of()), Reason.FUNDING_EXCEEDS_COST);
        unavailable(run(List.of(cost("10", "2026-09-20", 1L, "10000000")), List.of()), Reason.INVALID_FUNDING);
    }
    @Test
    void extremeExponentFundingIsRejectedBeforeAnyArithmetic() {
        // Passes the cents check and is positive; subtracting a Box balance from it
        // would expand ~a billion digits. It must be refused on magnitude alone.
        for (String huge : List.of("1e999999999", "9.99e999999998")) {
            Result result = assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),
                () -> run(List.of(cost("10", "2026-09-20", 1L, huge)), List.of()));
            assertEquals(Status.UNAVAILABLE, result.status());
            assertNull(result.projected());
            assertEquals(List.of(new Problem(Reason.INVALID_FUNDING, 0, null, 1L, null)), result.missingInputs());
        }
        // The same bound holds for the cost amount and for a negligible zero scale.
        unavailable(assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),
            () -> run(List.of(cost("1e999999999", "2026-09-20", null, null)), List.of())), Reason.INVALID_AMOUNT);
        check(assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),
            () -> run(List.of(cost("10", "2026-09-20", 1L, "0e-999999999")), List.of())), "9990", "3000", "6990");
    }
    @Test
    void fundingAboveItsCostIsNotAccumulatedIntoABoxShortfall() {
        // Only the row error: the over-cost amount never reaches the Box total.
        Result result = run(List.of(cost("10", "2026-09-20", 1L, "9999999.99")), List.of());
        assertEquals(List.of(new Problem(Reason.FUNDING_EXCEEDS_COST, 0, null, 1L, null)), result.missingInputs());
    }
    @Test
    void duplicateReceiptIdentityIncludesItsKind() {
        Receipt debt = receipt(ReceiptKind.DEBT, 1, "10", "2026-09-20");
        unavailable(run(List.of(), List.of(debt, receipt(ReceiptKind.DEBT, 1, "10", "2026-09-21"))), Reason.DUPLICATE_RECEIPT);
        check(run(List.of(), List.of(debt, receipt(ReceiptKind.PAYMENT_RECORD, 1, "10", "2026-09-20"))), "10020", "3000", "7020");
        unavailable(run(List.of(), List.of(receipt(ReceiptKind.DEBT, 1, "10", "2026-10-20"))), Reason.DATE_OUT_OF_WINDOW);
        unavailable(run(List.of(), List.of(new Receipt(ReceiptKind.DEBT, 1, d("10"), null))), Reason.DATE_OUT_OF_WINDOW);
    }
    @Test
    void userZoneDeterminesCalendarWindowAndBothEndpointsAreIncluded() {
        Instant boundary = Instant.parse("2026-09-20T04:30:00Z");
        assertEquals(new Window(LocalDate.parse("2026-09-19"), LocalDate.parse("2026-10-18")),
            calculate(BASELINE, boundary, ZONE, List.of(), List.of(), true).window());
        assertEquals(new Window(LocalDate.parse("2026-09-20"), LocalDate.parse("2026-10-19")),
            calculate(BASELINE, boundary, "Asia/Tokyo", List.of(), List.of(), true).window());
        assertEquals(Status.COMPLETE, run(List.of(cost("1", "2026-09-20", null, null), cost("1", "2026-10-19", null, null)), List.of()).status());
        unavailable(run(List.of(cost("1", "2026-09-19", null, null)), List.of()), Reason.DATE_OUT_OF_WINDOW);
        unavailable(run(List.of(new Cost(d("1"), null, null, null, true)), List.of()), Reason.DATE_OUT_OF_WINDOW);
    }
    @Test
    void calendarDaysSurviveLeapYearYearEndAndDst() {
        for (String[] row : List.of(new String[]{"2028-02-01T17:00:00Z", "2028-02-01", "2028-03-01"},
                new String[]{"2026-12-20T17:00:00Z", "2026-12-20", "2027-01-18"},
                new String[]{"2026-03-01T17:00:00Z", "2026-03-01", "2026-03-30"},
                new String[]{"2026-10-20T16:00:00Z", "2026-10-20", "2026-11-18"})) {
            assertEquals(new Window(LocalDate.parse(row[1]), LocalDate.parse(row[2])),
                calculate(BASELINE, Instant.parse(row[0]), "America/New_York", List.of(), List.of(), true).window());
        }
    }
    @Test
    void missingOrInvalidZoneNeverFallsBackToSystemCalendar() {
        for (String zone : Arrays.asList(null, "", "bad/zone", "+02:00")) {
            Result result = calculate(BASELINE, AT, zone, List.of(), List.of(), true);
            unavailable(result, Reason.ZONE_UNAVAILABLE);
            assertNull(result.window());
            assertNotNull(result.baseline());
        }
    }
    @Test
    void invalidOrIncompleteBoxSnapshotIsNotAnEmptyBalance() {
        for (Baseline baseline : Arrays.asList(null, new Baseline(null, d("0"), Map.of()),
                new Baseline(d("10"), d("1"), Map.of()),
                new Baseline(d("10"), d("-1"), Map.of(1L, d("-1"))),
                new Baseline(d("10"), d("0.001"), Map.of(1L, d("0.001"))))) {
            Result result = calculate(baseline, AT, ZONE, List.of(), List.of(), true);
            unavailable(result, Reason.BASELINE_UNAVAILABLE);
            assertNull(result.baseline());
        }
        check(calculate(new Baseline(d("0"), d("0"), Map.of()), AT, ZONE, List.of(), List.of(), true), "0", "0", "0");
    }
    @Test
    void snapshotAndResultCollectionsCannotMutateBoxBalances() {
        var boxes = new HashMap<>(BASELINE.boxBalances());
        var baseline = new Baseline(BASELINE.netBalance(), BASELINE.inBoxes(), boxes);
        boxes.clear();
        Result result = calculate(baseline, AT, ZONE, List.of(cost("100", "2026-09-20", 1L, "100")), List.of(), true);
        assertEquals(d("2500"), baseline.boxBalances().get(1L));
        assertEquals(d("2400.00"), result.projected().perBox().get(1L));
        assertThrows(UnsupportedOperationException.class, () -> result.projected().perBox().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.missingInputs().clear());
        assertThrows(UnsupportedOperationException.class, () -> baseline.boxBalances().clear());
    }
    @Test
    void structuralMisuseIsSeparateFromBusinessUnavailability() {
        assertThrows(IllegalArgumentException.class, () -> calculate(BASELINE, null, ZONE, List.of(), List.of(), true));
        assertThrows(IllegalArgumentException.class, () -> calculate(BASELINE, AT, ZONE, null, List.of(), true));
        assertThrows(IllegalArgumentException.class, () -> run(Arrays.asList((Cost) null), List.of()));
        assertThrows(IllegalArgumentException.class, () -> run(List.of(), List.of(new Receipt(null, 1, d("1"), LocalDate.parse("2026-09-20")))));
        assertThrows(IllegalArgumentException.class, () -> run(List.of(), List.of(receipt(ReceiptKind.DEBT, 0, "1", "2026-09-20"))));
        assertThrows(IllegalArgumentException.class, () -> run(Collections.nCopies(51, cost("1", "2026-09-20", null, null)), List.of()));
        assertThrows(IllegalArgumentException.class, () -> run(List.of(), Collections.nCopies(51, receipt(ReceiptKind.DEBT, 1, "1", "2026-09-20"))));
    }
    @Test
    void publicWindowIsTheCalendarRowsAreValidatedAgainst() {
        // Slice 5B derives the timing window from this same method.
        assertEquals(run(List.of(), List.of()).window(), PlanningPreviewCalculator.window(AT, ZONE));
        assertNull(PlanningPreviewCalculator.window(null, ZONE));
        assertNull(PlanningPreviewCalculator.window(AT, "+02:00"));
    }
    private static Result run(List<Cost> costs, List<Receipt> receipts) { return calculate(BASELINE, AT, ZONE, costs, receipts, true); }
    private static void unavailable(Result result, Reason reason) {
        assertEquals(Status.UNAVAILABLE, result.status());
        assertNull(result.projected());
        assertTrue(result.missingInputs().stream().anyMatch(p -> p.reason() == reason));
    }
    private static void check(Result result, String net, String boxes, String unallocated) {
        assertNotEquals(Status.UNAVAILABLE, result.status());
        assertEquals(d(net).setScale(2), result.projected().totals().netBalance());
        assertEquals(d(boxes).setScale(2), result.projected().totals().inBoxes());
        assertEquals(d(unallocated).setScale(2), result.projected().totals().availableToSpend());
        invariant(result);
    }
    private static void invariant(Result result) {
        Totals totals = result.projected().totals();
        assertEquals(totals.netBalance().subtract(totals.inBoxes()), totals.availableToSpend());
        assertEquals(totals.inBoxes(), result.projected().perBox().values().stream().reduce(d("0.00"), BigDecimal::add));
        assertTrue(result.projected().perBox().values().stream().allMatch(v -> v.signum() >= 0));
    }
}
