package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.Box;
import com.keenti.finances.domain.model.BoxPlan;
import com.keenti.finances.domain.model.Contact;
import com.keenti.finances.domain.model.CreditAccountSettings;
import com.keenti.finances.domain.model.CreditStatement;
import com.keenti.finances.domain.model.DashboardOverview;
import com.keenti.finances.domain.model.DashboardOverview.BillingCursor;
import com.keenti.finances.domain.model.DashboardOverview.BoxPlanGuidance;
import com.keenti.finances.domain.model.DashboardOverview.CurrentPosition;
import com.keenti.finances.domain.model.DashboardOverview.ExpectedContribution;
import com.keenti.finances.domain.model.DashboardOverview.ExpectedDebt;
import com.keenti.finances.domain.model.DashboardOverview.ExpectedMoney;
import com.keenti.finances.domain.model.DashboardOverview.NeedsAttention;
import com.keenti.finances.domain.model.DashboardOverview.PlanGuidance;
import com.keenti.finances.domain.model.DashboardOverview.PositionAccount;
import com.keenti.finances.domain.model.DashboardOverview.Section;
import com.keenti.finances.domain.model.DashboardOverview.StatementObligation;
import com.keenti.finances.domain.model.DashboardOverview.YearHistory;
import com.keenti.finances.domain.model.Debt;
import com.keenti.finances.domain.model.FinancialAccount;
import com.keenti.finances.domain.model.MonthSummary;
import com.keenti.finances.domain.model.PendingContribution;
import com.keenti.finances.domain.model.SavingGoalDetails;
import com.keenti.finances.domain.model.SpendingBudgetSnapshot;
import com.keenti.finances.domain.port.in.DashboardOverviewUseCase;
import com.keenti.finances.domain.port.in.CreditStatementUseCase;
import com.keenti.finances.domain.port.in.SavingGoalUseCase;
import com.keenti.finances.domain.port.in.SpendingBudgetUseCase;
import com.keenti.finances.domain.port.out.BoxPlanRepository;
import com.keenti.finances.domain.port.out.BoxRepository;
import com.keenti.finances.domain.port.out.ContactRepository;
import com.keenti.finances.domain.port.out.CreditAccountSettingsRepository;
import com.keenti.finances.domain.port.out.DebtPaymentRepository;
import com.keenti.finances.domain.port.out.DebtRepository;
import com.keenti.finances.domain.port.out.FinancialAccountRepository;
import com.keenti.finances.domain.port.out.PaymentRecordRepository;
import com.keenti.finances.domain.port.out.SubscriptionRepository;
import com.keenti.finances.domain.port.out.TransactionRepository;
import com.keenti.finances.domain.port.out.UserTimeZoneProvider;
import com.keenti.finances.infrastructure.adapter.in.rest.UserScoped;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jboss.logging.Logger;

/**
 * Composes the dashboard's five sections out of facts other services already own.
 *
 * <p>What this service deliberately does <em>not</em> do:
 *
 * <ul>
 *   <li>Invent a formula. Net Balance, In Boxes and Available to Spend come
 *       from the same repositories {@link DashboardService} uses; plan figures
 *       come from {@link SavingGoalUseCase} and {@link SpendingBudgetUseCase};
 *       a statement's outstanding amount is its official balance minus the
 *       payments already allocated to it.</li>
 *   <li>Write. No billing is generated, no generation cursor advances, no Box
 *       Movement or Transaction is created. Reading an active Box Plan performs
 *       the idempotent lazy period evaluation ADR-0021 specifies for every plan
 *       read, which records progress and moves no money.</li>
 *   <li>Fabricate a value for a failed read. Each section is computed in
 *       isolation and an exception makes that one section unavailable, with no
 *       data attached. A zero balance and an unreadable balance are different
 *       statements about the User's money.</li>
 * </ul>
 *
 * <p>User isolation is the repositories' existing {@code userScope} Hibernate
 * filter (ADR-0011/0012/0018). Every list this service walks — Financial
 * Accounts, Boxes, Subscriptions, Debts — is read through a {@code @UserScoped}
 * repository first, and the per-item reads that follow are keyed by IDs that
 * came out of those scoped lists.
 */
@ApplicationScoped
@UserScoped
public class DashboardOverviewService implements DashboardOverviewUseCase {

    private static final Logger LOG = Logger.getLogger(DashboardOverviewService.class);

    /**
     * Payload bounds. The dashboard states totals over everything and lists only
     * the first page of items, so a User with hundreds of open Debts gets a
     * correct total and a bounded response rather than either a truncated total
     * or an unbounded one.
     */
    private static final int MAX_STATEMENTS = 50;
    private static final int MAX_EXPECTED_ITEMS = 25;
    private static final int MAX_BILLING_CURSORS = 25;

    @Inject
    FinancialAccountRepository financialAccountRepository;

    @Inject
    BoxRepository boxRepository;

    @Inject
    BoxPlanRepository boxPlanRepository;

    @Inject
    TransactionRepository transactionRepository;

    @Inject
    CreditAccountSettingsRepository creditAccountSettingsRepository;

    @Inject
    CreditStatementUseCase creditStatementUseCase;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    PaymentRecordRepository paymentRecordRepository;

    @Inject
    DebtRepository debtRepository;

    @Inject
    DebtPaymentRepository debtPaymentRepository;

    @Inject
    ContactRepository contactRepository;

    @Inject
    SavingGoalUseCase savingGoalUseCase;

    @Inject
    SpendingBudgetUseCase spendingBudgetUseCase;

    @Inject
    UserTimeZoneProvider userTimeZoneProvider;

    @Override
    public DashboardOverview getOverview(Integer year) {
        ZoneId zone = null;
        LocalDate today = null;
        try {
            zone = userTimeZoneProvider.getTimeZone();
            today = userTimeZoneProvider.today();
        } catch (RuntimeException e) {
            // An unusable zone is reported as an absent day. It is never replaced
            // with UTC or the server's zone, either of which answers with a
            // different calendar day and so a different "past due" (decision D2).
            LOG.warnf("dashboard.overview zone unavailable: %s", e.toString());
        }

        Integer historyYear = year != null ? year : today == null ? null : today.getYear();
        LOG.infof("dashboard.overview year=%s today=%s", historyYear, today);

        return new DashboardOverview(
            today,
            zone == null ? null : zone.getId(),
            section("position", this::currentPosition),
            section("attention", this::needsAttention),
            section("plans", this::planGuidance),
            section("expected", this::expectedMoney),
            historyYear == null ? Section.unavailable("date")
                : section("history", () -> yearHistory(historyYear)));
    }

    /** Runs one section, turning its failure into an unavailable section only. */
    private <T> Section<T> section(String name, SectionSupplier<T> supplier) {
        try {
            return Section.ok(supplier.get());
        } catch (RuntimeException e) {
            LOG.errorf(e, "dashboard.overview section=%s unavailable", name);
            return Section.unavailable("error");
        }
    }

    @FunctionalInterface
    private interface SectionSupplier<T> {
        T get();
    }

    /* ---------------------------------------------------------------------
     * 1 — Current position
     * ------------------------------------------------------------------ */

    private CurrentPosition currentPosition() {
        boolean trackingActive = financialAccountRepository.isTrackingActive();
        boolean setupRequired = financialAccountRepository.isTrackingSetupRequired();
        List<FinancialAccount> accounts = financialAccountRepository.findAll(false);

        BigDecimal netBalance = trackingActive
            ? financialAccountRepository.getTotalBalance()
            : transactionRepository.getNetBalance();
        BigDecimal inBoxes = boxRepository.getTotalBalance();
        BigDecimal availableToSpend = netBalance.subtract(inBoxes);

        BigDecimal moneyHeld = BigDecimal.ZERO;
        BigDecimal creditDebt = BigDecimal.ZERO;
        BigDecimal creditInFavor = BigDecimal.ZERO;
        BigDecimal availableCredit = null;
        boolean creditLimitsPartial = false;
        List<PositionAccount> rows = new ArrayList<>();

        for (FinancialAccount account : accounts) {
            BigDecimal balance = account.getBalance();
            BigDecimal limit = null;
            BigDecimal accountCapacity = null;

            if (account.isCredit()) {
                if (balance.signum() < 0) {
                    creditDebt = creditDebt.add(balance.negate());
                } else {
                    creditInFavor = creditInFavor.add(balance);
                }
                try {
                    limit = creditAccountSettingsRepository.findByAccountId(account.getId())
                        .map(CreditAccountSettings::creditLimit)
                        .orElse(null);
                } catch (RuntimeException e) {
                    // Capacity is optional context beside the balances. Losing it
                    // must not blank a Net Balance, an In Boxes and an Available
                    // to Spend that were all read successfully.
                    LOG.warnf(e, "dashboard.overview credit settings unavailable account=%d",
                        account.getId());
                    creditLimitsPartial = true;
                }
                if (limit != null) {
                    // Limit-derived capacity, floored at zero: a card over its
                    // limit has no capacity left, not a negative amount of it.
                    accountCapacity = limit.add(balance).max(BigDecimal.ZERO);
                    availableCredit = availableCredit == null
                        ? accountCapacity
                        : availableCredit.add(accountCapacity);
                }
            } else {
                moneyHeld = moneyHeld.add(balance);
            }

            rows.add(new PositionAccount(account.getId(), account.getName(), account.getKind(),
                balance, limit, accountCapacity));
        }

        return new CurrentPosition(
            trackingActive,
            setupRequired,
            trackingActive ? "accounts" : "transactions",
            // Before activation there are no signed account balances to split,
            // and decision D1 forbids guessing a breakdown of a Net Balance that
            // is recorded income minus expenses.
            trackingActive ? moneyHeld : null,
            trackingActive ? creditDebt : null,
            trackingActive ? creditInFavor : null,
            netBalance,
            inBoxes,
            availableToSpend,
            trackingActive && !creditLimitsPartial ? availableCredit : null,
            creditLimitsPartial,
            List.copyOf(rows));
    }

    /* ---------------------------------------------------------------------
     * 2 — Needs attention
     * ------------------------------------------------------------------ */

    private NeedsAttention needsAttention() {
        List<StatementObligation> statements = new ArrayList<>();
        boolean partial = false;

        for (FinancialAccount account : financialAccountRepository.findAll(false)) {
            if (!account.isCredit()) continue;
            try {
                for (CreditStatement statement : creditStatementUseCase.list(account.getId())) {
                    StatementObligation row = toObligation(account, statement);
                    // A covered statement with nothing flagged needs no attention.
                    // It is omitted rather than shown as a settled obligation.
                    if (row.outstandingBalance().signum() > 0 || row.reconciliationMismatch()) {
                        statements.add(row);
                    }
                }
            } catch (RuntimeException e) {
                // One unreadable card leaves the others listed; the section says
                // it may be incomplete instead of implying this card owes nothing.
                LOG.warnf(e, "dashboard.overview statements unavailable account=%d", account.getId());
                partial = true;
            }
        }

        // Money still owed comes before a review notice, and only then by due
        // date. Sorting on the date alone lets an old, fully-paid statement that
        // happens to be flagged for review push an unpaid one past the cut.
        statements.sort(Comparator
            .comparing((StatementObligation row) -> row.outstandingBalance().signum() > 0 ? 0 : 1)
            .thenComparing(StatementObligation::dueDate,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StatementObligation::statementId));
        boolean truncated = statements.size() > MAX_STATEMENTS;

        // The cursor travels raw. Deriving pending/due-today/caught-up needs the
        // User's calendar day, which decision D2 resolves once per render.
        List<BillingCursor> billing = new ArrayList<>(subscriptionRepository.findAll().stream()
            .map(subscription -> new BillingCursor(subscription.getId(), subscription.getName(),
                subscription.getType(), subscription.getNextBillingDate()))
            .toList());

        // Which cursors are behind is only knowable with the User's calendar
        // day, so the cap cannot keep "the ones needing attention". Ordering by
        // the cursor date instead keeps the furthest-behind — and an unreadable
        // cursor ahead of every readable one, since it is the one case that
        // must never be silently dropped.
        billing.sort(Comparator
            .comparing(BillingCursor::nextBillingDate, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(BillingCursor::subscriptionId));
        boolean billingTruncated = billing.size() > MAX_BILLING_CURSORS;

        return new NeedsAttention(
            List.copyOf(truncated ? statements.subList(0, MAX_STATEMENTS) : statements),
            truncated,
            List.copyOf(billingTruncated ? billing.subList(0, MAX_BILLING_CURSORS) : billing),
            billingTruncated,
            partial);
    }

    private StatementObligation toObligation(FinancialAccount account, CreditStatement statement) {
        BigDecimal paid = statement.paidAmount() == null ? BigDecimal.ZERO : statement.paidAmount();
        BigDecimal outstanding = statement.officialBalance().subtract(paid);
        // Same comparison the Credit Financial Account page makes: what the
        // recorded ledger now says for the period versus the estimate captured
        // when the bank's snapshot was confirmed. It is a review notice, never a
        // payment state and never a claim that either figure is wrong.
        BigDecimal mismatch = creditStatementUseCase
            .estimateOutstandingBalance(account.getId(), statement.periodEnd())
            .subtract(statement.estimatedBalance());

        return new StatementObligation(account.getId(), account.getName(), statement.id(),
            statement.periodStart(), statement.periodEnd(), statement.dueDate(),
            statement.officialBalance(), paid, outstanding,
            statement.officialMinimumPayment(), statement.officialAvoidInterest(),
            mismatch.signum() != 0, mismatch);
    }

    /* ---------------------------------------------------------------------
     * 3 — Your plans
     * ------------------------------------------------------------------ */

    private PlanGuidance planGuidance() {
        List<Box> boxes = boxRepository.findAll(false);
        List<BoxPlanGuidance> items = new ArrayList<>();
        BigDecimal reserved = BigDecimal.ZERO;
        int withoutPlan = 0;
        boolean partial = false;

        for (Box box : boxes) {
            Optional<BoxPlan> active;
            try {
                active = boxPlanRepository.findActiveByBoxId(box.getId());
            } catch (RuntimeException e) {
                // A Box whose plan could not be read is not an unplanned Box:
                // "no plan" invites creating one, which is the wrong offer here.
                LOG.warnf(e, "dashboard.overview plan unavailable box=%d", box.getId());
                partial = true;
                continue;
            }
            if (active.isEmpty()) {
                withoutPlan++;
                continue;
            }

            try {
                items.add(describe(box, active.get()));
                reserved = reserved.add(box.getBalance());
            } catch (RuntimeException e) {
                LOG.warnf(e, "dashboard.overview plan detail unavailable box=%d", box.getId());
                partial = true;
            }
        }

        return new PlanGuidance(boxRepository.getTotalBalance(), reserved, List.copyOf(items),
            withoutPlan, partial);
    }

    /**
     * Copies one active plan's authoritative figures.
     *
     * <p>Nothing is recomputed here. A Saving Goal's remaining amount, progress
     * and current commitment, and a Spending Budget's suggested top-up, are the
     * values the plan services publish; reproducing their arithmetic on the
     * dashboard is how two screens start disagreeing about the same plan.
     *
     * <p>The status comes from the plan the detail read returned, not from the
     * {@code plan} that located it: reading an active plan performs the lazy
     * period evaluation of ADR-0021, which can close or complete it, and the
     * earlier copy is stale the moment it does.
     */
    private BoxPlanGuidance describe(Box box, BoxPlan plan) {
        if (plan.type() == BoxPlan.Type.SAVING_GOAL) {
            SavingGoalDetails details = savingGoalUseCase.getActive(box.getId()).orElseThrow();
            BoxPlan evaluated = details.plan();
            // The same suggestion BoxPlanSuggestionService makes: a Goal with
            // 200.00 left is not asking for a 500.00 commitment.
            BigDecimal suggested = details.currentCommitment() == null
                    || details.remainingAmount() == null
                ? null
                : details.currentCommitment().min(details.remainingAmount());
            return new BoxPlanGuidance(box.getId(), box.getName(), details.boxBalance(),
                evaluated.id(), evaluated.type().name(), evaluated.status().name(),
                details.currentRevision().targetAmount(), details.currentRevision().targetDate(),
                details.remainingAmount(), details.progressPercent(),
                details.currentCommitment(), details.arrears(), suggested, null, null);
        }

        SpendingBudgetSnapshot snapshot = spendingBudgetUseCase.getActive(box.getId());
        BoxPlan evaluated = snapshot.plan();
        return new BoxPlanGuidance(box.getId(), box.getName(), snapshot.currentBalance(),
            evaluated.id(), evaluated.type().name(), evaluated.status().name(),
            null, null, null, null, null, null, null,
            snapshot.revision().desiredBalance(), snapshot.suggestedTopUp());
    }

    /* ---------------------------------------------------------------------
     * 4 — Money expected
     * ------------------------------------------------------------------ */

    private ExpectedMoney expectedMoney() {
        boolean partial = false;

        List<Debt> active = debtRepository.findAll().stream()
            .filter(debt -> "ACTIVE".equals(debt.getStatus())
                && "INGRESS".equals(debt.getDirection()))
            .toList();
        Map<Long, BigDecimal> paidByDebt =
            debtPaymentRepository.sumByDebtIds(active.stream().map(Debt::getId).toList());
        Map<Long, String> contactNames = new HashMap<>();
        for (Contact contact : contactRepository.findAll()) {
            contactNames.put(contact.getId(), contact.getName());
        }

        List<ExpectedDebt> debts = new ArrayList<>();
        BigDecimal debtsOutstanding = BigDecimal.ZERO;
        for (Debt debt : active) {
            BigDecimal paid = paidByDebt.getOrDefault(debt.getId(), BigDecimal.ZERO);
            BigDecimal remaining = debt.getTotalAmount().subtract(paid);
            // A Debt whose payments already cover it is owed nothing, whatever
            // its stored status says.
            if (remaining.signum() <= 0) continue;
            debtsOutstanding = debtsOutstanding.add(remaining);
            debts.add(new ExpectedDebt(debt.getId(), debt.getDescription(), debt.getContactId(),
                debt.getContactId() == null ? null : contactNames.get(debt.getContactId()),
                debt.getTotalAmount(), paid, remaining));
        }
        debts.sort(Comparator.comparing(ExpectedDebt::remaining).reversed()
            .thenComparing(ExpectedDebt::debtId));

        // One user-scoped query for every pending contribution, already filtered
        // by the exclusions decision D2 states. The alternative — a Subscription
        // list, then that Subscription's whole payment history, then a scan for
        // the few PENDING rows — reads every PAID record the User has ever
        // accumulated to answer a question about the open ones.
        List<ExpectedContribution> contributions = new ArrayList<>();
        BigDecimal contributionsOutstanding = BigDecimal.ZERO;
        boolean contributionsAvailable = true;
        try {
            for (PendingContribution pending : paymentRecordRepository.findPendingContributions()) {
                contributionsOutstanding = contributionsOutstanding.add(pending.amount());
                contributions.add(new ExpectedContribution(pending.paymentRecordId(),
                    pending.subscriptionId(), pending.subscriptionName(), pending.memberId(),
                    pending.contactId(), pending.contactName(),
                    pending.amount(), pending.billingDate()));
            }
        } catch (RuntimeException e) {
            // Not zero, and not an empty list. Either would say nobody owes this
            // User anything, which is the one thing the failed read cannot say.
            LOG.warnf(e, "dashboard.overview contributions unavailable");
            contributionsAvailable = false;
            contributions.clear();
            partial = true;
        }

        boolean debtsTruncated = debts.size() > MAX_EXPECTED_ITEMS;
        boolean contributionsTruncated = contributions.size() > MAX_EXPECTED_ITEMS;

        return new ExpectedMoney(
            debtsOutstanding, debts.size(), debtsTruncated,
            List.copyOf(debtsTruncated ? debts.subList(0, MAX_EXPECTED_ITEMS) : debts),
            contributionsAvailable,
            contributionsAvailable ? contributionsOutstanding : null,
            contributions.size(), contributionsTruncated,
            List.copyOf(contributionsTruncated
                ? contributions.subList(0, MAX_EXPECTED_ITEMS)
                : contributions),
            partial);
    }

    /* ---------------------------------------------------------------------
     * 5 — History
     * ------------------------------------------------------------------ */

    private YearHistory yearHistory(int year) {
        // Read straight from the Transaction monthly summary rather than through
        // DashboardUseCase, which would also re-read the accounts and Boxes the
        // position section owns — and would make an unreadable account balance
        // take the year's recorded history down with it, which is exactly the
        // coupling independent sections exist to avoid. The annual totals are
        // the same sum DashboardService performs over the same months.
        List<MonthSummary> monthly = transactionRepository.findMonthlySummary(year);
        BigDecimal totalIngress = monthly.stream()
            .map(MonthSummary::getIngress)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEgress = monthly.stream()
            .map(MonthSummary::getEgress)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new YearHistory(year, totalIngress, totalEgress, monthly);
    }
}
