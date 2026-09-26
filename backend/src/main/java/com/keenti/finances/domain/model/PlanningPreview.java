package com.keenti.finances.domain.model;

import com.keenti.finances.domain.model.PlanningPreviewCalculator.Problem;
import com.keenti.finances.domain.model.PlanningPreviewCalculator.ReceiptKind;
import com.keenti.finances.domain.model.PlanningPreviewCalculator.Status;
import com.keenti.finances.domain.model.PlanningPreviewCalculator.Totals;
import com.keenti.finances.domain.model.PlanningPreviewCalculator.Window;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * D5 preview read model (slice 5B). Three sections are read independently:
 * the projection baseline with its receipts, the statement timing list and the
 * undated Debts. A failure in one never removes or zeroes another.
 */
public final class PlanningPreview {
    private PlanningPreview() {}

    /* Request, already structurally valid. Client-side totals do not exist here. */

    public record Request(boolean essentialsReviewed, List<CostInput> items,
                          List<ReceiptSelection> expectedReceipts) {}
    public record CostInput(BigDecimal amount, LocalDate date, String description, Long boxId,
                            BigDecimal boxAmount, boolean notYetRecordedConfirmed) {}
    /** Identity and scenario date only; the amount is always reloaded by the server. */
    public record ReceiptSelection(ReceiptKind recordKind, long recordId, LocalDate date) {}
    public record ReceiptKey(ReceiptKind kind, long id) {}

    /* Projection snapshot: one REPEATABLE READ transaction. */

    public record BoxBalance(long boxId, String name, BigDecimal balance) {}
    /** creditInFavor is null before tracking; it is inside netBalance and is not cash. */
    public record Baseline(boolean trackingActive, BigDecimal netBalance, BigDecimal inBoxes,
                           List<BoxBalance> boxes, BigDecimal creditInFavor) {}
    public enum Eligibility { ELIGIBLE, NOT_FOUND, INELIGIBLE }
    /** amount is set only when ELIGIBLE: full remaining Debt or full Payment Record amount. */
    public record Resolution(Eligibility eligibility, BigDecimal amount) {}
    /**
     * baseline null: the baseline could not be read. receiptsReadable false: the
     * baseline was read but selected receipts could not be resolved.
     */
    public record ProjectionSnapshot(Baseline baseline, Map<ReceiptKey, Resolution> receipts,
                                     boolean receiptsReadable) {
        public static final ProjectionSnapshot UNREADABLE = new ProjectionSnapshot(null, Map.of(), false);
        public ProjectionSnapshot { receipts = Map.copyOf(receipts); }
    }

    /* Timing: confirmed Credit Statements only, never netted into the projection. */

    public enum TimingStatus { COMPLETE, PARTIAL, UNAVAILABLE, NOT_APPLICABLE }
    public enum TimingReasonCode {
        TRACKING_INACTIVE, ZONE_UNAVAILABLE, READ_FAILED, STATEMENT_DUE_DATE_MISSING,
        RECONCILIATION_MISMATCH, UNCONFIRMED_STATEMENT, STATEMENT_SCHEDULE_MISSING
    }
    public record TimingReason(TimingReasonCode code, Long accountId, Long statementId) {}
    public record StatementDue(long accountId, String accountName, long statementId,
                               LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate,
                               BigDecimal officialBalance, BigDecimal paidAmount,
                               BigDecimal outstandingBalance, BigDecimal minimumPayment,
                               BigDecimal avoidInterest, boolean reconciliationMismatch) {}
    /** Context for an unconfirmed period; never an obligation and never a deduction. */
    public record StatementEstimate(long accountId, String accountName, LocalDate periodStart,
                                    LocalDate periodEnd, LocalDate dueDate,
                                    BigDecimal estimatedBalance) {}
    public record Timing(TimingStatus status, List<StatementDue> dated, List<StatementDue> overdue,
                         List<StatementEstimate> estimates, List<TimingReason> reasons) {
        public Timing {
            dated = List.copyOf(dated);
            overdue = List.copyOf(overdue);
            estimates = List.copyOf(estimates);
            reasons = List.copyOf(reasons);
        }
        public static Timing withoutData(TimingStatus status, TimingReasonCode reason) {
            return new Timing(status, List.of(), List.of(), List.of(),
                List.of(new TimingReason(reason, null, null)));
        }
    }

    /* Undated Debts: both Debt Directions, one row per Debt, never netted (ADR-0023). */

    public record UndatedDebt(long debtId, String direction, Long contactId, String contactName,
                              String description, BigDecimal totalAmount, BigDecimal paidAmount,
                              BigDecimal remaining) {}

    /* Result */

    /** A missing input or invalid row; receiptIndex is the request row, not a resolved index. */
    public record MissingInput(PlanningPreviewCalculator.Reason reason, Integer itemIndex,
                               Integer receiptIndex, Long boxId, BigDecimal shortfall) {
        public static MissingInput of(Problem problem) {
            return new MissingInput(problem.reason(), problem.itemIndex(), problem.receiptIndex(),
                problem.boxId(), problem.shortfall());
        }
    }
    public record IncludedReceipt(int receiptIndex, ReceiptKind recordKind, long recordId,
                                  BigDecimal amount, LocalDate date) {}
    public record BoxProjection(long boxId, String name, BigDecimal balance, BigDecimal projectedBalance) {}
    public record Projection(Totals totals, List<BoxProjection> perBox) {}
    public enum Note {
        LEDGER_TOTAL_NOT_CASH, NO_AUTOMATIC_TRANSACTION_MATCHING, UNENTERED_COSTS_EXCLUDED,
        CREDIT_IN_FAVOR_IN_BASELINE, RECEIPTS_IF_RECEIVED, PLAN_EVALUATION_INCOMPLETE
    }

    /**
     * baseline and projected are null when unreadable/unavailable, never zero.
     * undatedDebts is null when its read failed; an empty list means none recorded.
     */
    public record Result(Instant generatedAt, String timeZone, Window window, Baseline baselineSource,
                         Totals baseline, Projection projected, Status status,
                         List<MissingInput> missingInputs, List<IncludedReceipt> receipts,
                         Timing timing, List<UndatedDebt> undatedDebts, List<Note> notes) {}
}
