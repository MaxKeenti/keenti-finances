/**
 * When a dashboard section is entitled to say "there is nothing here".
 *
 * Every section has three states, not two. It has something to show; it has
 * nothing to show *and knows that for certain*; or it could not read everything
 * it needed, in which case having found nothing is not evidence of anything.
 *
 * The third state is the one that gets lost. "No active plans — create one",
 * "no expected money" and "nothing needs your attention" are all claims, and a
 * section that failed halfway through its reads cannot make them: the plan, the
 * contribution or the overdrawn account it did not manage to read is exactly
 * the one the User needed to see. So emptiness is asserted only from a complete
 * read, and an incomplete one says so and offers a retry instead.
 */

import type { OverviewExpected, OverviewPlans, OverviewPosition } from '$lib/server/payloads';

/**
 * Which credit terms the position's Net Balance sentence has to name.
 *
 * Credit debt and credit in the User's favour are independent sums — one card
 * can be owed while another is overpaid — and Net Balance already contains
 * both. So a sentence that names only one of two non-zero terms states
 * arithmetic that does not add up: "held minus debt = net" is wrong by exactly
 * the credit in favour sitting inside `net`.
 *
 * `null` means there is no breakdown to narrate, which is the pre-activation
 * state rather than a zeroed one (decision D1).
 */
export type CreditTerms = 'mixed' | 'debt' | 'in-favor' | 'settled';

export function creditTerms(position: OverviewPosition): CreditTerms | null {
	if (position.moneyHeld === null) return null;
	const debt = position.creditDebt ?? 0;
	const inFavor = position.creditInFavor ?? 0;
	if (debt > 0 && inFavor > 0) return 'mixed';
	if (debt > 0) return 'debt';
	if (inFavor > 0) return 'in-favor';
	return 'settled';
}

/**
 * - `populated` — there is something to show.
 * - `empty` — there is nothing, and every read it depends on succeeded.
 * - `unknown` — nothing was found, but at least one read did not complete.
 */
export type SectionEmptiness = 'populated' | 'empty' | 'unknown';

function emptiness(hasContent: boolean, complete: boolean): SectionEmptiness {
	if (hasContent) return 'populated';
	return complete ? 'empty' : 'unknown';
}

export function plansEmptiness(plans: OverviewPlans): SectionEmptiness {
	// A Box whose plan could not be read is not a Box without a plan, so an
	// empty list under `partial` must not offer to create the plan that may
	// already exist.
	return emptiness(plans.items.length > 0, !plans.partial);
}

/**
 * Whether the plan totals may be presented as totals.
 *
 * Reserved money is summed over the plans that were read. Under a partial read
 * that is a subtotal, and an unqualified heading would understate how much of
 * the User's money is already spoken for.
 */
export function plansTotalsAreComplete(plans: OverviewPlans): boolean {
	return !plans.partial;
}

export function expectedEmptiness(expected: OverviewExpected): SectionEmptiness {
	const hasContent = expected.debtCount > 0 || expected.contributionCount > 0;
	// Debts arrive with the section; contributions can fail on their own, and
	// when they do the expected total is a subtotal of the Debts alone.
	return emptiness(hasContent, expected.contributionsAvailable && !expected.partial);
}

/**
 * Needs attention draws on two sections: its own facts, and the account
 * balances the position section holds. Without the position there are no
 * overdrawn-account or over-limit alerts, so an empty list cannot mean the User
 * has nothing to attend to.
 */
export function attentionEmptiness(input: {
	alertCount: number;
	partial: boolean;
	positionAvailable: boolean;
}): SectionEmptiness {
	return emptiness(input.alertCount > 0, !input.partial && input.positionAvailable);
}
