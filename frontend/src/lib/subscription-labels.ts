/**
 * User-facing labels for the Subscription obligation states (decision D2).
 *
 * Kept beside the derivations but separate from them, the same way
 * `statement-labels.ts` is: `obligation-status.ts` and `subscription-summary.ts`
 * stay pure and locale-free, and every surface that describes a generation
 * cursor or a contribution reads its wording from here so two pages cannot
 * describe the same record differently.
 *
 * Nothing here turns a generation state into a payment claim. "Pending" means
 * Keenti has not written the Payment Records yet — not that a provider charge
 * is unpaid, and not that a Subscription Member is late.
 */

import { m } from '$lib/paraglide/messages.js';
import { formatDateOnly } from '$lib/formatting';
import type { BillingGeneration, ContributionState } from '$lib/obligation-status';

export function billingGenerationLabel(state: BillingGeneration['state']): string {
	switch (state) {
		case 'unavailable':
			return m.subscriptions_generation_unavailable();
		case 'pending':
			return m.subscriptions_generation_pending();
		case 'due-today':
			return m.subscriptions_generation_due_today();
		case 'caught-up':
			return m.subscriptions_generation_caught_up();
	}
}

/**
 * The sentence under the generation label.
 *
 * Every state except `unavailable` names the cursor date, so the User can see
 * which day the status is about. When the cursor itself is what could not be
 * read there is no date to name, and none is invented.
 */
export function billingGenerationDescription(
	generation: BillingGeneration,
	locale: string | undefined,
): string {
	if (generation.state === 'unavailable' || generation.scheduledDate === null) {
		return m.subscriptions_generation_unavailable_description();
	}
	const date = formatDateOnly(generation.scheduledDate, locale);
	switch (generation.state) {
		case 'pending':
			return m.subscriptions_generation_pending_description({ date });
		case 'due-today':
			return m.subscriptions_generation_due_today_description({ date });
		case 'caught-up':
			return m.subscriptions_generation_caught_up_description({ date });
	}
}

export function contributionLabel(state: ContributionState): string {
	switch (state) {
		case 'unavailable':
			return m.subscriptions_contribution_unavailable();
		case 'received':
			return m.subscriptions_contribution_received();
		case 'awaiting':
			return m.subscriptions_contribution_awaiting();
	}
}

/**
 * The label for one Payment Record, which is not always a contribution.
 *
 * Billing writes a record with no Subscription Member for a `PERSONAL`
 * Subscription, carrying the whole price the Owner themselves owes. Calling
 * that "awaiting contribution" would describe money arriving from someone
 * else; it is the User's own record, so it reads as a plain payment status.
 * The distinction comes from the record itself, not from the Subscription
 * Type, which may have changed since the record was written.
 */
export function recordStateLabel(state: ContributionState, ownRecord: boolean): string {
	if (!ownRecord) return contributionLabel(state);
	switch (state) {
		case 'unavailable':
			return m.subscriptions_contribution_unavailable();
		case 'received':
			return m.status_paid();
		case 'awaiting':
			return m.status_pending();
	}
}

export function contributionBadgeVariant(
	state: ContributionState,
): 'success' | 'warning' | 'secondary' {
	switch (state) {
		case 'received':
			return 'success';
		case 'awaiting':
			return 'warning';
		case 'unavailable':
			return 'secondary';
	}
}
