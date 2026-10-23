/**
 * Per-currency grouping for lists of money-bearing records (ADR-0024).
 *
 * Amounts in different currencies are never added together. A screen that
 * summarises a mixed list shows one total per currency, ordered so the Primary
 * Currency comes first and the rest keep the supported order.
 */

import { SUPPORTED_CURRENCIES } from '$lib/formatting';

export function currencyOrder(codes: Iterable<string>, primary: string): string[] {
	const present = new Set(codes);
	const ordered: string[] = [];
	if (present.has(primary)) ordered.push(primary);
	for (const code of SUPPORTED_CURRENCIES) {
		if (code !== primary && present.has(code)) ordered.push(code);
	}
	// A code the frontend does not know yet still gets its own group.
	for (const code of present) {
		if (!ordered.includes(code)) ordered.push(code);
	}
	return ordered;
}

export function groupByCurrency<T extends { currency: string }>(
	items: readonly T[],
	primary: string,
): { currency: string; items: T[] }[] {
	return currencyOrder(
		items.map((item) => item.currency),
		primary,
	).map((currency) => ({ currency, items: items.filter((item) => item.currency === currency) }));
}

/** Sums `amount(item)` within each currency; never across them. */
export function totalsByCurrency<T extends { currency: string }>(
	items: readonly T[],
	amount: (item: T) => number,
	primary: string,
): { currency: string; total: number }[] {
	return groupByCurrency(items, primary).map((group) => ({
		currency: group.currency,
		total: group.items.reduce((sum, item) => sum + amount(item), 0),
	}));
}
