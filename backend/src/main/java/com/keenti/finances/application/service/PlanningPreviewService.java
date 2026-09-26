package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.Box;
import com.keenti.finances.domain.model.BoxPlan;
import com.keenti.finances.domain.model.PlanningPreview.Baseline;
import com.keenti.finances.domain.model.PlanningPreview.BoxBalance;
import com.keenti.finances.domain.model.PlanningPreview.BoxProjection;
import com.keenti.finances.domain.model.PlanningPreview.CostInput;
import com.keenti.finances.domain.model.PlanningPreview.Eligibility;
import com.keenti.finances.domain.model.PlanningPreview.IncludedReceipt;
import com.keenti.finances.domain.model.PlanningPreview.MissingInput;
import com.keenti.finances.domain.model.PlanningPreview.Note;
import com.keenti.finances.domain.model.PlanningPreview.Projection;
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
import com.keenti.finances.domain.model.PlanningPreviewCalculator;
import com.keenti.finances.domain.model.PlanningPreviewCalculator.Cost;
import com.keenti.finances.domain.model.PlanningPreviewCalculator.Problem;
import com.keenti.finances.domain.model.PlanningPreviewCalculator.Reason;
import com.keenti.finances.domain.model.PlanningPreviewCalculator.Receipt;
import com.keenti.finances.domain.model.PlanningPreviewCalculator.Status;
import com.keenti.finances.domain.model.PlanningPreviewCalculator.Window;
import com.keenti.finances.domain.port.in.PlanningPreviewUseCase;
import com.keenti.finances.domain.port.in.SavingGoalUseCase;
import com.keenti.finances.domain.port.in.SpendingBudgetUseCase;
import com.keenti.finances.domain.port.out.BoxPlanRepository;
import com.keenti.finances.domain.port.out.BoxRepository;
import com.keenti.finances.domain.port.out.UserTimeZoneProvider;
import com.keenti.finances.infrastructure.adapter.in.rest.UserScoped;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.jboss.logging.Logger;

/**
 * D5 planning preview (slice 5B): resolves a scenario against a fresh server
 * snapshot and the 5A calculator.
 *
 * <p>Order matters. The permitted lazy Box Plan evaluation (ADR-0021) runs
 * first, in the plan services' own transactions, so it is committed before any
 * balance is read; it records plan progress and moves no money. The three
 * sections are then read by {@link PlanningSnapshotReader}, each in its own
 * read-only snapshot, so a failure in one leaves the others intact.
 *
 * <p>No billing is generated, no cursor advances, no Transaction, Box Funding,
 * Box Movement or account activity is created.
 */
@ApplicationScoped
@UserScoped
public class PlanningPreviewService implements PlanningPreviewUseCase {

    private static final Logger LOG = Logger.getLogger(PlanningPreviewService.class);
    private static final Set<Reason> REVIEW_REASONS =
        Set.of(Reason.ESSENTIALS_NOT_REVIEWED, Reason.COST_NOT_CONFIRMED_UNRECORDED);

    @Inject PlanningSnapshotReader reader;
    @Inject UserTimeZoneProvider userTimeZoneProvider;
    @Inject BoxRepository boxRepository;
    @Inject BoxPlanRepository boxPlanRepository;
    @Inject SavingGoalUseCase savingGoalUseCase;
    @Inject SpendingBudgetUseCase spendingBudgetUseCase;

    /** The one captured instant; replaceable in unit tests. */
    Supplier<Instant> clock = Instant::now;

    @Override
    public Result preview(Request request) {
        Instant capturedAt = clock.get();
        String zone = zone();
        Window window = PlanningPreviewCalculator.window(capturedAt, zone);
        List<Note> notes = new ArrayList<>();
        if (!evaluatePlans()) notes.add(Note.PLAN_EVALUATION_INCOMPLETE);

        // Duplicates are rejected before resolution, so a repeated ID is never
        // read or counted twice, whatever its eligibility.
        List<Problem> resolution = new ArrayList<>();
        Map<ReceiptKey, Integer> firstIndex = new LinkedHashMap<>();
        List<ReceiptSelection> selections = request.expectedReceipts();
        for (int i = 0; i < selections.size(); i++) {
            ReceiptSelection selection = selections.get(i);
            ReceiptKey key = new ReceiptKey(selection.recordKind(), selection.recordId());
            if (firstIndex.putIfAbsent(key, i) != null) {
                resolution.add(receiptProblem(Reason.DUPLICATE_RECEIPT, i));
            }
        }
        Set<ReceiptKey> lookups = new LinkedHashSet<>();
        firstIndex.keySet().stream().filter(key -> key.id() > 0).forEach(lookups::add);

        ProjectionSnapshot projection = guarded("projection",
            () -> reader.readProjection(lookups), ProjectionSnapshot.UNREADABLE);
        Timing timing = guarded("timing", () -> reader.readTiming(window),
            Timing.withoutData(TimingStatus.UNAVAILABLE, TimingReasonCode.READ_FAILED));
        List<UndatedDebt> undatedDebts = guarded("undated-debts", reader::readUndatedDebts, null);

        Baseline source = projection.baseline();
        List<Receipt> receipts = new ArrayList<>();
        List<Integer> requestIndex = new ArrayList<>();
        if (source != null) {
            if (!lookups.isEmpty() && !projection.receiptsReadable()) {
                resolution.add(new Problem(Reason.RECEIPT_UNAVAILABLE, null, null, null, null));
            } else {
                for (var entry : firstIndex.entrySet()) {
                    int index = entry.getValue();
                    Resolution resolved = projection.receipts().get(entry.getKey());
                    if (resolved == null || resolved.eligibility() == Eligibility.NOT_FOUND) {
                        // Unknown, trashed or another User's: one indistinguishable reason.
                        resolution.add(receiptProblem(Reason.RECEIPT_NOT_FOUND, index));
                    } else if (resolved.eligibility() == Eligibility.INELIGIBLE) {
                        resolution.add(receiptProblem(Reason.RECEIPT_INELIGIBLE, index));
                    } else {
                        ReceiptSelection selection = selections.get(index);
                        receipts.add(new Receipt(selection.recordKind(), selection.recordId(),
                            resolved.amount(), selection.date()));
                        requestIndex.add(index);
                    }
                }
            }
        }

        PlanningPreviewCalculator.Result calculated = PlanningPreviewCalculator.calculate(
            calculatorBaseline(source), capturedAt, zone, costs(request.items()), receipts,
            request.essentialsReviewed());

        List<MissingInput> missing = new ArrayList<>();
        // Resolution problems exist only for rows the calculator never saw; any one
        // of them makes the whole projection unavailable rather than a smaller subtotal.
        boolean forceUnavailable = !resolution.isEmpty();
        for (Problem problem : calculated.missingInputs()) {
            // Review confirmations are moot once a row is invalid: nothing is shown.
            if (forceUnavailable && REVIEW_REASONS.contains(problem.reason())) continue;
            Integer receiptIndex = problem.receiptIndex() == null ? null
                : requestIndex.get(problem.receiptIndex());
            missing.add(new MissingInput(problem.reason(), problem.itemIndex(), receiptIndex,
                problem.boxId(), problem.shortfall()));
        }
        resolution.stream().map(MissingInput::of).forEach(missing::add);

        Status status = forceUnavailable ? Status.UNAVAILABLE : calculated.status();
        Projection projected = status == Status.UNAVAILABLE || calculated.projected() == null ? null
            : projection(source, calculated.projected());
        List<IncludedReceipt> included = new ArrayList<>();
        if (projected != null) {
            for (int i = 0; i < receipts.size(); i++) {
                Receipt receipt = receipts.get(i);
                included.add(new IncludedReceipt(requestIndex.get(i), receipt.recordKind(),
                    receipt.recordId(), receipt.amount(), receipt.date()));
            }
            if (!included.isEmpty()) notes.add(Note.RECEIPTS_IF_RECEIVED);
        }

        notes.add(Note.LEDGER_TOTAL_NOT_CASH);
        notes.add(Note.NO_AUTOMATIC_TRANSACTION_MATCHING);
        notes.add(Note.UNENTERED_COSTS_EXCLUDED);
        // Only when a calculated baseline is returned: a read-but-inconsistent
        // source is BASELINE_UNAVAILABLE and the note would point at nothing.
        if (calculated.baseline() != null && source.creditInFavor() != null
                && source.creditInFavor().signum() > 0) {
            notes.add(Note.CREDIT_IN_FAVOR_IN_BASELINE);
        }

        LOG.infof("planning.preview status=%s timing=%s costs=%d receipts=%d",
            status, timing.status(), request.items().size(), selections.size());
        return new Result(capturedAt, window == null ? null : zone, window, source,
            calculated.baseline(), projected, status, List.copyOf(missing), List.copyOf(included),
            timing, undatedDebts, List.copyOf(notes));
    }

    private String zone() {
        try {
            return userTimeZoneProvider.getTimeZone().getId();
        } catch (RuntimeException e) {
            // Never UTC or the server zone (D2): the calculator reports ZONE_UNAVAILABLE.
            LOG.warnf("planning.preview zone unavailable: %s", e.toString());
            return null;
        }
    }

    /**
     * ADR-0021's lazy period evaluation for every active plan, before balances.
     * A failure leaves that plan's guidance stale but changes no balance, so it
     * is reported as a note rather than blocking the preview.
     */
    private boolean evaluatePlans() {
        boolean complete = true;
        List<Box> boxes;
        try {
            boxes = boxRepository.findAll(false);
        } catch (RuntimeException e) {
            LOG.warnf(e, "planning.preview plan evaluation skipped");
            return false;
        }
        for (Box box : boxes) {
            try {
                var plan = boxPlanRepository.findActiveByBoxId(box.getId());
                if (plan.isEmpty()) continue;
                if (plan.get().type() == BoxPlan.Type.SAVING_GOAL) {
                    savingGoalUseCase.getActive(box.getId());
                } else {
                    spendingBudgetUseCase.getActive(box.getId());
                }
            } catch (RuntimeException e) {
                LOG.warnf(e, "planning.preview plan evaluation failed box=%d", box.getId());
                complete = false;
            }
        }
        return complete;
    }

    private <T> T guarded(String section, Supplier<T> read, T unavailable) {
        try {
            return read.get();
        } catch (RuntimeException e) {
            // Transaction begin/rollback failures surface here rather than inside
            // the reader's own handling; they still affect one section only.
            LOG.errorf(e, "planning.preview section=%s unavailable", section);
            return unavailable;
        }
    }

    private static PlanningPreviewCalculator.Baseline calculatorBaseline(Baseline source) {
        if (source == null) return null;
        Map<Long, BigDecimal> boxes = new LinkedHashMap<>();
        for (BoxBalance box : source.boxes()) boxes.put(box.boxId(), box.balance());
        return new PlanningPreviewCalculator.Baseline(source.netBalance(), source.inBoxes(), boxes);
    }

    private static List<Cost> costs(List<CostInput> items) {
        return items.stream().map(item -> new Cost(item.amount(), item.date(), item.boxId(),
            item.boxAmount(), item.notYetRecordedConfirmed())).toList();
    }

    private static Projection projection(Baseline source, PlanningPreviewCalculator.Projection projected) {
        List<BoxProjection> perBox = source.boxes().stream()
            .map(box -> new BoxProjection(box.boxId(), box.name(), box.balance(),
                projected.perBox().get(box.boxId())))
            .toList();
        return new Projection(projected.totals(), perBox);
    }

    private static Problem receiptProblem(Reason reason, int index) {
        return new Problem(reason, null, index, null, null);
    }
}
