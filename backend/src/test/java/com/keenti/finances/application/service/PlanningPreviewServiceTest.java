package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.Box;
import com.keenti.finances.domain.model.BoxPlan;
import com.keenti.finances.domain.model.PlanningPreview.Baseline;
import com.keenti.finances.domain.model.PlanningPreview.BoxBalance;
import com.keenti.finances.domain.model.PlanningPreview.CostInput;
import com.keenti.finances.domain.model.PlanningPreview.Eligibility;
import com.keenti.finances.domain.model.PlanningPreview.MissingInput;
import com.keenti.finances.domain.model.PlanningPreview.Note;
import com.keenti.finances.domain.model.PlanningPreview.ProjectionSnapshot;
import com.keenti.finances.domain.model.PlanningPreview.ReceiptKey;
import com.keenti.finances.domain.model.PlanningPreview.ReceiptSelection;
import com.keenti.finances.domain.model.PlanningPreview.Request;
import com.keenti.finances.domain.model.PlanningPreview.Resolution;
import com.keenti.finances.domain.model.PlanningPreview.Result;
import com.keenti.finances.domain.model.PlanningPreview.Timing;
import com.keenti.finances.domain.model.PlanningPreview.TimingReasonCode;
import com.keenti.finances.domain.model.PlanningPreview.TimingStatus;
import com.keenti.finances.domain.model.PlanningPreview.UndatedDebt;
import com.keenti.finances.domain.model.PlanningPreviewCalculator.ReceiptKind;
import com.keenti.finances.domain.model.PlanningPreviewCalculator.Reason;
import com.keenti.finances.domain.model.PlanningPreviewCalculator.Status;
import com.keenti.finances.domain.model.PlanningPreviewCalculator.Window;
import com.keenti.finances.domain.port.in.SavingGoalUseCase;
import com.keenti.finances.domain.port.in.SpendingBudgetUseCase;
import com.keenti.finances.domain.port.out.BoxPlanRepository;
import com.keenti.finances.domain.port.out.BoxRepository;
import com.keenti.finances.domain.port.out.UserTimeZoneProvider;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

import static com.keenti.finances.domain.model.PlanningPreviewFixtures.AT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Orchestration against a stub reader: section independence, receipt index
 * mapping and the order of plan evaluation. Values reuse the 5A fixture baseline
 * (N 10,000.00, Boxes 2,500.00 and 500.00) but nothing here touches storage.
 */
class PlanningPreviewServiceTest {

    private static final RuntimeException BOOM = new IllegalStateException("read failed");
    private static final LocalDate TODAY = LocalDate.parse("2026-09-20");
    private static final Baseline BASELINE = new Baseline(true, new BigDecimal("10000.00"),
        new BigDecimal("3000.00"), List.of(new BoxBalance(1, "Rent", new BigDecimal("2500.00")),
            new BoxBalance(2, "Trips", new BigDecimal("500.00"))), BigDecimal.ZERO);
    private static final Timing TIMING = new Timing(TimingStatus.COMPLETE, List.of(), List.of(), List.of(), List.of());

    private final List<String> calls = new ArrayList<>();
    private Supplier<ProjectionSnapshot> projection = () -> new ProjectionSnapshot(BASELINE, Map.of(), true);
    private Supplier<Timing> timing = () -> TIMING;
    private Supplier<List<UndatedDebt>> debts = List::of;
    private Supplier<ZoneId> zone = () -> ZoneId.of("America/Mexico_City");
    private Supplier<Object> goal = () -> Optional.empty();
    private Window timingWindow;
    private Collection<ReceiptKey> lookedUp;

    @SuppressWarnings("unchecked")
    private <T> T port(Class<T> type, Map<String, Supplier<Object>> answers) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (self, method, args) -> {
            var answer = answers.get(method.getName());
            if (answer == null) throw new AssertionError("unexpected " + type.getSimpleName() + "." + method.getName());
            return answer.get();
        });
    }

    private PlanningPreviewService service() {
        PlanningPreviewService service = new PlanningPreviewService();
        service.clock = () -> AT;
        service.reader = new PlanningSnapshotReader() {
            @Override public ProjectionSnapshot readProjection(Collection<ReceiptKey> receipts) {
                calls.add("projection");
                lookedUp = List.copyOf(receipts);
                return projection.get();
            }
            @Override public Timing readTiming(Window window) {
                calls.add("timing");
                timingWindow = window;
                return timing.get();
            }
            @Override public List<UndatedDebt> readUndatedDebts() {
                calls.add("debts");
                return debts.get();
            }
        };
        service.userTimeZoneProvider = port(UserTimeZoneProvider.class, Map.of("getTimeZone", () -> zone.get()));
        service.boxRepository = port(BoxRepository.class, Map.of("findAll", () -> List.of(
            new Box(1L, "Rent", 220, null, null, 0, BigDecimal.ZERO, false, null, null, 0))));
        service.boxPlanRepository = port(BoxPlanRepository.class, Map.of("findActiveByBoxId", () -> Optional.of(
            new BoxPlan(7L, 1L, BoxPlan.Type.SAVING_GOAL, BoxPlan.Status.ACTIVE, null, null, null, null, null))));
        service.savingGoalUseCase = port(SavingGoalUseCase.class, Map.of("getActive", () -> {
            calls.add("evaluate");
            return goal.get();
        }));
        service.spendingBudgetUseCase = port(SpendingBudgetUseCase.class, Map.of());
        return service;
    }

    private static CostInput cost(String amount, int day, Long box, String funding) {
        return new CostInput(new BigDecimal(amount), TODAY.plusDays(day), null, box,
            funding == null ? null : new BigDecimal(funding), true);
    }

    private static ReceiptSelection receipt(ReceiptKind kind, long id, int day) {
        return new ReceiptSelection(kind, id, TODAY.plusDays(day));
    }

    @Test
    void plansAreEvaluatedBeforeAnySnapshotIsRead() {
        Result result = service().preview(new Request(true, List.of(cost("1200.00", 12, null, null)), List.of()));
        assertEquals(List.of("evaluate", "projection", "timing", "debts"), calls);
        assertEquals(Status.COMPLETE, result.status());
        assertEquals(new BigDecimal("5800.00"), result.projected().totals().availableToSpend());
        assertEquals(AT, result.generatedAt());
        assertEquals(new Window(TODAY, TODAY.plusDays(29)), timingWindow);
    }

    @Test
    void aFailedPlanEvaluationIsANoteNotAnUnavailableProjection() {
        goal = () -> { throw BOOM; };
        Result result = service().preview(new Request(true, List.of(), List.of()));
        assertEquals(Status.COMPLETE, result.status());
        assertTrue(result.notes().contains(Note.PLAN_EVALUATION_INCOMPLETE));
    }

    @Test
    void anUnreadableBaselineLeavesTimingAndDebtsIntact() {
        projection = () -> { throw BOOM; };
        Result result = service().preview(new Request(true, List.of(), List.of()));
        assertEquals(Status.UNAVAILABLE, result.status());
        assertNull(result.projected());
        assertNull(result.baseline());
        assertEquals(List.of(Reason.BASELINE_UNAVAILABLE), reasons(result));
        assertEquals(TimingStatus.COMPLETE, result.timing().status());
        assertNotNull(result.undatedDebts());
    }

    @Test
    void failedTimingOrDebtReadsLeaveTheProjectionIntact() {
        timing = () -> { throw BOOM; };
        debts = () -> { throw BOOM; };
        Result result = service().preview(new Request(true, List.of(), List.of()));
        assertEquals(Status.COMPLETE, result.status());
        assertEquals(new BigDecimal("7000.00"), result.projected().totals().availableToSpend());
        assertEquals(TimingStatus.UNAVAILABLE, result.timing().status());
        assertEquals(TimingReasonCode.READ_FAILED, result.timing().reasons().getFirst().code());
        assertTrue(result.timing().dated().isEmpty());
        assertNull(result.undatedDebts());
    }

    @Test
    void unreadableReceiptsMakeTheProjectionUnavailableButKeepTheBaseline() {
        projection = () -> new ProjectionSnapshot(BASELINE, Map.of(), false);
        Result result = service().preview(new Request(true, List.of(),
            List.of(receipt(ReceiptKind.DEBT, 1, 3))));
        assertEquals(Status.UNAVAILABLE, result.status());
        assertEquals(List.of(Reason.RECEIPT_UNAVAILABLE), reasons(result));
        assertEquals(new BigDecimal("7000.00"), result.baseline().availableToSpend());
    }

    @Test
    void receiptProblemsNameTheRequestRowAndDuplicatesAreLookedUpOnce() {
        projection = () -> new ProjectionSnapshot(BASELINE, Map.of(
            new ReceiptKey(ReceiptKind.DEBT, 1), new Resolution(Eligibility.ELIGIBLE, new BigDecimal("4500.00")),
            new ReceiptKey(ReceiptKind.DEBT, 2), new Resolution(Eligibility.INELIGIBLE, null),
            new ReceiptKey(ReceiptKind.PAYMENT_RECORD, 5), new Resolution(Eligibility.ELIGIBLE, new BigDecimal("133.33"))),
            true);
        Result result = service().preview(new Request(false, List.of(cost("10.00", 1, null, null)), List.of(
            receipt(ReceiptKind.DEBT, 9, 1),               // 0 not found
            receipt(ReceiptKind.DEBT, 1, 1),               // 1 eligible
            receipt(ReceiptKind.DEBT, 2, 1),               // 2 ineligible
            receipt(ReceiptKind.DEBT, 1, 2),               // 3 duplicate of 1
            receipt(ReceiptKind.PAYMENT_RECORD, 5, 40),    // 4 eligible, date outside
            receipt(ReceiptKind.PAYMENT_RECORD, 0, 1))));  // 5 non-positive: unknown, not looked up
        assertEquals(Status.UNAVAILABLE, result.status());
        assertNull(result.projected());
        assertEquals(List.of(new ReceiptKey(ReceiptKind.DEBT, 9), new ReceiptKey(ReceiptKind.DEBT, 1),
            new ReceiptKey(ReceiptKind.DEBT, 2), new ReceiptKey(ReceiptKind.PAYMENT_RECORD, 5)), lookedUp);
        assertEquals(Map.of(0, Reason.RECEIPT_NOT_FOUND, 2, Reason.RECEIPT_INELIGIBLE,
                3, Reason.DUPLICATE_RECEIPT, 4, Reason.DATE_OUT_OF_WINDOW, 5, Reason.RECEIPT_NOT_FOUND),
            result.missingInputs().stream().collect(java.util.stream.Collectors.toMap(
                MissingInput::receiptIndex, MissingInput::reason)));
        // Review confirmations are not reported for an invalid scenario.
        assertTrue(result.missingInputs().stream().noneMatch(p -> p.reason() == Reason.ESSENTIALS_NOT_REVIEWED));
        assertTrue(result.receipts().isEmpty());
    }

    @Test
    void resolvedReceiptsUseServerAmountsAndReportTheirRequestRows() {
        projection = () -> new ProjectionSnapshot(BASELINE, Map.of(
            new ReceiptKey(ReceiptKind.DEBT, 1), new Resolution(Eligibility.ELIGIBLE, new BigDecimal("4500.00"))), true);
        Result result = service().preview(new Request(true, List.of(),
            List.of(receipt(ReceiptKind.DEBT, 1, 20))));
        // FX-HORIZON-INCOME-OPTIN-01.
        assertEquals(new BigDecimal("14500.00"), result.projected().totals().netBalance());
        assertEquals(new BigDecimal("11500.00"), result.projected().totals().availableToSpend());
        assertEquals(0, result.receipts().getFirst().receiptIndex());
        assertTrue(result.notes().contains(Note.RECEIPTS_IF_RECEIVED));
    }

    @Test
    void anUnusableZoneNeverFallsBackToAnotherCalendar() {
        zone = () -> { throw BOOM; };
        Result result = service().preview(new Request(true, List.of(), List.of()));
        assertEquals(Status.UNAVAILABLE, result.status());
        assertEquals(List.of(Reason.ZONE_UNAVAILABLE), reasons(result));
        assertNull(result.window());
        assertNull(timingWindow);
        assertNotNull(result.baseline());
    }

    @Test
    void boxCapacityIsCheckedAgainstTheSnapshotAndNamesTheBox() {
        // FX-HORIZON-BOXSHORT-01 with the fixture's Box balances.
        Result result = service().preview(new Request(true,
            List.of(cost("2000.00", 1, 2L, "600.00")), List.of()));
        assertEquals(Status.UNAVAILABLE, result.status());
        MissingInput problem = result.missingInputs().getFirst();
        assertEquals(Reason.BOX_CAPACITY_EXCEEDED, problem.reason());
        assertEquals(2L, problem.boxId());
        assertEquals(new BigDecimal("100.00"), problem.shortfall());
    }

    @Test
    void creditInFavorIsNotedOnlyWhenACalculatedBaselineIsReturned() {
        BigDecimal favor = new BigDecimal("55.50");
        projection = () -> new ProjectionSnapshot(new Baseline(true, BASELINE.netBalance(),
            BASELINE.inBoxes(), BASELINE.boxes(), favor), Map.of(), true);
        Result consistent = service().preview(new Request(true, List.of(), List.of()));
        assertNotNull(consistent.baseline());
        assertTrue(consistent.notes().contains(Note.CREDIT_IN_FAVOR_IN_BASELINE));

        // Read fine, but In Boxes disagrees with the Boxes: no baseline, no note.
        projection = () -> new ProjectionSnapshot(new Baseline(true, BASELINE.netBalance(),
            new BigDecimal("3000.01"), BASELINE.boxes(), favor), Map.of(), true);
        Result inconsistent = service().preview(new Request(true, List.of(), List.of()));
        assertNull(inconsistent.baseline());
        assertEquals(List.of(Reason.BASELINE_UNAVAILABLE), reasons(inconsistent));
        assertFalse(inconsistent.notes().contains(Note.CREDIT_IN_FAVOR_IN_BASELINE));
    }

    private static List<Reason> reasons(Result result) {
        return result.missingInputs().stream().map(MissingInput::reason).toList();
    }
}
