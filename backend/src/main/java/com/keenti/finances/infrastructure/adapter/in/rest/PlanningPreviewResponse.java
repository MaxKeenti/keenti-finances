package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.PlanningPreview;
import com.keenti.finances.domain.model.PlanningPreview.BoxProjection;
import com.keenti.finances.domain.model.PlanningPreview.IncludedReceipt;
import com.keenti.finances.domain.model.PlanningPreview.MissingInput;
import com.keenti.finances.domain.model.PlanningPreview.StatementDue;
import com.keenti.finances.domain.model.PlanningPreview.StatementEstimate;
import com.keenti.finances.domain.model.PlanningPreview.TimingReason;
import com.keenti.finances.domain.model.PlanningPreview.UndatedDebt;
import com.keenti.finances.domain.model.PlanningPreviewCalculator.Totals;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * The D5 envelope. Unavailable values are null, never zero: a null projection,
 * baseline or undatedDebts list says the figure is unknown. Status strings use
 * the contract's spelling; reason and note codes are stable identifiers.
 */
public record PlanningPreviewResponse(
    Instant generatedAt,
    String timeZone,
    Window window,
    Baseline baseline,
    Projected projected,
    String status,
    List<MissingInput> missingInputs,
    List<IncludedReceipt> includedReceipts,
    Timing timing,
    List<UndatedDebt> undatedDebts,
    String undatedDebtsStatus,
    List<String> notes
) {
    public record Window(LocalDate from, LocalDate to) {}
    /** source is "accounts" or "transactions"; creditInFavor is inside netBalance and is not cash. */
    public record Baseline(BigDecimal netBalance, BigDecimal inBoxes, BigDecimal availableToSpend,
                           String source, BigDecimal creditInFavor) {}
    public record Projected(BigDecimal netBalance, BigDecimal inBoxes, BigDecimal availableToSpend,
                            List<BoxProjection> perBox) {}
    public record Timing(String status, List<StatementDue> dated, List<StatementDue> overdue,
                         List<StatementEstimate> estimates, List<TimingReason> reasons) {}

    static PlanningPreviewResponse from(PlanningPreview.Result result) {
        Totals baseline = result.baseline();
        var source = result.baselineSource();
        var projected = result.projected();
        var timing = result.timing();
        return new PlanningPreviewResponse(
            result.generatedAt(),
            result.timeZone(),
            result.window() == null ? null : new Window(result.window().from(), result.window().to()),
            baseline == null || source == null ? null : new Baseline(baseline.netBalance(),
                baseline.inBoxes(), baseline.availableToSpend(),
                source.trackingActive() ? "accounts" : "transactions", source.creditInFavor()),
            projected == null ? null : new Projected(projected.totals().netBalance(),
                projected.totals().inBoxes(), projected.totals().availableToSpend(), projected.perBox()),
            result.status().name().toLowerCase(Locale.ROOT),
            result.missingInputs(),
            result.receipts(),
            new Timing(switch (timing.status()) {
                case COMPLETE -> "complete";
                case PARTIAL -> "partial";
                case UNAVAILABLE -> "unavailable";
                case NOT_APPLICABLE -> "notApplicable";
            }, timing.dated(), timing.overdue(), timing.estimates(), timing.reasons()),
            result.undatedDebts(),
            result.undatedDebts() == null ? "unavailable" : "available",
            result.notes().stream().map(Enum::name).toList());
    }
}
