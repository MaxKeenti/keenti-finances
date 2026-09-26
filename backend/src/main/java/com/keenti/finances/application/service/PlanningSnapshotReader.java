package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.Contact;
import com.keenti.finances.domain.model.CreditStatement;
import com.keenti.finances.domain.model.CreditStatementEstimate;
import com.keenti.finances.domain.model.Debt;
import com.keenti.finances.domain.model.FinancialAccount;
import com.keenti.finances.domain.model.PaymentRecordReceiptCandidate;
import com.keenti.finances.domain.model.PlanningPreview.Baseline;
import com.keenti.finances.domain.model.PlanningPreview.BoxBalance;
import com.keenti.finances.domain.model.PlanningPreview.Eligibility;
import com.keenti.finances.domain.model.PlanningPreview.ProjectionSnapshot;
import com.keenti.finances.domain.model.PlanningPreview.ReceiptKey;
import com.keenti.finances.domain.model.PlanningPreview.Resolution;
import com.keenti.finances.domain.model.PlanningPreview.StatementDue;
import com.keenti.finances.domain.model.PlanningPreview.StatementEstimate;
import com.keenti.finances.domain.model.PlanningPreview.Timing;
import com.keenti.finances.domain.model.PlanningPreview.TimingReason;
import com.keenti.finances.domain.model.PlanningPreview.TimingReasonCode;
import com.keenti.finances.domain.model.PlanningPreview.TimingStatus;
import com.keenti.finances.domain.model.PlanningPreview.UndatedDebt;
import com.keenti.finances.domain.model.PlanningPreviewCalculator;
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
import com.keenti.finances.infrastructure.adapter.in.rest.UserScoped;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

/**
 * The planning preview's three independent reads.
 *
 * <p>Each public method is its own {@code REQUIRES_NEW} transaction opened by
 * {@link ConsistentReadScope} as REPEATABLE READ, READ ONLY, and always rolled
 * back. One snapshot per section keeps every figure inside a section mutually
 * consistent — the Net Balance, In Boxes, each Box and each selected receipt
 * come from the same instant. Sections are deliberately <em>not</em> sharing a
 * transaction: after any failed statement PostgreSQL aborts the whole
 * transaction, so a statement-read failure sharing the baseline's transaction
 * would take the projection down with it. A section's reads stop at its first
 * failure and report the section unavailable; nothing is read after an error
 * inside the same transaction.
 *
 * <p>Nothing here writes. The database refuses writes in a READ ONLY
 * transaction, and the rollback means none could be kept anyway.
 */
@ApplicationScoped
@UserScoped
public class PlanningSnapshotReader {

    private static final Logger LOG = Logger.getLogger(PlanningSnapshotReader.class);

    @Inject ConsistentReadScope readScope;
    @Inject FinancialAccountRepository financialAccountRepository;
    @Inject TransactionRepository transactionRepository;
    @Inject BoxRepository boxRepository;
    @Inject DebtRepository debtRepository;
    @Inject DebtPaymentRepository debtPaymentRepository;
    @Inject PaymentRecordRepository paymentRecordRepository;
    @Inject ContactRepository contactRepository;
    @Inject CreditStatementUseCase creditStatementUseCase;
    @Inject CreditAccountSettingsRepository creditAccountSettingsRepository;

    /* ---------------------------------------------------------------------
     * Projection: baseline and selected receipts, one snapshot
     * ------------------------------------------------------------------ */

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public ProjectionSnapshot readProjection(Collection<ReceiptKey> receipts) {
        try {
            Baseline baseline;
            try {
                readScope.begin();
                baseline = baseline();
            } catch (RuntimeException e) {
                LOG.errorf(e, "planning.preview baseline unavailable");
                return ProjectionSnapshot.UNREADABLE;
            }
            try {
                return new ProjectionSnapshot(baseline, resolve(receipts), true);
            } catch (RuntimeException e) {
                LOG.errorf(e, "planning.preview receipts unavailable");
                return new ProjectionSnapshot(baseline, Map.of(), false);
            }
        } finally {
            readScope.discard();
        }
    }

    private Baseline baseline() {
        // The dashboard's branch (D5): tracking active sums non-archived Account
        // balances; otherwise all Transactions. Neither filters by date, so
        // recorded future-dated Transactions and full MSI purchases are already in.
        boolean trackingActive = financialAccountRepository.isTrackingActive();
        BigDecimal netBalance = trackingActive
            ? financialAccountRepository.getTotalBalance()
            : transactionRepository.getNetBalance();
        // Every active Box, with or without a plan, from the authoritative Box
        // read; the total is the dashboard's own In Boxes query, so the calculator
        // compares two independent reads of the same snapshot.
        List<BoxBalance> boxes = boxRepository.findAll(false).stream()
            .map(box -> new BoxBalance(box.getId(), box.getName(), box.getBalance()))
            .toList();
        BigDecimal inBoxes = boxRepository.getTotalBalance();
        BigDecimal creditInFavor = null;
        if (trackingActive) {
            creditInFavor = financialAccountRepository.findAll(false).stream()
                .filter(FinancialAccount::isCredit)
                .map(FinancialAccount::getBalance)
                .filter(balance -> balance.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return new Baseline(trackingActive, netBalance, inBoxes, boxes, creditInFavor);
    }

    private Map<ReceiptKey, Resolution> resolve(Collection<ReceiptKey> receipts) {
        Map<ReceiptKey, Resolution> resolved = new HashMap<>();
        Set<Long> debtIds = ids(receipts, ReceiptKind.DEBT);
        Set<Long> recordIds = ids(receipts, ReceiptKind.PAYMENT_RECORD);

        if (!debtIds.isEmpty()) {
            // The user-scoped, trash-filtered list, never a bare find by ID: an ID
            // outside it is unknown whether it is another User's or deleted.
            Map<Long, Debt> owned = debtRepository.findAll().stream()
                .filter(debt -> debtIds.contains(debt.getId()))
                .collect(Collectors.toMap(Debt::getId, Function.identity()));
            Map<Long, BigDecimal> paid = owned.isEmpty() ? Map.of()
                : debtPaymentRepository.sumByDebtIds(List.copyOf(owned.keySet()));
            for (Long id : debtIds) {
                Debt debt = owned.get(id);
                Resolution resolution;
                if (debt == null) {
                    resolution = new Resolution(Eligibility.NOT_FOUND, null);
                } else {
                    BigDecimal remaining = debt.getTotalAmount()
                        .subtract(paid.getOrDefault(id, BigDecimal.ZERO));
                    // The calculator's own money bound: a remaining amount the
                    // preview cannot carry is ineligible, not a User input error.
                    boolean eligible = "ACTIVE".equals(debt.getStatus())
                        && "INGRESS".equals(debt.getDirection())
                        && PlanningPreviewCalculator.validAmount(remaining);
                    resolution = eligible ? new Resolution(Eligibility.ELIGIBLE, remaining)
                        : new Resolution(Eligibility.INELIGIBLE, null);
                }
                resolved.put(new ReceiptKey(ReceiptKind.DEBT, id), resolution);
            }
        }

        if (!recordIds.isEmpty()) {
            Map<Long, PaymentRecordReceiptCandidate> owned = paymentRecordRepository
                .findReceiptCandidates(recordIds).stream()
                .collect(Collectors.toMap(PaymentRecordReceiptCandidate::id, Function.identity()));
            for (Long id : recordIds) {
                PaymentRecordReceiptCandidate candidate = owned.get(id);
                // PAID is ineligible with or without a linked Transaction.
                Resolution resolution = candidate == null
                    ? new Resolution(Eligibility.NOT_FOUND, null)
                    : candidate.eligible()
                        ? new Resolution(Eligibility.ELIGIBLE, candidate.amount())
                        : new Resolution(Eligibility.INELIGIBLE, null);
                resolved.put(new ReceiptKey(ReceiptKind.PAYMENT_RECORD, id), resolution);
            }
        }
        return resolved;
    }

    private static Set<Long> ids(Collection<ReceiptKey> receipts, ReceiptKind kind) {
        return receipts.stream().filter(key -> key.kind() == kind)
            .map(ReceiptKey::id).collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    /* ---------------------------------------------------------------------
     * Timing: every confirmed Credit Statement, one snapshot
     * ------------------------------------------------------------------ */

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Timing readTiming(Window window) {
        try {
            readScope.begin();
            // Checked before the zone: without tracking there are no statements to
            // be missing, whatever the calendar.
            if (!financialAccountRepository.isTrackingActive()) {
                return Timing.withoutData(TimingStatus.NOT_APPLICABLE, TimingReasonCode.TRACKING_INACTIVE);
            }
            if (window == null) {
                return Timing.withoutData(TimingStatus.UNAVAILABLE, TimingReasonCode.ZONE_UNAVAILABLE);
            }
            return timing(window);
        } catch (RuntimeException e) {
            // Unavailable, never an empty list: a failed or incomplete read cannot
            // say the User owes nothing.
            LOG.errorf(e, "planning.preview timing unavailable");
            return Timing.withoutData(TimingStatus.UNAVAILABLE, TimingReasonCode.READ_FAILED);
        } finally {
            readScope.discard();
        }
    }

    private Timing timing(Window window) {
        List<StatementDue> dated = new ArrayList<>();
        List<StatementDue> overdue = new ArrayList<>();
        List<StatementEstimate> estimates = new ArrayList<>();
        List<TimingReason> reasons = new ArrayList<>();

        // Same account scope as the baseline: archived accounts are excluded.
        for (FinancialAccount account : financialAccountRepository.findAll(false)) {
            if (!account.isCredit()) continue;
            // The full, uncapped statement list for the account — not the
            // dashboard's 50-item attention section.
            List<CreditStatement> statements = creditStatementUseCase.list(account.getId());
            for (CreditStatement statement : statements) {
                BigDecimal paid = statement.paidAmount() == null ? BigDecimal.ZERO : statement.paidAmount();
                BigDecimal outstanding = statement.officialBalance().subtract(paid);
                boolean mismatch = creditStatementUseCase
                    .estimateOutstandingBalance(account.getId(), statement.periodEnd())
                    .compareTo(statement.estimatedBalance()) != 0;
                if (mismatch) {
                    reasons.add(new TimingReason(TimingReasonCode.RECONCILIATION_MISMATCH,
                        account.getId(), statement.id()));
                }
                if (outstanding.signum() <= 0) continue;
                if (statement.dueDate() == null) {
                    reasons.add(new TimingReason(TimingReasonCode.STATEMENT_DUE_DATE_MISSING,
                        account.getId(), statement.id()));
                    continue;
                }
                StatementDue row = new StatementDue(account.getId(), account.getName(), statement.id(),
                    statement.periodStart(), statement.periodEnd(), statement.dueDate(),
                    statement.officialBalance(), paid, outstanding,
                    statement.officialMinimumPayment(), statement.officialAvoidInterest(), mismatch);
                if (statement.dueDate().isBefore(window.from())) {
                    overdue.add(row);
                } else if (!statement.dueDate().isAfter(window.to())) {
                    dated.add(row);
                }
            }
            unconfirmedPeriods(account, statements, window, estimates, reasons);
        }

        Comparator<StatementDue> order = Comparator.comparing(StatementDue::dueDate)
            .thenComparingLong(StatementDue::accountId).thenComparingLong(StatementDue::statementId);
        dated.sort(order);
        overdue.sort(order);
        return new Timing(reasons.isEmpty() ? TimingStatus.COMPLETE : TimingStatus.PARTIAL,
            dated, overdue, estimates, reasons);
    }

    /**
     * The open period and the last closed one, from the continuous estimate
     * (which includes MSI installments). An unconfirmed period with an estimated
     * balance is context; one due inside the window makes timing partial, because
     * a confirmed-only list would otherwise look complete while missing it.
     */
    private void unconfirmedPeriods(FinancialAccount account, List<CreditStatement> statements,
                                    Window window, List<StatementEstimate> estimates,
                                    List<TimingReason> reasons) {
        if (creditAccountSettingsRepository.findByAccountId(account.getId()).isEmpty()) {
            reasons.add(new TimingReason(TimingReasonCode.STATEMENT_SCHEDULE_MISSING, account.getId(), null));
            return;
        }
        CreditStatementEstimate current = creditStatementUseCase.estimateCurrentStatement(account.getId(), window.from());
        CreditStatementEstimate previous = creditStatementUseCase.estimateCurrentStatement(
            account.getId(), current.periodStart().minusDays(1));
        for (CreditStatementEstimate estimate : List.of(previous, current)) {
            boolean confirmed = statements.stream().anyMatch(statement ->
                !statement.periodStart().isAfter(estimate.periodEnd())
                    && !statement.periodEnd().isBefore(estimate.periodEnd()));
            if (confirmed || estimate.estimatedBalance().signum() <= 0) continue;
            estimates.add(new StatementEstimate(account.getId(), account.getName(),
                estimate.periodStart(), estimate.periodEnd(), estimate.dueDate(),
                estimate.estimatedBalance()));
            if (!estimate.dueDate().isAfter(window.to())) {
                reasons.add(new TimingReason(TimingReasonCode.UNCONFIRMED_STATEMENT, account.getId(), null));
            }
        }
    }

    /* ---------------------------------------------------------------------
     * Undated Debts: both Directions, one snapshot
     * ------------------------------------------------------------------ */

    /** Null when unreadable; an empty list means no outstanding Debt. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public List<UndatedDebt> readUndatedDebts() {
        try {
            readScope.begin();
            List<Debt> active = debtRepository.findAll().stream()
                .filter(debt -> "ACTIVE".equals(debt.getStatus())).toList();
            if (active.isEmpty()) return List.of();
            Map<Long, BigDecimal> paid = debtPaymentRepository.sumByDebtIds(
                active.stream().map(Debt::getId).toList());
            Map<Long, String> names = new HashMap<>();
            for (Contact contact : contactRepository.findAll()) names.put(contact.getId(), contact.getName());

            List<UndatedDebt> rows = new ArrayList<>();
            for (Debt debt : active) {
                BigDecimal paidAmount = paid.getOrDefault(debt.getId(), BigDecimal.ZERO);
                BigDecimal remaining = debt.getTotalAmount().subtract(paidAmount);
                if (remaining.signum() <= 0) continue;
                rows.add(new UndatedDebt(debt.getId(), debt.getDirection(), debt.getContactId(),
                    debt.getContactId() == null ? null : names.get(debt.getContactId()),
                    debt.getDescription(), debt.getTotalAmount(), paidAmount, remaining));
            }
            // INGRESS before EGRESS, then by ID. No totals: each Debt Counterpart
            // side stays separate and nothing is netted or deducted (ADR-0023).
            rows.sort(Comparator.comparing(UndatedDebt::direction, Comparator.reverseOrder())
                .thenComparingLong(UndatedDebt::debtId));
            return List.copyOf(rows);
        } catch (RuntimeException e) {
            LOG.errorf(e, "planning.preview undated debts unavailable");
            return null;
        } finally {
            readScope.discard();
        }
    }
}
