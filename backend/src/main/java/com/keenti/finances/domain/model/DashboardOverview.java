package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The composed dashboard read model (Phase 4).
 *
 * <p>Every figure here is copied from an existing domain service or repository.
 * Nothing in this model introduces an accounting formula: Net Balance, In Boxes
 * and Available to Spend keep the definitions ADR-0022 and {@code CONTEXT.md}
 * give them, plan figures come from the authoritative Box Plan contract, and a
 * confirmed Credit Statement's {@code outstandingBalance} stays a separate
 * obligation that is never subtracted from Net Balance a second time.
 *
 * <p>Each section carries its own availability. One failing read leaves the
 * others intact, and an unavailable section carries no data at all — never a
 * zero, an empty list, or any other value that would read as a fact about the
 * User's money.
 */
public record DashboardOverview(
    /** The User's calendar day, or {@code null} when their zone is unusable. */
    LocalDate today,
    String timeZone,
    Section<CurrentPosition> position,
    Section<NeedsAttention> attention,
    Section<PlanGuidance> plans,
    Section<ExpectedMoney> expected,
    Section<YearHistory> history
) {

    /** One independently available section. */
    public record Section<T>(String status, String reason, T data) {
        public static <T> Section<T> ok(T data) {
            return new Section<>("ok", null, data);
        }

        /** No data travels with a failure: a stand-in zero would be a claim. */
        public static <T> Section<T> unavailable(String reason) {
            return new Section<>("unavailable", reason, null);
        }
    }

    /**
     * Section 1 — the signed current position.
     *
     * <p>{@code moneyHeld}, {@code creditDebt} and {@code creditInFavor} are
     * {@code null} while Financial Account tracking is off. Before activation
     * there are no signed account balances to split, and decision D1 forbids
     * guessing a breakdown for the pre-activation Net Balance, which is recorded
     * income minus expenses rather than a sum of account balances.
     *
     * <p>{@code availableCredit} is limit-derived capacity. It is {@code null}
     * when no Credit Financial Account has a configured limit, and it never
     * enters money held, Net Balance, In Boxes, or Available to Spend.
     *
     * <p>{@code creditLimitsPartial} says a card's credit settings could not be
     * read. Capacity is optional context, so its failure withholds the capacity
     * figure alone: blanking the whole section would hide balances that were
     * read successfully, and those are what the User came for.
     */
    public record CurrentPosition(
        boolean trackingActive,
        boolean setupRequired,
        /** {@code accounts} or {@code transactions} — which formula produced netBalance. */
        String netBalanceSource,
        BigDecimal moneyHeld,
        /** Credit owed, as a positive magnitude. */
        BigDecimal creditDebt,
        BigDecimal creditInFavor,
        BigDecimal netBalance,
        BigDecimal inBoxes,
        BigDecimal availableToSpend,
        BigDecimal availableCredit,
        boolean creditLimitsPartial,
        List<PositionAccount> accounts
    ) {}

    /** One Financial Account, for the position drill-down. */
    public record PositionAccount(
        Long id,
        String name,
        String kind,
        BigDecimal balance,
        /** {@code null} when the Credit Financial Account has no configured limit. */
        BigDecimal creditLimit,
        BigDecimal availableCredit
    ) {}

    /**
     * Section 2 — facts that may need attention, not derived statuses.
     *
     * <p>Whether a confirmed statement is past due, due today or upcoming
     * depends on the User's calendar day, and whether billing generation is
     * pending depends on the same day. Those labels are derived once, where the
     * captured instant and zone are known (decision D2), so this section ships
     * the dates and amounts rather than a pre-rendered badge.
     *
     * <p>{@code partial} means at least one credit card's statements could not
     * be read. It is not "no obligations": the list cannot promise completeness.
     *
     * <p>Both lists are capped. {@code statementsTruncated} and
     * {@code billingTruncated} say so, and either one makes the absence of an
     * alert unprovable for the same reason {@code partial} does.
     */
    public record NeedsAttention(
        List<StatementObligation> statements,
        boolean statementsTruncated,
        List<BillingCursor> billing,
        boolean billingTruncated,
        boolean partial
    ) {}

    /** A confirmed Credit Statement that is still owed or flagged for review. */
    public record StatementObligation(
        Long accountId,
        String accountName,
        Long statementId,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate dueDate,
        BigDecimal officialBalance,
        BigDecimal paidAmount,
        /** officialBalance minus allocated payments — the figure D2 uses. */
        BigDecimal outstandingBalance,
        BigDecimal officialMinimumPayment,
        BigDecimal officialAvoidInterest,
        boolean reconciliationMismatch,
        BigDecimal mismatchAmount
    ) {}

    /**
     * A Subscription's billing-generation cursor.
     *
     * <p>ADR-0019: this is the next period Keenti still has to write Payment
     * Records for — not the last one it wrote. It is not evidence that a
     * provider charged anything or that a payment is late, and reading it never
     * advances it.
     */
    public record BillingCursor(
        Long subscriptionId,
        String name,
        String subscriptionType,
        LocalDate nextBillingDate
    ) {}

    /**
     * Section 3 — active Box Plan guidance (ADR-0021).
     *
     * <p>Suggestions are guidance. Nothing here allocates money, and the
     * presence of a suggested top-up never implies one will be applied.
     */
    public record PlanGuidance(
        BigDecimal inBoxes,
        /** Box balances held in Boxes that have an active plan. */
        BigDecimal reservedInPlannedBoxes,
        List<BoxPlanGuidance> items,
        int boxesWithoutActivePlan,
        boolean partial
    ) {}

    /** One active Saving Goal or Spending Budget, with its authoritative fields. */
    public record BoxPlanGuidance(
        Long boxId,
        String boxName,
        BigDecimal boxBalance,
        Long planId,
        String type,
        String status,
        BigDecimal targetAmount,
        LocalDate targetDate,
        BigDecimal remainingAmount,
        BigDecimal progressPercent,
        /** Saving Goal: the regular commitment plus any arrears for this period. */
        BigDecimal currentCommitment,
        BigDecimal arrears,
        /**
         * Saving Goal: min(currentCommitment, remainingAmount) — the same figure
         * {@code BoxPlanSuggestionService} suggests everywhere else. A Goal with
         * 200.00 left does not need a 500.00 commitment contributed to it.
         */
        BigDecimal suggestedContribution,
        BigDecimal desiredBalance,
        /** Spending Budget: max(desiredBalance − Box balance, 0). */
        BigDecimal suggestedTopUp
    ) {}

    /**
     * Section 4 — money recorded as owed to the User.
     *
     * <p>Explicitly not received and never added to available money. Only
     * `ACTIVE` Debts with something still outstanding and `PENDING` Payment
     * Records belonging to a Subscription Member appear. A Personal
     * Subscription's own record ({@code memberId = null}) is the Owner's own
     * charge, not money another person owes, so it is excluded (decision D2).
     *
     * <p>{@code contributionsAvailable} is {@code false} when the contribution
     * read failed. {@code contributionsOutstanding} is then {@code null} and the
     * count is meaningless: a zero there would say nobody owes this User
     * anything, which is precisely what the failed read could not establish.
     */
    public record ExpectedMoney(
        BigDecimal debtsOutstanding,
        int debtCount,
        boolean debtsTruncated,
        List<ExpectedDebt> debts,
        boolean contributionsAvailable,
        /** {@code null} when {@code contributionsAvailable} is false. */
        BigDecimal contributionsOutstanding,
        int contributionCount,
        boolean contributionsTruncated,
        List<ExpectedContribution> contributions,
        boolean partial
    ) {}

    public record ExpectedDebt(
        Long debtId,
        String description,
        Long contactId,
        String contactName,
        BigDecimal totalAmount,
        BigDecimal totalPaid,
        BigDecimal remaining
    ) {}

    public record ExpectedContribution(
        Long paymentRecordId,
        Long subscriptionId,
        String subscriptionName,
        Long memberId,
        Long contactId,
        String contactName,
        BigDecimal amount,
        LocalDate billingDate
    ) {}

    /**
     * Section 5 — the selected year's recorded history.
     *
     * <p>Scoped entirely to {@code year}. Changing the year changes nothing in
     * the current-position section, which is all-time by definition.
     */
    public record YearHistory(
        int year,
        BigDecimal totalIngress,
        BigDecimal totalEgress,
        List<MonthSummary> monthly
    ) {}
}
