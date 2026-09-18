/**
 * User-facing labels for the confirmed-statement payment states (decision D2).
 *
 * Kept beside the derivation but separate from it: `obligation-status.ts` stays
 * pure and locale-free, and every surface that shows a statement — the
 * dashboard's attention row and the Credit Financial Account page — reads its
 * wording from here, so the two cannot describe the same statement differently.
 */

import { m } from '$lib/paraglide/messages.js';
import { formatDateOnly } from '$lib/formatting';
import type { StatementPayment, StatementPaymentState } from '$lib/obligation-status';

export function statementStateLabel(state: StatementPaymentState): string {
	switch (state) {
		case 'unavailable':
			return m.statement_status_unavailable();
		case 'estimated-only':
			return m.statement_status_estimated();
		case 'covered':
			return m.statement_status_covered();
		case 'outstanding-due-unknown':
			return m.statement_status_outstanding_due_unknown();
		case 'outstanding-past-due':
			return m.statement_status_outstanding_past_due();
		case 'outstanding-due-today':
			return m.statement_status_outstanding_due_today();
		case 'outstanding-upcoming':
			return m.statement_status_outstanding_upcoming();
	}
}

/**
 * The amount sentence, or `null` when there is no confirmed amount to state.
 *
 * An unavailable status has no amount by definition, and a covered one has
 * nothing left to pay; neither gets a fabricated `$0.00`.
 */
export function statementAmountLabel(
	status: StatementPayment,
	format: (value: number) => string,
	locale: string | undefined,
): string | null {
	if (status.outstanding === null || status.outstanding <= 0) return null;
	const amount = format(status.outstanding);
	return status.dueDate === null
		? m.statement_outstanding_amount_no_date({ amount })
		: m.statement_outstanding_amount({ amount, date: formatDateOnly(status.dueDate, locale) });
}
