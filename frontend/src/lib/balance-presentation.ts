/**
 * Balance and credit presentation derivation (Slice 1A-balance / 3A, decision D1).
 *
 * These helpers decide *what a figure means*, never what it is: no total is
 * computed, adjusted, or substituted here. The accounting formulas stay where
 * ADR-0022 put them —
 *
 *   Net Balance        = sum of signed active Financial Account balances
 *   In Boxes           = sum of active Box balances
 *   Available to Spend = Net Balance − In Boxes
 *
 * — and this module only explains them.
 *
 * The rules that keep the explanations honest:
 *
 * - Tracking mode comes from the authoritative `active` boolean on
 *   `GET /api/accounts/status`. It is never inferred from whether two totals
 *   happen to be equal, from a nonzero balance, or from an empty account list:
 *   a User whose recorded income minus expenses coincidentally equals their
 *   account ledger is not thereby "tracking".
 * - A failed read produces `unknown`/`unavailable`, never a zero and never a
 *   guessed formula. A successfully loaded total keeps being shown even when
 *   the mode is unknown — it just goes unexplained.
 * - Credit figures stay outside the totals above. Available credit is
 *   limit-derived capacity, not money held; a confirmed statement's
 *   outstanding payment is a separate obligation and is never subtracted from
 *   Net Balance a second time.
 */

/** What formula produced the Net Balance currently on screen. */
export type NetBalanceSource =
	/** Tracking off: all-time INGRESS minus EGRESS Transactions. */
	| 'transactions'
	/** Tracking on: the sum of signed Financial Account balances. */
	| 'accounts'
	/** The tracking read failed or carried no usable `active` boolean. */
	| 'unknown';

/** The authoritative tracking section, or `null` when it is unavailable. */
export type TrackingStatus = { active: boolean } | null;

/**
 * Reads the tracking mode from the authoritative status section.
 *
 * Note what this deliberately does not look at: the balances. `status` carries
 * `transactionNetBalance` and `accountNetBalance`, and comparing them is the
 * tempting shortcut that decision D1 rules out.
 */
export function netBalanceSource(tracking: TrackingStatus): NetBalanceSource {
	if (tracking === null || typeof tracking.active !== 'boolean') return 'unknown';
	return tracking.active ? 'accounts' : 'transactions';
}

export type BalanceTotals = {
	netBalance: number;
	inBoxes: number;
	availableToSpend: number;
};

export type AvailableState =
	/** The balance section could not be loaded. Not a zero. */
	| 'unavailable'
	/** Available to Spend is zero or positive. */
	| 'reconciled'
	/** Net Balance is positive but Boxes reserve more than it. */
	| 'over-reserved'
	/** The signed recorded position itself is negative. */
	| 'negative-net';

export type AvailableExplanation = {
	state: AvailableState;
	/** How far below zero Available to Spend is, as a positive magnitude. */
	shortfall: number | null;
	/**
	 * How far below zero the Net Balance itself is, as a positive magnitude, or
	 * `null` when it is not negative.
	 *
	 * The two figures differ whenever Boxes hold anything: Net −200 with 50 In
	 * Boxes leaves Available at −250, and describing the recorded position as
	 * "250 below zero" would overstate it by the reservation. The `negative-net`
	 * sentence is about the Net Balance, so it prints this one.
	 */
	negativeNet: number | null;
	/**
	 * Whether any Box actually holds money.
	 *
	 * Offering "withdraw from a Box" when every Box is empty sends the User to
	 * an action that cannot succeed and implies the shortfall is their
	 * allocation's fault. `null` means the Box balances were not supplied, so
	 * no withdrawal is suggested either way.
	 */
	withdrawalPossible: boolean | null;
};

/**
 * Explains a negative Available to Spend without conflating its causes.
 *
 * Over-reserving (Boxes hold more than the Net Balance) and a negative Net
 * Balance are different stories with different remedies, and neither is
 * evidence that a record is wrong — a confirmed-statement reconciliation
 * mismatch is reported separately, from the statement's own flag.
 */
export function availableToSpendExplanation(
	totals: BalanceTotals | null,
	options: { boxBalances?: number[] | null } = {},
): AvailableExplanation {
	const withdrawalPossible =
		options.boxBalances === undefined || options.boxBalances === null
			? null
			: options.boxBalances.some((balance) => balance > 0);

	if (totals === null) {
		return { state: 'unavailable', shortfall: null, negativeNet: null, withdrawalPossible };
	}
	const negativeNet = totals.netBalance < 0 ? Math.abs(totals.netBalance) : null;
	if (totals.availableToSpend >= 0) {
		return { state: 'reconciled', shortfall: 0, negativeNet, withdrawalPossible };
	}

	const shortfall = Math.abs(totals.availableToSpend);
	// Boxes that hold nothing cannot be the reason money is reserved away, so
	// when In Boxes is zero the shortfall is the recorded position itself.
	const overReserved = totals.netBalance >= 0 && totals.inBoxes > 0;
	return {
		state: overReserved ? 'over-reserved' : 'negative-net',
		shortfall,
		negativeNet,
		// In Boxes answers the same question in aggregate: a total of zero means
		// no Box holds anything to withdraw, so a caller that did not supply the
		// individual balances still gets a correct answer rather than a guess.
		withdrawalPossible: withdrawalPossible ?? totals.inBoxes > 0,
	};
}

/* -------------------------------------------------------------------------
 * Credit Financial Account labels (decision D1)
 * ---------------------------------------------------------------------- */

export type CreditPosition =
	/** Negative signed balance: money owed. Displayed as a magnitude. */
	| 'debt'
	/** Positive signed balance: an overpayment or refund in the User's favor. */
	| 'in-favor'
	/** Exactly zero. */
	| 'settled'
	/** The balance could not be read. */
	| 'unknown';

/** Classifies a Credit Financial Account's signed balance. */
export function creditPosition(balance: unknown): CreditPosition {
	if (typeof balance !== 'number' || !Number.isFinite(balance)) return 'unknown';
	if (balance < 0) return 'debt';
	if (balance > 0) return 'in-favor';
	return 'settled';
}

/** The magnitude to print beside a credit label, or `null` when unreadable. */
export function creditMagnitude(balance: unknown): number | null {
	return typeof balance === 'number' && Number.isFinite(balance) ? Math.abs(balance) : null;
}

/**
 * Limit-derived spending capacity, or `null` when no limit is configured.
 *
 * Without a credit limit there is nothing to subtract from, so available
 * credit is *unknown* rather than zero: `$0.00` would claim the User has none
 * left. This figure never enters money held, Net Balance, In Boxes, or
 * Available to Spend.
 */
export function availableCredit(creditLimit: unknown, balance: unknown): number | null {
	if (typeof creditLimit !== 'number' || !Number.isFinite(creditLimit)) return null;
	if (typeof balance !== 'number' || !Number.isFinite(balance)) return null;
	return Math.max(creditLimit + balance, 0);
}
