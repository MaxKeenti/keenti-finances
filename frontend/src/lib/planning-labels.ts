/**
 * User-facing guidance for the planning preview's reason and note codes.
 *
 * Every code the D5 contract defines has its own guidance saying what to do
 * next, naming the row it concerns. A code this build does not know is shown
 * verbatim with generic guidance rather than hidden, so a newer server can
 * never make a problem disappear from the page.
 */

import { m } from '$lib/paraglide/messages.js';
import { formatLocale } from '$lib/formatting';
import type {
	ClearReason,
	LocalIssue,
	PlanningWindow,
	PreviewFailure,
	PreviewMissingInput,
	PreviewTimingReason,
} from '$lib/planning-preview';

export type LabelContext = {
	window: PlanningWindow | null;
	/** Formats a calendar date for display. */
	date: (value: string) => string;
	money: (value: number) => string;
	boxName: (boxId: number) => string;
	accountName: (accountId: number) => string;
};

/** Server projection reasons with dedicated guidance. */
export const KNOWN_MISSING_INPUT_REASONS = [
	'BASELINE_UNAVAILABLE',
	'ZONE_UNAVAILABLE',
	'DATE_OUT_OF_WINDOW',
	'INVALID_AMOUNT',
	'INVALID_FUNDING',
	'BOX_REQUIRED',
	'BOX_NOT_FOUND',
	'FUNDING_EXCEEDS_COST',
	'BOX_CAPACITY_EXCEEDED',
	'DUPLICATE_RECEIPT',
	'ESSENTIALS_NOT_REVIEWED',
	'COST_NOT_CONFIRMED_UNRECORDED',
	'RECEIPT_NOT_FOUND',
	'RECEIPT_INELIGIBLE',
	'RECEIPT_UNAVAILABLE',
] as const;

export const KNOWN_TIMING_REASONS = [
	'TRACKING_INACTIVE',
	'ZONE_UNAVAILABLE',
	'READ_FAILED',
	'STATEMENT_DUE_DATE_MISSING',
	'RECONCILIATION_MISMATCH',
	'UNCONFIRMED_STATEMENT',
	'STATEMENT_SCHEDULE_MISSING',
] as const;

export const KNOWN_NOTES = [
	'LEDGER_TOTAL_NOT_CASH',
	'NO_AUTOMATIC_TRANSACTION_MATCHING',
	'UNENTERED_COSTS_EXCLUDED',
	'CREDIT_IN_FAVOR_IN_BASELINE',
	'RECEIPTS_IF_RECEIVED',
	'PLAN_EVALUATION_INCOMPLETE',
] as const;

/** "Cost 3" / "Expected receipt 2", numbered from one like the form. */
export function rowName(input: Pick<PreviewMissingInput, 'itemIndex' | 'receiptIndex'>): string {
	if (input.itemIndex !== null) return m.planning_cost_label({ number: input.itemIndex + 1 });
	if (input.receiptIndex !== null) return m.planning_receipt_label({ number: input.receiptIndex + 1 });
	return '';
}

function windowBounds(context: LabelContext) {
	return context.window === null
		? { from: '—', to: '—' }
		: { from: context.date(context.window.from), to: context.date(context.window.to) };
}

export function missingInputMessage(input: PreviewMissingInput, context: LabelContext): string {
	const row = rowName(input);
	switch (input.reason) {
		case 'BASELINE_UNAVAILABLE':
			return m.planning_reason_baseline_unavailable();
		case 'ZONE_UNAVAILABLE':
			return m.planning_reason_zone_unavailable();
		case 'DATE_OUT_OF_WINDOW':
			return m.planning_reason_date_out_of_window({ row, ...windowBounds(context) });
		case 'INVALID_AMOUNT':
			return input.receiptIndex !== null
				? m.planning_reason_receipt_ineligible({ row })
				: m.planning_reason_invalid_amount({ row });
		case 'INVALID_FUNDING':
			return m.planning_reason_invalid_funding({ row });
		case 'BOX_REQUIRED':
			return m.planning_reason_box_required({ row });
		case 'BOX_NOT_FOUND':
			return m.planning_reason_box_not_found({ row });
		case 'FUNDING_EXCEEDS_COST':
			return m.planning_reason_funding_exceeds_cost({ row });
		case 'BOX_CAPACITY_EXCEEDED':
			return m.planning_reason_box_capacity_exceeded({
				box: input.boxId === null ? m.planning_box_unknown({ id: '?' }) : context.boxName(input.boxId),
				shortfall: input.shortfall === null ? '—' : context.money(input.shortfall),
			});
		case 'DUPLICATE_RECEIPT':
			return m.planning_reason_duplicate_receipt({ row });
		case 'ESSENTIALS_NOT_REVIEWED':
			return m.planning_reason_essentials_not_reviewed();
		case 'COST_NOT_CONFIRMED_UNRECORDED':
			return m.planning_reason_cost_not_confirmed_unrecorded({ row });
		case 'RECEIPT_NOT_FOUND':
			return m.planning_reason_receipt_not_found({ row });
		case 'RECEIPT_INELIGIBLE':
			return m.planning_reason_receipt_ineligible({ row });
		case 'RECEIPT_UNAVAILABLE':
			return m.planning_reason_receipt_unavailable();
		default:
			return m.planning_reason_unknown({ code: row ? `${row}: ${input.reason}` : input.reason });
	}
}

export function localIssueMessage(issue: LocalIssue, context: LabelContext): string {
	switch (issue) {
		case 'AMOUNT_REQUIRED':
			return m.planning_issue_amount_required();
		case 'AMOUNT_FORMAT':
			return m.planning_issue_amount_format();
		case 'DATE_REQUIRED':
			return m.planning_issue_date_required(windowBounds(context));
		case 'DESCRIPTION_TOO_LONG':
			return m.planning_issue_description_too_long();
		case 'BOX_AMOUNT_REQUIRED':
			return m.planning_issue_box_amount_required();
		case 'BOX_AMOUNT_FORMAT':
			return m.planning_issue_box_amount_format();
		case 'BOX_UNAVAILABLE':
			return m.planning_issue_box_unavailable();
		case 'RECEIPT_DATE_REQUIRED':
			return m.planning_issue_receipt_date_required(windowBounds(context));
	}
}

export function timingReasonMessage(reason: PreviewTimingReason, context: LabelContext): string {
	const account =
		reason.accountId === null ? m.planning_timing_account_unknown() : context.accountName(reason.accountId);
	switch (reason.code) {
		case 'TRACKING_INACTIVE':
			return m.planning_timing_reason_tracking_inactive();
		case 'ZONE_UNAVAILABLE':
			return m.planning_timing_reason_zone_unavailable();
		case 'READ_FAILED':
			return m.planning_timing_reason_read_failed();
		case 'STATEMENT_DUE_DATE_MISSING':
			return m.planning_timing_reason_statement_due_date_missing({ account });
		case 'RECONCILIATION_MISMATCH':
			return m.planning_timing_reason_reconciliation_mismatch({ account });
		case 'UNCONFIRMED_STATEMENT':
			return m.planning_timing_reason_unconfirmed_statement({ account });
		case 'STATEMENT_SCHEDULE_MISSING':
			return m.planning_timing_reason_statement_schedule_missing({ account });
		default:
			return m.planning_reason_unknown({ code: reason.code });
	}
}

export function noteMessage(code: string, costCount: number): string {
	switch (code) {
		case 'LEDGER_TOTAL_NOT_CASH':
			return m.planning_note_ledger_total_not_cash();
		case 'NO_AUTOMATIC_TRANSACTION_MATCHING':
			return m.planning_note_no_automatic_transaction_matching();
		case 'UNENTERED_COSTS_EXCLUDED':
			return m.planning_essentials_note({ count: costCount });
		case 'CREDIT_IN_FAVOR_IN_BASELINE':
			return m.planning_note_credit_in_favor_in_baseline();
		case 'RECEIPTS_IF_RECEIVED':
			return m.planning_note_receipts_if_received();
		case 'PLAN_EVALUATION_INCOMPLETE':
			return m.planning_note_plan_evaluation_incomplete();
		default:
			return m.planning_note_unknown({ code });
	}
}

export function failureMessage(failure: PreviewFailure): string {
	switch (failure) {
		case 'unreachable':
			return m.planning_failure_unreachable();
		case 'auth':
			return m.planning_failure_auth();
		case 'rejected':
			return m.planning_failure_rejected();
		case 'server':
			return m.planning_failure_server();
		case 'invalid':
			return m.planning_failure_invalid();
	}
}

export function clearedMessage(reason: ClearReason): string {
	switch (reason) {
		case 'edited':
			return m.planning_cleared_edited();
		case 'returned':
			return m.planning_cleared_returned();
		case 'refreshed':
			return m.planning_cleared_refreshed();
	}
}

/**
 * The snapshot instant in the User's zone, never the browser's.
 *
 * Without a usable zone the instant is shown in UTC and labelled as such,
 * rather than silently converted through whatever zone the device is in.
 */
export function formatSnapshotTime(iso: string, timeZone: string | null, locale: string | undefined): string {
	const instant = new Date(iso);
	if (timeZone) {
		try {
			return new Intl.DateTimeFormat(formatLocale(locale), {
				dateStyle: 'medium',
				timeStyle: 'short',
				timeZone,
			}).format(instant);
		} catch {
			// Fall through to the labelled UTC form.
		}
	}
	return `${new Intl.DateTimeFormat(formatLocale(locale), {
		dateStyle: 'medium',
		timeStyle: 'short',
		timeZone: 'UTC',
	}).format(instant)} UTC`;
}
