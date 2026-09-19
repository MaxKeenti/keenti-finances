/**
 * Subscription period and cost derivations (Slice 3B, decision D2).
 *
 * Four different facts live here and stay apart:
 *
 * 1. What the provider charges — the Subscription's **current** price. Keenti
 *    stores one price, so a past period's provider cost is not knowable; this
 *    module never presents the current price as a historical one.
 * 2. What the User currently expects to collect — the split of that current
 *    price over the current Subscription Members. An expectation, not a bill.
 * 3. What was actually billed, collected and is still outstanding for one
 *    period — read only from the stored Payment Records of that period, never
 *    recomputed from today's price or membership.
 * 4. The User's own share — again a *current* figure, and 0.00 in middleman
 *    mode, where the Owner forwards charges rather than splitting them.
 *
 * None of these say anything about whether the provider's own charge was paid.
 * Keenti has no subscription-linked provider-charge record: a Transaction can be linked to a
 * Subscription (ADR-0010), but that link is a pure annotation with no direction
 * or period contract. Transaction linking currently accepts only INGRESS. A `PAID`
 * member Payment Record means a contribution was received, never that the
 * provider expense exists or was settled.
 *
 * The split formula mirrors `SubscriptionService.recalculateShares` exactly —
 * `cost / (memberCount + (ownerParticipates ? 1 : 0))`, half-up to two
 * decimals — because the per-Member `shareAmount` the backend stores is what
 * billing writes into Payment Records. To mirror it it must also round like
 * it: every amount here is scaled to integer cents before any arithmetic, so
 * a share lands on the same cent `BigDecimal` chose rather than a cent below
 * it. Nothing here changes an accounting formula; it only labels the results.
 */

import { contributionStatus, type CalendarDay, type Contribution } from '$lib/obligation-status';

export type SubscriptionCost = {
	cost: unknown;
	billingCycle: unknown;
	type: unknown;
	ownerParticipates?: unknown;
};

export type MemberShare = { id: number; shareAmount: number | null };

/**
 * A money amount as an exact integer number of cents.
 *
 * Every arithmetic step below happens in cents. An amount arriving as a
 * `number` is already a decimal the backend wrote with two places, so scaling
 * it once and rounding absorbs the binary representation error in one place
 * (`20.15 * 100` is `2014.999…`); after that, addition and division are exact
 * integer operations and cannot drift.
 */
function cents(value: number): number {
	return Math.round(value * 100);
}

function fromCents(value: number): number {
	return value / 100;
}

/**
 * Divides cents the way the backend's `BigDecimal.divide(..., 2, HALF_UP)` does.
 *
 * Rounding the *quotient* of two floats is not the same operation: 20.15 split
 * two ways is 10.075, which JavaScript holds as 10.074999999999999 and rounds
 * down to 10.07, while `BigDecimal` rounds its exact 10.075 up to 10.08. The
 * per-Member `shareAmount` the backend stores is the half-up figure, so a
 * float-rounded share would disagree with the very records this page reads.
 *
 * Costs are non-negative, so half-up and half-away-from-zero coincide.
 */
function divideCentsHalfUp(total: number, divisor: number): number {
	const quotient = Math.floor(total / divisor);
	const remainder = total - quotient * divisor;
	return remainder * 2 >= divisor ? quotient + 1 : quotient;
}

/** Sums money exactly, by converting each term to cents before adding. */
function sumMoney(values: number[]): number {
	return fromCents(values.reduce((total, value) => total + cents(value), 0));
}

function money(value: unknown): number | null {
	return typeof value === 'number' && Number.isFinite(value) && value >= 0 ? value : null;
}

export type CurrentSplitState =
	/** The Subscription or its member list could not be read. */
	| 'unavailable'
	/** `PERSONAL`: nothing is split, the whole price is the User's. */
	| 'personal'
	/** `SHARED` with no Subscription Members assigned yet. */
	| 'no-members'
	/** `SHARED` with Members: the price splits. */
	| 'split';

export type CurrentSplit = {
	state: CurrentSplitState;
	/** The Subscription's current price for one billing period. Gross, not due. */
	providerCost: number | null;
	/** How many ways the current price divides, Owner included when they participate. */
	splitCount: number | null;
	/** One splitter's share of the current price. */
	shareAmount: number | null;
	/** What the current Members are collectively expected to contribute. */
	expectedContributions: number | null;
	/**
	 * Whether the expectation came from stored Member shares or the formula.
	 *
	 * `formula` is an *estimate*: at least one Member's stored `shareAmount` was
	 * missing or unusable, so the even split stands in for it. The surface says
	 * so rather than letting a computed figure pass for a stored one.
	 */
	expectedBasis: 'member-shares' | 'formula' | null;
	/** Members whose stored share could not be read, and so were estimated. */
	unreadableShares: number;
	/** The User's own current share. 0 in middleman mode. */
	ownShare: number | null;
	/**
	 * `providerCost - (expectedContributions + ownShare)`, when non-zero.
	 *
	 * Splitting 299.00 three ways gives 99.67 each, which totals 299.01. The
	 * remainder is shown rather than absorbed, so the parts visibly do not
	 * claim to reconstruct the price exactly.
	 */
	roundingRemainder: number | null;
};

const UNAVAILABLE_SPLIT: CurrentSplit = {
	state: 'unavailable',
	providerCost: null,
	splitCount: null,
	shareAmount: null,
	expectedContributions: null,
	expectedBasis: null,
	unreadableShares: 0,
	ownShare: null,
	roundingRemainder: null,
};

/**
 * The Subscription's current cost position.
 *
 * `members === null` means the member list could not be read. That is not
 * "no Members": a zero split count would understate what the User expects to
 * collect and overstate their own share, so it reports unavailable instead.
 */
export function currentSplit(input: {
	subscription: SubscriptionCost | null;
	members: MemberShare[] | null;
}): CurrentSplit {
	const subscription = input.subscription;
	if (subscription === null) return UNAVAILABLE_SPLIT;

	const providerCost = money(subscription.cost);
	if (providerCost === null) return UNAVAILABLE_SPLIT;

	if (subscription.type === 'PERSONAL') {
		return {
			state: 'personal',
			providerCost,
			splitCount: 1,
			shareAmount: providerCost,
			expectedContributions: 0,
			expectedBasis: 'formula',
			unreadableShares: 0,
			ownShare: providerCost,
			roundingRemainder: null,
		};
	}
	if (subscription.type !== 'SHARED') return { ...UNAVAILABLE_SPLIT, providerCost };
	if (input.members === null) return { ...UNAVAILABLE_SPLIT, providerCost };

	// Missing participation is not a safe default for an existing subscription.
	if (typeof subscription.ownerParticipates !== 'boolean') return { ...UNAVAILABLE_SPLIT, providerCost };
	const ownerParticipates = subscription.ownerParticipates;
	const memberCount = input.members.length;
	if (memberCount === 0) {
		return {
			state: 'no-members',
			providerCost,
			splitCount: ownerParticipates ? 1 : 0,
			shareAmount: ownerParticipates ? providerCost : null,
			expectedContributions: 0,
			expectedBasis: 'formula',
			unreadableShares: 0,
			// With no Members and no Owner participation nobody is assigned a
			// share at all; the price is real but unallocated, and `0` would
			// claim the User owes nothing for a Subscription they still pay.
			ownShare: ownerParticipates ? providerCost : null,
			roundingRemainder: null,
		};
	}

	const splitCount = memberCount + (ownerParticipates ? 1 : 0);
	const shareAmount = fromCents(divideCentsHalfUp(cents(providerCost), splitCount));

	// Stored Member shares are what billing actually writes into Payment
	// Records, so they are the expectation whenever they are readable. The even
	// split only stands in for the ones that are not, and how many were
	// substituted travels with the figure.
	const storedShares = input.members.map((member) => money(member.shareAmount));
	const unreadableShares = storedShares.filter((share) => share === null).length;
	const expectedContributions = sumMoney(storedShares.map((share) => share ?? shareAmount));
	const ownShare = ownerParticipates ? shareAmount : 0;
	const remainder = fromCents(
		cents(providerCost) - (cents(expectedContributions) + cents(ownShare)),
	);

	return {
		state: 'split',
		providerCost,
		splitCount,
		shareAmount,
		expectedContributions,
		expectedBasis: unreadableShares === 0 ? 'member-shares' : 'formula',
		unreadableShares,
		ownShare,
		roundingRemainder: remainder === 0 ? null : remainder,
	};
}

/* -------------------------------------------------------------------------
 * Stored Payment Records for one period
 * ---------------------------------------------------------------------- */

export type PaymentRecordInput = {
	id: number;
	memberId: number | null;
	billingDate: string;
	amount: number;
	status: string;
	paidDate: string | null;
	transactionId: number | null;
};

export type PeriodRecord = {
	record: PaymentRecordInput;
	status: Contribution;
};

export type PeriodSummaryState = 'unavailable' | 'no-records' | 'has-records';

export type PeriodSummary = {
	state: PeriodSummaryState;
	billingDate: CalendarDay | null;
	/** Records for Subscription Members — money the User expects to receive. */
	memberRecords: PeriodRecord[];
	/**
	 * Records with no Member.
	 *
	 * Billing writes one of these for a `PERSONAL` Subscription, carrying the
	 * price the Owner themselves owes. It is the User's own share for that
	 * period, not a contribution from anyone, so it never enters collected or
	 * outstanding.
	 */
	ownerRecords: PeriodRecord[];
	/**
	 * Sum of readable Member record amounts, whatever their status.
	 *
	 * `null` when the period has Member records but none of them could be read.
	 * A subtotal of the readable subset is a real partial figure; a subtotal of
	 * *nothing* readable is `0.00`, which would report a period as billed
	 * nothing and collected nothing purely because its records were malformed.
	 */
	billed: number | null;
	/** Sum of readable `PAID` Member record amounts. */
	collected: number | null;
	/** Sum of readable `PENDING` Member record amounts. Awaiting, never overdue. */
	outstanding: number | null;
	/** Member records whose amount or status could not be read. */
	unreadableCount: number;
	/**
	 * Whether the totals cover only part of the period's Member records.
	 *
	 * True whenever something was left out, so a surface can mark the figures
	 * as partial instead of presenting them as the period's whole story.
	 */
	partial: boolean;
	/** Own-share records whose amount could not be read. */
	unreadableOwnerCount: number;
};

const UNAVAILABLE_PERIOD: PeriodSummary = {
	state: 'unavailable',
	billingDate: null,
	memberRecords: [],
	ownerRecords: [],
	billed: null,
	collected: null,
	outstanding: null,
	unreadableCount: 0,
	partial: false,
	unreadableOwnerCount: 0,
};

/** Distinct billing periods in the loaded records, newest first. */
export function periodsOf(records: PaymentRecordInput[] | null): CalendarDay[] {
	if (records === null) return [];
	return [...new Set(records.map((record) => record.billingDate))].sort((left, right) =>
		right.localeCompare(left),
	);
}

/**
 * Aggregates one period from its **stored** Payment Records.
 *
 * Every figure comes from the records that period actually has. Nothing is
 * recomputed from today's price or today's membership: a period billed when
 * the Subscription cost 299.00 and had two Members stays billed at those
 * amounts after a price change, and re-deriving it would silently rewrite the
 * User's history.
 *
 * `records === null` means the payments section could not be read, which is
 * reported rather than collapsed into "this period has nothing".
 */
export function periodSummary(input: {
	records: PaymentRecordInput[] | null;
	billingDate: string | null;
}): PeriodSummary {
	if (input.records === null) return UNAVAILABLE_PERIOD;
	if (input.billingDate === null) return { ...UNAVAILABLE_PERIOD, state: 'no-records' };

	const forPeriod = input.records.filter((record) => record.billingDate === input.billingDate);
	const evaluated: PeriodRecord[] = forPeriod.map((record) => ({
		record,
		status: contributionStatus({
			status: record.status,
			amount: record.amount,
			billingDate: record.billingDate,
			paidDate: record.paidDate,
			transactionId: record.transactionId,
		}),
	}));

	const memberRecords = evaluated.filter((entry) => entry.record.memberId !== null);
	const ownerRecords = evaluated.filter((entry) => entry.record.memberId === null);
	if (evaluated.length === 0) {
		return { ...UNAVAILABLE_PERIOD, state: 'no-records', billingDate: input.billingDate };
	}

	const readable = memberRecords.filter((entry) => entry.status.state !== 'unavailable');
	const unreadableCount = memberRecords.length - readable.length;
	// Nothing readable to add up: there is no subtotal, partial or otherwise.
	// Reporting 0.00 here would answer "how much came in this period" with a
	// number the records never supported.
	const nothingReadable = memberRecords.length > 0 && readable.length === 0;
	const sum = (entries: PeriodRecord[]) =>
		nothingReadable ? null : sumMoney(entries.map((entry) => entry.status.amount ?? 0));

	return {
		state: 'has-records',
		billingDate: input.billingDate,
		memberRecords,
		ownerRecords,
		billed: sum(readable),
		collected: sum(readable.filter((entry) => entry.status.state === 'received')),
		outstanding: sum(readable.filter((entry) => entry.status.state === 'awaiting')),
		unreadableCount,
		partial: unreadableCount > 0,
		unreadableOwnerCount: ownerRecords.filter((entry) => entry.status.amount === null).length,
	};
}

/* -------------------------------------------------------------------------
 * List-page aggregates
 * ---------------------------------------------------------------------- */

export type PriceEquivalents = {
	/** Gross current price per month, yearly plans counted as a twelfth. */
	monthly: number;
	/** The same figure over twelve months. */
	yearly: number;
	/** Subscriptions whose price or cycle could not be read, and are excluded. */
	excluded: number;
};

/**
 * Current gross price equivalents across a list of Subscriptions.
 *
 * These are price equivalents, not cash due: a yearly plan does not charge a
 * twelfth each month, and nothing here accounts for contributions the User
 * collects back from Subscription Members.
 *
 * `subscriptions === null` — the list could not be read — returns `null`. The
 * page must not answer "how much am I committed to" with 0.00 because the
 * request failed; that is a statement about the User's money that no response
 * supported.
 */
export function priceEquivalents(
	subscriptions: SubscriptionCost[] | null,
): PriceEquivalents | null {
	if (subscriptions === null) return null;

	// Accumulated as *yearly* cents, so the monthly figure is rounded exactly
	// once at the end. Rounding each yearly plan to a monthly twelfth first and
	// then summing would make the two figures disagree by a few cents, and
	// neither would be the twelfth of the other the label promises.
	let yearlyCents = 0;
	let excluded = 0;
	for (const subscription of subscriptions) {
		const cost = money(subscription.cost);
		if (cost === null || (subscription.billingCycle !== 'MONTHLY' && subscription.billingCycle !== 'YEARLY')) {
			excluded++;
			continue;
		}
		yearlyCents += subscription.billingCycle === 'YEARLY' ? cents(cost) : cents(cost) * 12;
	}

	return {
		monthly: fromCents(divideCentsHalfUp(yearlyCents, 12)),
		yearly: fromCents(yearlyCents),
		excluded,
	};
}
