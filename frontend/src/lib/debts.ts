/**
 * Debt balances in both directions — summarizing recorded Debts, nothing more.
 *
 * These helpers only add up what is already recorded as unpaid. They introduce
 * no new financial formula, and outstanding money never enters Net Balance,
 * In Boxes, or Available to Spend, which count money that actually moved.
 *
 * The two sides are never netted against each other: owing Ana 500 while Ana
 * owes you 300 is two obligations, not one of 200 (ADR-0023). Nor are two
 * currencies: pesos and euros owed by the same Contact are two balances, and a
 * side's total is stated once per currency (ADR-0024).
 */

/**
 * A Debt's Direction, which is also the Direction of the Transaction each of
 * its Debt Payments creates: `INGRESS` when the Contact owes the User,
 * `EGRESS` when the User owes the Contact.
 */
export type DebtDirection = 'INGRESS' | 'EGRESS';

/** Either Direction, or no restriction at all. */
export type DirectionFilter = 'ALL' | DebtDirection;

export type DirectedDebt = {
	id: number;
	contactId: number | null;
	direction: string;
	/** Absent on a Debt from before currencies existed, which was MXN. */
	currency?: string;
	remaining: number;
	status: string;
};

export type CounterpartSummary = {
	key: string;
	direction: DebtDirection;
	currency: string;
	contactId: number | null;
	name: string;
	outstanding: number;
	debtCount: number;
};

export type DebtSide = {
	direction: DebtDirection;
	counterparts: CounterpartSummary[];
	/** One total per currency, never one sum across currencies. */
	totals: { currency: string; total: number }[];
	outstandingDebtCount: number;
};

export type DebtSummary = {
	owedToYou: DebtSide;
	youOwe: DebtSide;
};

/**
 * A Debt recorded before Debts became bidirectional carries no Direction, and
 * back then every Debt was money owed to the User. Anything unrecognized reads
 * the same way rather than dropping the Debt out of both sides of the page.
 */
export function debtDirection(value: string | null | undefined): DebtDirection {
	return value === 'EGRESS' ? 'EGRESS' : 'INGRESS';
}

/**
 * Stable key for the counterpart drill-down.
 *
 * The Direction is part of the key because one Contact can sit on both sides at
 * once — you may owe Ana while Ana owes you — and those are separate balances.
 * A Debt without a Contact cannot be grouped with anything else, so it is its
 * own counterpart. The key is what `?counterpart=` carries, which makes an
 * opened counterpart a real URL: linkable, reloadable, Back-navigable.
 */
export function counterpartKey(
	debt: Pick<DirectedDebt, 'id' | 'contactId' | 'direction' | 'currency'>,
): string {
	const side = debtDirection(debt.direction) === 'EGRESS' ? 'out' : 'in';
	const base = debt.contactId === null ? `${side}-debt-${debt.id}` : `${side}-contact-${debt.contactId}`;
	// MXN keeps the key it always had, so existing links still resolve.
	const currency = debtCurrency(debt.currency);
	return currency === 'MXN' ? base : `${base}-${currency.toLowerCase()}`;
}

export function debtCurrency(value: string | null | undefined): string {
	return value ?? 'MXN';
}

/** A Debt still open: not settled, and with something left to pay. */
export function isOutstanding(debt: Pick<DirectedDebt, 'status' | 'remaining'>): boolean {
	return debt.status !== 'PAID' && debt.remaining > 0;
}

/** `?direction=` narrowed to a value the page can render; anything else shows both sides. */
export function parseDirectionFilter(value: string | null | undefined): DirectionFilter {
	return value === 'INGRESS' || value === 'EGRESS' ? value : 'ALL';
}

function emptySide(direction: DebtDirection): DebtSide {
	return { direction, counterparts: [], totals: [], outstandingDebtCount: 0 };
}

/** Outstanding money per counterpart and in total, on each side separately. */
export function summarizeDebts<T extends DirectedDebt>(
	debts: T[],
	nameFor: (debt: T) => string,
): DebtSummary {
	const owedToYou = emptySide('INGRESS');
	const youOwe = emptySide('EGRESS');
	const counterparts = new Map<string, CounterpartSummary>();

	for (const debt of debts) {
		if (!isOutstanding(debt)) continue;
		const direction = debtDirection(debt.direction);
		const side = direction === 'EGRESS' ? youOwe : owedToYou;
		const currency = debtCurrency(debt.currency);
		const total = side.totals.find((entry) => entry.currency === currency);
		if (total) total.total += debt.remaining;
		else side.totals.push({ currency, total: debt.remaining });
		side.outstandingDebtCount += 1;

		const key = counterpartKey(debt);
		const current = counterparts.get(key);
		if (current) {
			current.outstanding += debt.remaining;
			current.debtCount += 1;
		} else {
			const summary: CounterpartSummary = {
				key,
				direction,
				currency,
				contactId: debt.contactId,
				name: nameFor(debt),
				outstanding: debt.remaining,
				debtCount: 1,
			};
			counterparts.set(key, summary);
			side.counterparts.push(summary);
		}
	}

	const byOutstanding = (a: CounterpartSummary, b: CounterpartSummary) =>
		b.outstanding - a.outstanding || a.name.localeCompare(b.name);
	owedToYou.counterparts.sort(byOutstanding);
	youOwe.counterparts.sort(byOutstanding);

	return { owedToYou, youOwe };
}

/** The total of one currency on a side, or zero when nothing is owed in it. */
export function sideTotal(side: DebtSide, currency: string): number {
	return side.totals.find((entry) => entry.currency === currency)?.total ?? 0;
}
