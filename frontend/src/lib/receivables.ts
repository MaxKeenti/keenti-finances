/**
 * Money owed to the User — summarizing recorded Debts, nothing more.
 *
 * These helpers only add up what is already recorded as unpaid. They introduce
 * no new financial formula, and outstanding money never enters Net Balance,
 * In Boxes, or Available to Spend, which count money actually received.
 */

export type ReceivableDebt = {
	id: number;
	contactId: number | null;
	remaining: number;
	status: string;
};

export type DebtorSummary = {
	key: string;
	contactId: number | null;
	name: string;
	outstanding: number;
	debtCount: number;
};

export type ReceivablesSummary = {
	debtors: DebtorSummary[];
	totalOutstanding: number;
	outstandingDebtCount: number;
};

/**
 * Stable key for the receivables drill-down.
 *
 * A Debt without a Contact cannot be grouped with anything else, so it is its
 * own debtor. The key is what `?debtor=` carries, which makes an opened debtor
 * a real URL: linkable, reloadable, and reachable with the Back button.
 */
export function debtorKey(debt: Pick<ReceivableDebt, 'id' | 'contactId'>): string {
	return debt.contactId === null ? `debt-${debt.id}` : `contact-${debt.contactId}`;
}

/** A Debt still owed to the User: not settled, and with something left to pay. */
export function isOutstanding(debt: Pick<ReceivableDebt, 'status' | 'remaining'>): boolean {
	return debt.status !== 'PAID' && debt.remaining > 0;
}

/** Outstanding money owed to the User, per debtor and in total. */
export function summarizeReceivables<T extends ReceivableDebt>(
	debts: T[],
	nameFor: (debt: T) => string,
): ReceivablesSummary {
	const debtors = new Map<string, DebtorSummary>();
	let totalOutstanding = 0;
	let outstandingDebtCount = 0;

	for (const debt of debts) {
		if (!isOutstanding(debt)) continue;
		totalOutstanding += debt.remaining;
		outstandingDebtCount += 1;
		const key = debtorKey(debt);
		const current = debtors.get(key);
		if (current) {
			current.outstanding += debt.remaining;
			current.debtCount += 1;
		} else {
			debtors.set(key, {
				key,
				contactId: debt.contactId,
				name: nameFor(debt),
				outstanding: debt.remaining,
				debtCount: 1,
			});
		}
	}

	return {
		debtors: [...debtors.values()].sort(
			(a, b) => b.outstanding - a.outstanding || a.name.localeCompare(b.name),
		),
		totalOutstanding,
		outstandingDebtCount,
	};
}
