package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static com.keenti.finances.domain.model.PlanningPreviewCalculator.*;

/** Invented D5 values, reusable by slice 5B; no shared development records. */
public final class PlanningPreviewFixtures {
    public static final Instant AT = Instant.parse("2026-09-20T18:00:00Z");
    public static final String ZONE = "America/Mexico_City";
    public static final Baseline BASELINE = new Baseline(d("10000"), d("3000"), Map.of(1L, d("2500"), 2L, d("500")));
    public record Scenario(String id, Baseline baseline, List<Cost> costs, List<Receipt> receipts,
                           boolean reviewed, Status status, String net, String boxes, String unallocated) {
        public Result calculate() {
            return PlanningPreviewCalculator.calculate(baseline, AT, ZONE, costs, receipts, reviewed);
        }
    }
    public static final List<Scenario> SCENARIOS = List.of(
        new Scenario("FX-HORIZON-CASH-01", BASELINE, List.of(cost("1200", "2026-10-02", null, null)), List.of(), true,
            Status.COMPLETE, "8800", "3000", "5800"),
        new Scenario("FX-HORIZON-BOXFUNDED-01", BASELINE, List.of(cost("2500", "2026-10-01", 1L, "2500")), List.of(), true,
            Status.COMPLETE, "7500", "500", "7000"),
        new Scenario("FX-HORIZON-BOXSHORT-01", new Baseline(d("10000"), d("3000"), Map.of(1L, d("2000"), 2L, d("1000"))),
            List.of(cost("2500", "2026-10-01", 1L, "2500")), List.of(), true, Status.UNAVAILABLE, null, null, null),
        // Recorded card 900 + statement 3100 belong to 5B's adapter fixtures.
        // They are already reflected in this baseline; no new cost is passed here.
        new Scenario("FX-HORIZON-STMT-NEUTRAL-01", BASELINE, List.of(), List.of(), true,
            Status.COMPLETE, "10000", "3000", "7000"),
        // Recorded future EGRESS 1100 is likewise a baseline fact, not a new cost.
        new Scenario("FX-HORIZON-FUTURE-RECORDED-01", BASELINE, List.of(), List.of(), true,
            Status.COMPLETE, "10000", "3000", "7000"),
        new Scenario("FX-HORIZON-INCOME-OPTIN-01", BASELINE, List.of(), List.of(receipt(ReceiptKind.DEBT, 1, "4500", "2026-10-10")), true,
            Status.COMPLETE, "14500", "3000", "11500"),
        new Scenario("FX-HORIZON-PARTIAL-01", BASELINE, List.of(), List.of(), false,
            Status.PARTIAL, "10000", "3000", "7000")
    );
    public static BigDecimal d(String amount) { return new BigDecimal(amount); }
    public static Cost cost(String amount, String date, Long boxId, String funding) {
        return new Cost(d(amount), LocalDate.parse(date), boxId, funding == null ? null : d(funding), true);
    }
    public static Receipt receipt(ReceiptKind kind, long id, String amount, String date) {
        return new Receipt(kind, id, d(amount), LocalDate.parse(date));
    }
    private PlanningPreviewFixtures() {}
}
