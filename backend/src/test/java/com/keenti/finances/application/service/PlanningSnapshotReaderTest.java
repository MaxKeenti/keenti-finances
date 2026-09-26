package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.Box;
import com.keenti.finances.domain.model.CreditAccountSettings;
import com.keenti.finances.domain.model.CreditStatement;
import com.keenti.finances.domain.model.CreditStatementEstimate;
import com.keenti.finances.domain.model.Debt;
import com.keenti.finances.domain.model.FinancialAccount;
import com.keenti.finances.domain.model.PaymentRecordReceiptCandidate;
import com.keenti.finances.domain.model.PlanningPreview.Eligibility;
import com.keenti.finances.domain.model.PlanningPreview.ProjectionSnapshot;
import com.keenti.finances.domain.model.PlanningPreview.ReceiptKey;
import com.keenti.finances.domain.model.PlanningPreview.Timing;
import com.keenti.finances.domain.model.PlanningPreview.TimingReasonCode;
import com.keenti.finances.domain.model.PlanningPreview.TimingStatus;
import com.keenti.finances.domain.model.PlanningPreview.UndatedDebt;
import com.keenti.finances.domain.model.PlanningPreviewCalculator.ReceiptKind;
import com.keenti.finances.domain.model.PlanningPreviewCalculator.Window;
import com.keenti.finances.domain.port.in.CreditStatementUseCase;
import com.keenti.finances.domain.port.out.BoxRepository;
import com.keenti.finances.domain.port.out.ConsistentReadScope;
import com.keenti.finances.domain.port.out.ContactRepository;
import com.keenti.finances.domain.port.out.CreditAccountSettingsRepository;
import com.keenti.finances.domain.port.out.DebtPaymentRepository;
import com.keenti.finances.domain.port.out.DebtRepository;
import com.keenti.finances.domain.port.out.FinancialAccountRepository;
import com.keenti.finances.domain.port.out.PaymentRecordRepository;
import com.keenti.finances.domain.port.out.TransactionRepository;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Section reads against stub ports. They establish the classification rules and
 * that a failed read yields an unavailable section — never a partial list or a
 * zero — and that every section discards its snapshot. Whether the real
 * transaction and isolation behave is established by the Quarkus tests.
 */
class PlanningSnapshotReaderTest {

    private static final RuntimeException BOOM = new IllegalStateException("read failed");
    private static final LocalDate TODAY = LocalDate.parse("2026-09-20");
    private static final Window WINDOW = new Window(TODAY, TODAY.plusDays(29));

    private final Map<Class<?>, Map<String, Function<Object[], Object>>> answers = new HashMap<>();
    private final List<String> scope = new ArrayList<>();

    private void on(Class<?> port, String method, Function<Object[], Object> answer) {
        answers.computeIfAbsent(port, key -> new HashMap<>()).put(method, answer);
    }

    private void on(Class<?> port, String method, Object value) {
        on(port, method, args -> value);
    }

    private void throwing(Class<?> port, String method) {
        on(port, method, args -> { throw BOOM; });
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> port) {
        return (T) Proxy.newProxyInstance(port.getClassLoader(), new Class<?>[] {port},
            (self, method, args) -> {
                if (method.getName().equals("toString")) return port.getSimpleName();
                var answer = answers.getOrDefault(port, Map.of()).get(method.getName());
                if (answer == null) throw new AssertionError("unexpected " + port.getSimpleName() + "." + method.getName());
                return answer.apply(args);
            });
    }

    private PlanningSnapshotReader reader() {
        PlanningSnapshotReader reader = new PlanningSnapshotReader();
        reader.readScope = new ConsistentReadScope() {
            @Override public void begin() { scope.add("begin"); }
            @Override public void discard() { scope.add("discard"); }
        };
        reader.financialAccountRepository = proxy(FinancialAccountRepository.class);
        reader.transactionRepository = proxy(TransactionRepository.class);
        reader.boxRepository = proxy(BoxRepository.class);
        reader.debtRepository = proxy(DebtRepository.class);
        reader.debtPaymentRepository = proxy(DebtPaymentRepository.class);
        reader.paymentRecordRepository = proxy(PaymentRecordRepository.class);
        reader.contactRepository = proxy(ContactRepository.class);
        reader.creditStatementUseCase = proxy(CreditStatementUseCase.class);
        reader.creditAccountSettingsRepository = proxy(CreditAccountSettingsRepository.class);
        return reader;
    }

    /* ---------------------------------------------------------------------
     * Projection
     * ------------------------------------------------------------------ */

    private void baseline(boolean tracking) {
        on(FinancialAccountRepository.class, "isTrackingActive", tracking);
        on(FinancialAccountRepository.class, "getTotalBalance", new BigDecimal("10000.00"));
        on(TransactionRepository.class, "getNetBalance", new BigDecimal("400.00"));
        on(BoxRepository.class, "findAll", List.of(box(1, "2500.00"), box(2, "500.00")));
        on(BoxRepository.class, "getTotalBalance", new BigDecimal("3000.00"));
        on(FinancialAccountRepository.class, "findAll", List.of(
            account(10, "CREDIT", "55.50"), account(11, "CREDIT", "-80.00"), account(12, "DEBIT", "9000.00")));
    }

    @Test
    void baselineFollowsTheTrackingBranchAndCountsOnlyCreditInFavor() {
        baseline(true);
        ProjectionSnapshot tracked = reader().readProjection(List.of());
        assertEquals(new BigDecimal("10000.00"), tracked.baseline().netBalance());
        assertEquals(new BigDecimal("55.50"), tracked.baseline().creditInFavor());
        assertEquals(List.of(1L, 2L), tracked.baseline().boxes().stream().map(b -> b.boxId()).toList());

        baseline(false);
        ProjectionSnapshot legacy = reader().readProjection(List.of());
        assertEquals(new BigDecimal("400.00"), legacy.baseline().netBalance());
        assertNull(legacy.baseline().creditInFavor());
        assertEquals(List.of("begin", "discard", "begin", "discard"), scope);
    }

    @Test
    void aFailedBaselineReadIsUnreadableAndStillDiscardsTheSnapshot() {
        baseline(true);
        throwing(BoxRepository.class, "getTotalBalance");
        assertSame(ProjectionSnapshot.UNREADABLE, reader().readProjection(List.of()));
        assertEquals(List.of("begin", "discard"), scope);
    }

    @Test
    void aFailedIsolationStatementIsAnUnreadableBaseline() {
        PlanningSnapshotReader reader = reader();
        reader.readScope = new ConsistentReadScope() {
            @Override public void begin() { throw BOOM; }
            @Override public void discard() { scope.add("discard"); }
        };
        assertSame(ProjectionSnapshot.UNREADABLE, reader.readProjection(List.of()));
        assertEquals(List.of("discard"), scope);
    }

    @Test
    void aFailedReceiptReadKeepsTheBaselineButResolvesNothing() {
        baseline(true);
        throwing(PaymentRecordRepository.class, "findReceiptCandidates");
        ProjectionSnapshot snapshot = reader().readProjection(
            List.of(new ReceiptKey(ReceiptKind.PAYMENT_RECORD, 5)));
        assertNotNull(snapshot.baseline());
        assertFalse(snapshot.receiptsReadable());
        assertTrue(snapshot.receipts().isEmpty());
    }

    @Test
    void receiptEligibilityUsesReloadedStatusDirectionAndRemainingAmounts() {
        baseline(true);
        on(DebtRepository.class, "findAll", List.of(
            debt(1, "INGRESS", "ACTIVE", "4500.00"), debt(2, "EGRESS", "ACTIVE", "100.00"),
            debt(3, "INGRESS", "PAID", "100.00"), debt(4, "INGRESS", "ACTIVE", "100.00"),
            debt(6, "INGRESS", "ACTIVE", "10000000.01"), debt(7, "INGRESS", "ACTIVE", "10000000.00"),
            debt(9, "INGRESS", "ACTIVE", "1.00")));
        on(DebtPaymentRepository.class, "sumByDebtIds", Map.of(1L, new BigDecimal("500.00"),
            4L, new BigDecimal("100.00"), 7L, new BigDecimal("0.01")));
        // A trashed Subscription's record is absent from the query, like 25.
        on(PaymentRecordRepository.class, "findReceiptCandidates", List.of(
            new PaymentRecordReceiptCandidate(20L, "PENDING", new BigDecimal("133.33"), 7L),
            new PaymentRecordReceiptCandidate(21L, "PAID", new BigDecimal("133.33"), 7L),
            new PaymentRecordReceiptCandidate(22L, "PENDING", new BigDecimal("149.00"), null),
            new PaymentRecordReceiptCandidate(23L, "PENDING", new BigDecimal("10000000.00"), 7L),
            new PaymentRecordReceiptCandidate(24L, "PENDING", new BigDecimal("0.00"), 7L)));

        var keys = new ArrayList<ReceiptKey>();
        for (long id : List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L)) keys.add(new ReceiptKey(ReceiptKind.DEBT, id));
        for (long id : List.of(20L, 21L, 22L, 23L, 24L, 25L)) keys.add(new ReceiptKey(ReceiptKind.PAYMENT_RECORD, id));
        var resolved = reader().readProjection(keys).receipts();

        assertEquals(new BigDecimal("4000.00"), resolved.get(keys.get(0)).amount());
        assertEquals(Eligibility.INELIGIBLE, resolved.get(keys.get(1)).eligibility());   // EGRESS
        assertEquals(Eligibility.INELIGIBLE, resolved.get(keys.get(2)).eligibility());   // PAID
        assertEquals(Eligibility.INELIGIBLE, resolved.get(keys.get(3)).eligibility());   // fully paid, still ACTIVE
        assertEquals(Eligibility.NOT_FOUND, resolved.get(keys.get(4)).eligibility());
        // Above the D5 money bound: ineligible, never passed on as an invalid amount.
        assertEquals(Eligibility.INELIGIBLE, resolved.get(keys.get(5)).eligibility());
        assertEquals(new BigDecimal("9999999.99"), resolved.get(keys.get(6)).amount());  // exactly the bound
        assertEquals(new BigDecimal("133.33"), resolved.get(keys.get(7)).amount());
        for (int i = 8; i <= 11; i++) assertEquals(Eligibility.INELIGIBLE, resolved.get(keys.get(i)).eligibility());
        assertEquals(Eligibility.NOT_FOUND, resolved.get(keys.get(12)).eligibility());
    }

    /* ---------------------------------------------------------------------
     * Timing
     * ------------------------------------------------------------------ */

    private void card(List<CreditStatement> statements, boolean scheduled, BigDecimal estimate) {
        on(FinancialAccountRepository.class, "isTrackingActive", true);
        on(FinancialAccountRepository.class, "findAll", List.of(account(10, "CREDIT", "0.00"), account(12, "DEBIT", "10.00")));
        on(CreditStatementUseCase.class, "list", statements);
        on(CreditStatementUseCase.class, "estimateOutstandingBalance", args -> statements.stream()
            .filter(s -> s.periodEnd().equals(args[1])).findFirst()
            .map(CreditStatement::estimatedBalance).orElse(BigDecimal.ZERO));
        on(CreditAccountSettingsRepository.class, "findByAccountId", scheduled
            ? java.util.Optional.of(new CreditAccountSettings(10L, new BigDecimal("9000.00"), 5, 20))
            : java.util.Optional.empty());
        on(CreditStatementUseCase.class, "estimateCurrentStatement", args -> {
            LocalDate day = (LocalDate) args[1];
            return day.isBefore(TODAY.minusDays(14))
                ? new CreditStatementEstimate(TODAY.minusDays(75), TODAY.minusDays(46), TODAY.minusDays(31), estimate)
                : day.isBefore(TODAY)
                    ? new CreditStatementEstimate(TODAY.minusDays(45), TODAY.minusDays(15), TODAY, estimate)
                    : new CreditStatementEstimate(TODAY.minusDays(14), TODAY.plusDays(15), TODAY.plusDays(25), estimate);
        });
    }

    @Test
    void classifiesOverdueInWindowAndLaterStatementsWithoutDroppingOverdue() {
        List<CreditStatement> statements = List.of(
            statement(1, TODAY.minusDays(1), "100.00", "0.00"),       // overdue
            statement(2, TODAY, "200.00", "50.00"),                   // first day
            statement(3, TODAY.plusDays(29), "300.00", "0.00"),       // last day
            statement(4, TODAY.plusDays(30), "400.00", "0.00"),       // after window
            statement(5, TODAY.plusDays(3), "90.00", "90.00"));       // settled
        card(statements, true, BigDecimal.ZERO);
        Timing timing = reader().readTiming(WINDOW);
        assertEquals(TimingStatus.COMPLETE, timing.status());
        assertEquals(List.of(1L), timing.overdue().stream().map(s -> s.statementId()).toList());
        assertEquals(List.of(2L, 3L), timing.dated().stream().map(s -> s.statementId()).toList());
        assertEquals(new BigDecimal("150.00"), timing.dated().getFirst().outstandingBalance());
        assertEquals(List.of("begin", "discard"), scope);
    }

    @Test
    void sourceGapsMakeTimingPartialWithReasonsAndNoInventedObligations() {
        CreditStatement noDue = new CreditStatement(6L, 10L, TODAY.minusDays(40), TODAY.minusDays(10), null,
            BigDecimal.ZERO, new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.ZERO, null, null, BigDecimal.ZERO);
        CreditStatement mismatched = statement(7, TODAY.plusDays(4), "70.00", "0.00");
        card(List.of(noDue, mismatched), true, new BigDecimal("640.00"));
        on(CreditStatementUseCase.class, "estimateOutstandingBalance", args -> new BigDecimal("1.00"));

        Timing timing = reader().readTiming(WINDOW);
        assertEquals(TimingStatus.PARTIAL, timing.status());
        var codes = timing.reasons().stream().map(r -> r.code()).toList();
        assertTrue(codes.contains(TimingReasonCode.STATEMENT_DUE_DATE_MISSING));
        assertTrue(codes.contains(TimingReasonCode.RECONCILIATION_MISMATCH));
        assertTrue(codes.contains(TimingReasonCode.UNCONFIRMED_STATEMENT));
        assertTrue(timing.dated().getFirst().reconciliationMismatch());
        // Only the confirmed statement is an obligation; estimates are context.
        assertEquals(List.of(7L), timing.dated().stream().map(s -> s.statementId()).toList());
        assertFalse(timing.estimates().isEmpty());
    }

    @Test
    void aMissingScheduleIsPartialNotComplete() {
        card(List.of(), false, BigDecimal.ZERO);
        Timing timing = reader().readTiming(WINDOW);
        assertEquals(TimingStatus.PARTIAL, timing.status());
        assertEquals(TimingReasonCode.STATEMENT_SCHEDULE_MISSING, timing.reasons().getFirst().code());
    }

    @Test
    void anyFailedStatementReadMakesTimingUnavailableRatherThanShort() {
        card(List.of(statement(1, TODAY, "10.00", "0.00")), true, BigDecimal.ZERO);
        throwing(CreditStatementUseCase.class, "estimateCurrentStatement");
        Timing timing = reader().readTiming(WINDOW);
        assertEquals(TimingStatus.UNAVAILABLE, timing.status());
        assertEquals(TimingReasonCode.READ_FAILED, timing.reasons().getFirst().code());
        assertTrue(timing.dated().isEmpty());
        assertEquals(List.of("begin", "discard"), scope);
    }

    @Test
    void trackingOffIsNotApplicableEvenWithoutAZoneAndReadsNoStatements() {
        on(FinancialAccountRepository.class, "isTrackingActive", false);
        assertEquals(TimingStatus.NOT_APPLICABLE, reader().readTiming(null).status());
        on(FinancialAccountRepository.class, "isTrackingActive", true);
        Timing zoneless = reader().readTiming(null);
        assertEquals(TimingStatus.UNAVAILABLE, zoneless.status());
        assertEquals(TimingReasonCode.ZONE_UNAVAILABLE, zoneless.reasons().getFirst().code());
    }

    /* ---------------------------------------------------------------------
     * Undated Debts
     * ------------------------------------------------------------------ */

    @Test
    void undatedDebtsKeepBothDirectionsAsSeparateRows() {
        on(DebtRepository.class, "findAll", List.of(debt(2, "EGRESS", "ACTIVE", "500.00"),
            debt(1, "INGRESS", "ACTIVE", "300.00"), debt(3, "INGRESS", "PAID", "40.00"),
            debt(4, "INGRESS", "ACTIVE", "10.00")));
        on(DebtPaymentRepository.class, "sumByDebtIds", Map.of(4L, new BigDecimal("10.00")));
        on(ContactRepository.class, "findAll", List.of());
        List<UndatedDebt> debts = reader().readUndatedDebts();
        assertEquals(List.of(1L, 2L), debts.stream().map(UndatedDebt::debtId).toList());
        assertEquals(List.of("INGRESS", "EGRESS"), debts.stream().map(UndatedDebt::direction).toList());
    }

    @Test
    void aFailedDebtReadIsUnavailableNotEmpty() {
        throwing(DebtRepository.class, "findAll");
        assertNull(reader().readUndatedDebts());
        assertEquals(List.of("begin", "discard"), scope);
    }

    /* ------------------------------------------------------------------ */

    private static Box box(long id, String balance) {
        return new Box(id, "Box " + id, 220, null, null, 0, new BigDecimal(balance), false, null, null, 0);
    }

    private static FinancialAccount account(long id, String kind, String balance) {
        return new FinancialAccount(id, "Account " + id, kind, 220, BigDecimal.ZERO, TODAY,
            new BigDecimal(balance), false, null, null, 0);
    }

    private static Debt debt(long id, String direction, String status, String total) {
        return new Debt(id, null, direction, "Debt " + id, new BigDecimal(total), status, null);
    }

    private static CreditStatement statement(long id, LocalDate due, String official, String paid) {
        LocalDate end = due.minusDays(15);
        return new CreditStatement(id, 10L, end.minusDays(29), end, due, BigDecimal.ZERO,
            new BigDecimal(official), BigDecimal.ZERO, new BigDecimal(official), null, null, new BigDecimal(paid));
    }
}
