package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * D5's pure scenario calculation, not an account-liquidity forecast.
 * The caller supplies a consistent, User-scoped baseline and only selected,
 * eligible receipts reloaded from storage. Recorded Transactions, statements,
 * MSI schedules and Box Plan suggestions must never become scenario costs.
 * No clock, persistence access or financial writes occur here.
 */
public final class PlanningPreviewCalculator {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999.99");
    private static final int MAX_ROWS = 50;

    private PlanningPreviewCalculator() {}

    public enum Status { COMPLETE, PARTIAL, UNAVAILABLE }
    public enum ReceiptKind { DEBT, PAYMENT_RECORD }
    public enum Reason {
        BASELINE_UNAVAILABLE, ZONE_UNAVAILABLE, DATE_OUT_OF_WINDOW,
        INVALID_AMOUNT, INVALID_FUNDING, BOX_REQUIRED, BOX_NOT_FOUND,
        FUNDING_EXCEEDS_COST, BOX_CAPACITY_EXCEEDED, DUPLICATE_RECEIPT,
        ESSENTIALS_NOT_REVIEWED, COST_NOT_CONFIRMED_UNRECORDED
    }

    /** inBoxes must equal the sum of every active Box, including those with no plan. */
    public record Baseline(BigDecimal netBalance, BigDecimal inBoxes,
                           Map<Long, BigDecimal> boxBalances) {
        public Baseline {
            if (boxBalances != null) {
                boxBalances = Collections.unmodifiableMap(new LinkedHashMap<>(boxBalances));
            }
        }
    }
    public record Cost(BigDecimal amount, LocalDate date, Long boxId,
                       BigDecimal boxAmount, boolean notYetRecordedConfirmed) {}
    /** amount is the server-resolved full remaining Debt / full Payment Record amount. */
    public record Receipt(ReceiptKind recordKind, long recordId,
                          BigDecimal amount, LocalDate date) {}
    public record Window(LocalDate from, LocalDate to) {}
    public record Totals(BigDecimal netBalance, BigDecimal inBoxes,
                         BigDecimal availableToSpend) {}
    public record Projection(Totals totals, Map<Long, BigDecimal> perBox) {
        public Projection { perBox = Collections.unmodifiableMap(new LinkedHashMap<>(perBox)); }
    }
    /** Zero-based indices address the input rows; unrelated fields are null. */
    public record Problem(Reason reason, Integer itemIndex, Integer receiptIndex,
                          Long boxId, BigDecimal shortfall) {}
    public record Result(Instant generatedAt, Window window, Totals baseline,
                         Projection projected, Status status, List<Problem> missingInputs) {
        public Result { missingInputs = List.copyOf(missingInputs); }
    }

    /**
     * Structural mistakes throw IllegalArgumentException (future HTTP adapter: 4xx).
     * Business-invalid inputs return UNAVAILABLE with no projection. Valid inputs
     * lacking review confirmations produce a PARTIAL subtotal, never a complete claim.
     * Timing availability is handled independently by the future application service.
     */
    public static Result calculate(Baseline source, Instant capturedAt, String userZone,
                                   List<Cost> costs, List<Receipt> receipts,
                                   boolean essentialsReviewed) {
        requireStructure(capturedAt, costs, receipts);
        List<Problem> problems = new ArrayList<>();
        Totals baseline = baseline(source);
        if (baseline == null) problems.add(problem(Reason.BASELINE_UNAVAILABLE));
        Window window = window(capturedAt, userZone);
        if (window == null) problems.add(problem(Reason.ZONE_UNAVAILABLE));
        if (!problems.isEmpty()) return unavailable(capturedAt, window, baseline, problems);

        BigDecimal expenses = ZERO;
        BigDecimal income = ZERO;
        Map<Long, BigDecimal> funding = new TreeMap<>();
        for (int i = 0; i < costs.size(); i++) {
            Cost cost = costs.get(i);
            boolean amountValid = positiveAmount(cost.amount());
            if (!amountValid) problems.add(costProblem(Reason.INVALID_AMOUNT, i, cost.boxId()));
            if (!inside(cost.date(), window)) {
                problems.add(costProblem(Reason.DATE_OUT_OF_WINDOW, i, cost.boxId()));
            }
            BigDecimal allocated = cost.boxAmount() == null ? ZERO : cost.boxAmount();
            boolean fundingValid = cents(allocated) && allocated.signum() >= 0;
            if (!fundingValid) problems.add(costProblem(Reason.INVALID_FUNDING, i, cost.boxId()));
            if (fundingValid && amountValid && allocated.compareTo(cost.amount()) > 0) {
                problems.add(costProblem(Reason.FUNDING_EXCEEDS_COST, i, cost.boxId()));
            }
            if (cost.boxId() == null && fundingValid && allocated.signum() > 0) {
                problems.add(costProblem(Reason.BOX_REQUIRED, i, null));
            } else if (cost.boxId() != null && !source.boxBalances().containsKey(cost.boxId())) {
                problems.add(costProblem(Reason.BOX_NOT_FOUND, i, cost.boxId()));
            } else if (cost.boxId() != null && fundingValid) {
                funding.merge(cost.boxId(), allocated, BigDecimal::add);
            }
            if (amountValid) expenses = expenses.add(cost.amount());
        }
        var seenReceipts = new HashSet<ReceiptKey>();
        for (int i = 0; i < receipts.size(); i++) {
            Receipt receipt = receipts.get(i);
            if (!seenReceipts.add(new ReceiptKey(receipt.recordKind(), receipt.recordId()))) {
                problems.add(receiptProblem(Reason.DUPLICATE_RECEIPT, i));
            }
            if (!positiveAmount(receipt.amount())) {
                problems.add(receiptProblem(Reason.INVALID_AMOUNT, i));
            } else {
                income = income.add(receipt.amount());
            }
            if (!inside(receipt.date(), window)) {
                problems.add(receiptProblem(Reason.DATE_OUT_OF_WINDOW, i));
            }
        }
        funding.forEach((id, amount) -> {
            BigDecimal shortfall = amount.subtract(source.boxBalances().get(id));
            if (shortfall.signum() > 0) {
                problems.add(new Problem(Reason.BOX_CAPACITY_EXCEEDED, null, null, id, money(shortfall)));
            }
        });
        if (!problems.isEmpty()) return unavailable(capturedAt, window, baseline, problems);

        Map<Long, BigDecimal> perBox = new TreeMap<>();
        source.boxBalances().forEach((id, amount) ->
            perBox.put(id, money(amount.subtract(funding.getOrDefault(id, ZERO)))));
        BigDecimal totalFunding = funding.values().stream().reduce(ZERO, BigDecimal::add);
        BigDecimal projectedNet = baseline.netBalance().subtract(expenses).add(income);
        BigDecimal projectedBoxes = baseline.inBoxes().subtract(totalFunding);
        Projection projected = new Projection(totals(projectedNet, projectedBoxes), perBox);
        if (!essentialsReviewed) problems.add(problem(Reason.ESSENTIALS_NOT_REVIEWED));
        for (int i = 0; i < costs.size(); i++) {
            if (!costs.get(i).notYetRecordedConfirmed()) {
                problems.add(costProblem(Reason.COST_NOT_CONFIRMED_UNRECORDED, i, costs.get(i).boxId()));
            }
        }
        return new Result(capturedAt, window, baseline, projected,
            problems.isEmpty() ? Status.COMPLETE : Status.PARTIAL, problems);
    }

    private record ReceiptKey(ReceiptKind kind, long id) {}

    private static void requireStructure(Instant instant, List<Cost> costs, List<Receipt> receipts) {
        if (instant == null || costs == null || receipts == null
                || costs.size() > MAX_ROWS || receipts.size() > MAX_ROWS
                || costs.stream().anyMatch(c -> c == null)
                || receipts.stream().anyMatch(r -> r == null || r.recordKind() == null || r.recordId() <= 0)) {
            throw new IllegalArgumentException("A captured instant and at most 50 non-null costs and receipts are required; receipts need a kind and positive ID");
        }
    }

    private static Totals baseline(Baseline source) {
        if (source == null || !cents(source.netBalance()) || !cents(source.inBoxes())
                || source.inBoxes().signum() < 0 || source.boxBalances() == null) return null;
        BigDecimal sum = ZERO;
        for (var box : source.boxBalances().entrySet()) {
            if (box.getKey() == null || box.getKey() <= 0 || !cents(box.getValue())
                    || box.getValue().signum() < 0) return null;
            sum = sum.add(box.getValue());
        }
        return sum.compareTo(source.inBoxes()) == 0 ? totals(source.netBalance(), source.inBoxes()) : null;
    }

    private static Window window(Instant instant, String zone) {
        // ZoneId.of also accepts raw offsets; the contract requires an IANA zone.
        if (zone == null || !ZoneId.getAvailableZoneIds().contains(zone)) return null;
        try {
            LocalDate today = instant.atZone(ZoneId.of(zone)).toLocalDate();
            return new Window(today, today.plusDays(29));
        } catch (DateTimeException exception) {
            return null;
        }
    }
    private static boolean inside(LocalDate date, Window window) {
        return date != null && !date.isBefore(window.from()) && !date.isAfter(window.to());
    }
    private static boolean cents(BigDecimal value) {
        return value != null && value.stripTrailingZeros().scale() <= 2;
    }
    private static boolean positiveAmount(BigDecimal value) {
        return cents(value) && value.signum() > 0 && value.compareTo(MAX_AMOUNT) <= 0;
    }
    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.UNNECESSARY); }
    private static Totals totals(BigDecimal net, BigDecimal boxes) {
        return new Totals(money(net), money(boxes), money(net.subtract(boxes)));
    }
    private static Problem problem(Reason reason) { return new Problem(reason, null, null, null, null); }
    private static Problem costProblem(Reason reason, int index, Long boxId) {
        return new Problem(reason, index, null, boxId, null);
    }
    private static Problem receiptProblem(Reason reason, int index) {
        return new Problem(reason, null, index, null, null);
    }
    private static Result unavailable(Instant at, Window window, Totals baseline, List<Problem> problems) {
        return new Result(at, window, baseline, null, Status.UNAVAILABLE, problems);
    }
}
