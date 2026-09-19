/**
 * Boundary validation for financial payloads.
 *
 * A 200 response is not on its own evidence that the body means what a loader
 * assumes. Before Slice 1A-loader these loaders spread partial JSON over a
 * zero-filled default, so a malformed success could present a fabricated
 * 0.00 as the User's Net Balance. Every parser here returns `null` instead of
 * filling a missing or non-numeric money field, and the caller turns that into
 * an explicit "unavailable" section.
 *
 * Only fields the pages actually depend on are required; unknown extra fields
 * are ignored so an additive backend change stays compatible.
 */

import type { BalanceSummary } from '$lib/types/boxes';
import type { BoxPlanStatus, BoxPlanSummary, BoxPlanType } from '$lib/types/box-plans';

function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/** A money or count field must be a real, finite number — not null, not a string. */
function num(value: unknown): number | null {
	return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function str(value: unknown): string | null {
	return typeof value === 'string' ? value : null;
}

function nullableNum(value: unknown): number | null | undefined {
	if (value === null) return null;
	return num(value) ?? undefined;
}

/**
 * A nullable identifier that decides whether records are linked.
 *
 * `null` here is a claim — "this Payment Record has no Transaction", "this
 * Transaction belongs to no Subscription" — and the page turns that claim into
 * offered actions: link, unlink, delete a billing period. An unreadable value
 * must therefore fail the record rather than degrade to `null`, which would
 * both misdescribe the data and enable a write against it. Absent and
 * explicitly-null are the only accepted spellings of "not linked".
 */
function linkId(value: unknown): number | null | undefined {
	if (value === undefined || value === null) return null;
	return num(value) ?? undefined;
}

/**
 * An ISO calendar date (`YYYY-MM-DD`).
 *
 * Dates from these payloads are fed to `Intl` formatters, and an unparseable
 * one throws during render — turning a malformed 200 into a blank page instead
 * of one unavailable section. Validating at the boundary keeps the failure
 * scoped to the section that actually received bad data.
 */
function isoDate(value: unknown): string | null {
	const text = str(value);
	const match = text === null ? null : /^(\d{4})-(\d{2})-(\d{2})$/.exec(text);
	if (match === null) return null;

	// The shape alone is not enough: `2026-13-45` matches it, and JavaScript
	// would silently roll that over into a real but wrong day rather than
	// refusing it.
	const [, year, month, day] = match.map(Number);
	const date = new Date(year, month - 1, day);
	const real =
		date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
	return real ? (text as string) : null;
}

function nullableIsoDate(value: unknown): string | null | undefined {
	if (value === undefined || value === null) return null;
	return isoDate(value) ?? undefined;
}

function nullableStr(value: unknown): string | null | undefined {
	if (value === null) return null;
	return str(value) ?? undefined;
}

/** Applies a parser to every element, failing the whole list if any element fails. */
function parseList<T>(value: unknown, parse: (item: unknown) => T | null): T[] | null {
	if (!Array.isArray(value)) return null;
	const parsed: T[] = [];
	for (const item of value) {
		const one = parse(item);
		if (one === null) return null;
		parsed.push(one);
	}
	return parsed;
}

export type MonthSummary = { month: number; ingress: number; egress: number };

export type DashboardSummary = {
	year: number;
	netBalance: number;
	inBoxes: number;
	availableToSpend: number;
	totalIngress: number;
	totalEgress: number;
	monthly: MonthSummary[];
};

export function parseBalanceSummary(value: unknown): BalanceSummary | null {
	if (!isRecord(value)) return null;
	const netBalance = num(value.netBalance);
	const inBoxes = num(value.inBoxes);
	const availableToSpend = num(value.availableToSpend);
	if (netBalance === null || inBoxes === null || availableToSpend === null) return null;
	return { netBalance, inBoxes, availableToSpend };
}

function parseMonth(value: unknown): MonthSummary | null {
	if (!isRecord(value)) return null;
	const month = num(value.month);
	const ingress = num(value.ingress);
	const egress = num(value.egress);
	if (month === null || ingress === null || egress === null) return null;
	return { month, ingress, egress };
}

export function parseDashboardSummary(value: unknown): DashboardSummary | null {
	if (!isRecord(value)) return null;
	const netBalance = num(value.netBalance);
	const inBoxes = num(value.inBoxes);
	const availableToSpend = num(value.availableToSpend);
	const totalIngress = num(value.totalIngress);
	const totalEgress = num(value.totalEgress);
	const monthly = parseList(value.monthly, parseMonth);
	if (
		netBalance === null ||
		inBoxes === null ||
		availableToSpend === null ||
		totalIngress === null ||
		totalEgress === null ||
		monthly === null
	) {
		return null;
	}
	// `year` is echoed by the backend but the loader already knows which year it
	// asked for, so an absent year is not a reason to discard real balances.
	return {
		year: num(value.year) ?? 0,
		netBalance,
		inBoxes,
		availableToSpend,
		totalIngress,
		totalEgress,
		monthly,
	};
}

export type AccountSummary = { id: number; name: string; kind: string; balance: number };

export function parseAccount(value: unknown): AccountSummary | null {
	if (!isRecord(value)) return null;
	const id = num(value.id);
	const name = str(value.name);
	const kind = str(value.kind);
	const balance = num(value.balance);
	if (id === null || name === null || kind === null || balance === null) return null;
	return { id, name, kind, balance };
}

export function parseAccounts(value: unknown): AccountSummary[] | null {
	return parseList(value, parseAccount);
}

export type CreditSettings = { creditLimit: number };

export function parseCreditSettings(value: unknown): CreditSettings | null {
	if (!isRecord(value)) return null;
	const creditLimit = num(value.creditLimit);
	return creditLimit === null ? null : { creditLimit };
}

export type CreditSettingsDetail = CreditSettings & {
	statementClosingDay: number;
	paymentDueDay: number;
};

/**
 * The full Credit settings the account page edits.
 *
 * The limit alone is enough to derive available credit, but this page also
 * prefills the cycle days into a form that writes them back, so a missing or
 * unreadable day must fail the section rather than arrive in the form as a
 * blank the User would unknowingly save over their real setting.
 */
export function parseCreditSettingsDetail(value: unknown): CreditSettingsDetail | null {
	if (!isRecord(value)) return null;
	const creditLimit = num(value.creditLimit);
	const statementClosingDay = num(value.statementClosingDay);
	const paymentDueDay = num(value.paymentDueDay);
	if (creditLimit === null || statementClosingDay === null || paymentDueDay === null) return null;
	return { creditLimit, statementClosingDay, paymentDueDay };
}

export type CreditStatementSummary = {
	id: number;
	periodStart: string;
	periodEnd: string;
	/**
	 * `null` when the confirmed statement carries no usable due date.
	 *
	 * Decision D2 has a state for exactly this — outstanding, due date
	 * unavailable — so the amount still owed survives. Failing the whole list
	 * instead would discard a real outstanding balance over a missing date and
	 * leave the card looking like it owes nothing.
	 */
	dueDate: string | null;
	officialBalance: number;
	officialMinimumPayment: number;
	officialAvoidInterest: number;
	officialNote: string | null;
	paidAmount: number;
	outstandingBalance: number;
	/**
	 * Recorded activity disagrees with the confirmed bank snapshot.
	 *
	 * An independent review notice, not a payment state: a mismatched statement
	 * is still owed exactly its `outstandingBalance`, and neither figure is
	 * evidence that the other is wrong.
	 */
	reconciliationMismatch: boolean;
	mismatchAmount: number;
};

/**
 * Confirmed Credit Statements.
 *
 * Every figure here is a bank-issued snapshot the User later acts on — the
 * outstanding payment, the minimum, the amount that avoids interest — so a
 * missing or unreadable one fails the whole list rather than becoming a zero
 * that would read as "nothing left to pay". `reconciliationMismatch` must be a
 * real boolean for the same reason: absent is not the same claim as `false`.
 *
 * The due date is the exception. It changes how the obligation is *labeled*,
 * not whether it exists, and D2 defines an outstanding/due-date-unknown state
 * for it, so an unusable one arrives as `null` and the amount owed survives.
 */
export function parseCreditStatements(value: unknown): CreditStatementSummary[] | null {
	return parseList(value, (item) => {
		if (!isRecord(item)) return null;
		const id = num(item.id);
		const periodStart = isoDate(item.periodStart);
		const periodEnd = isoDate(item.periodEnd);
		// Unlike the amounts, an unusable due date does not fail the statement:
		// D2 keeps the outstanding balance and presents it as due-date-unknown.
		const dueDate = isoDate(item.dueDate);
		const officialBalance = num(item.officialBalance);
		const officialMinimumPayment = num(item.officialMinimumPayment);
		const officialAvoidInterest = num(item.officialAvoidInterest);
		const officialNote = nullableStr(item.officialNote);
		const paidAmount = num(item.paidAmount);
		const outstandingBalance = num(item.outstandingBalance);
		const mismatchAmount = num(item.mismatchAmount);
		if (
			id === null ||
			periodStart === null ||
			periodEnd === null ||
			officialBalance === null ||
			officialMinimumPayment === null ||
			officialAvoidInterest === null ||
			officialNote === undefined ||
			paidAmount === null ||
			outstandingBalance === null ||
			mismatchAmount === null ||
			typeof item.reconciliationMismatch !== 'boolean'
		) {
			return null;
		}
		return {
			id,
			periodStart,
			periodEnd,
			dueDate,
			officialBalance,
			officialMinimumPayment,
			officialAvoidInterest,
			officialNote,
			paidAmount,
			outstandingBalance,
			reconciliationMismatch: item.reconciliationMismatch,
			mismatchAmount,
		};
	});
}

export type AccountTrackingStatus = {
	/**
	 * The authoritative tracking mode.
	 *
	 * This boolean is the only thing that decides which formula produced the
	 * Net Balance on screen, so it must be a real boolean: decision D1 forbids
	 * inferring the mode from balances, and a defaulted `false` here would be
	 * exactly that inference wearing a different hat.
	 */
	active: boolean;
	setupRequired: boolean;
	activatedAt: string | null;
	transactionNetBalance: number | null;
	accountNetBalance: number | null;
};

export function parseAccountTrackingStatus(value: unknown): AccountTrackingStatus | null {
	if (!isRecord(value)) return null;
	if (typeof value.active !== 'boolean' || typeof value.setupRequired !== 'boolean') return null;
	// The two net balances are explanatory metadata for the setup screen. They
	// are reported when readable and withheld otherwise; an unreadable one is
	// not a reason to discard a perfectly good `active` boolean.
	return {
		active: value.active,
		setupRequired: value.setupRequired,
		activatedAt: nullableStr(value.activatedAt) ?? null,
		transactionNetBalance: num(value.transactionNetBalance),
		accountNetBalance: num(value.accountNetBalance),
	};
}

const BOX_PLAN_TYPES = new Set<BoxPlanType>(['SAVING_GOAL', 'SPENDING_BUDGET']);
const BOX_PLAN_STATUSES = new Set<BoxPlanStatus>([
	'ACTIVE',
	'READY_TO_COMPLETE',
	'OVERDUE',
	'COMPLETED',
	'ABANDONED',
	'ENDED',
]);

/**
 * Box Plan summaries for the Boxes overview.
 *
 * The overview states what a Box is *for*, so an unrecognized Plan Type or
 * status must fail the section rather than be shown as an unplanned Box: "no
 * plan" offers to create one, and that is the wrong invitation for a Box that
 * already has a plan we failed to understand.
 */
export function parseBoxPlanSummaries(value: unknown): BoxPlanSummary[] | null {
	return parseList(value, (item) => {
		if (!isRecord(item)) return null;
		const id = num(item.id);
		const boxId = num(item.boxId);
		const type = str(item.type);
		const status = str(item.status);
		const createdAt = str(item.createdAt);
		const closedAt = nullableStr(item.closedAt);
		const completionAmount = nullableNum(item.completionAmount);
		if (id === null || boxId === null || createdAt === null) return null;
		if (type === null || !BOX_PLAN_TYPES.has(type as BoxPlanType)) return null;
		if (status === null || !BOX_PLAN_STATUSES.has(status as BoxPlanStatus)) return null;
		if (closedAt === undefined || completionAmount === undefined) return null;
		return {
			id,
			boxId,
			type: type as BoxPlanType,
			status: status as BoxPlanStatus,
			createdAt,
			closedAt,
			completionAmount,
		};
	});
}

export type Subscription = {
	id: number;
	name: string;
	cost: number;
	billingCycle: string;
	type: string;
	categoryId: number | null;
	/**
	 * The generation cursor, or `null` when it could not be read.
	 *
	 * Unreadable does not fail the whole Subscription. The cursor only says
	 * whether Keenti still owes itself Payment Records; the name, price, split
	 * and stored records are independent facts, and dropping all of them over
	 * one bad date turned a missing cursor into a page that would not load.
	 * Every surface reads the cursor through `billingGenerationStatus`, which
	 * already reports an unreadable one as `unavailable` rather than guessing.
	 */
	nextBillingDate: string | null;
	tokenUuid: string | null;
	ownerParticipates: boolean | null;
	createdAt: string;
};

export function parseSubscription(value: unknown): Subscription | null {
	if (!isRecord(value)) return null;
	const id = num(value.id);
	const name = str(value.name);
	const cost = num(value.cost);
	const billingCycle = str(value.billingCycle);
	const type = str(value.type);
	const nextBillingDate = isoDate(value.nextBillingDate);
	if (
		id === null ||
		name === null ||
		cost === null ||
		billingCycle === null ||
		type === null
	) {
		return null;
	}
	return {
		id,
		name,
		cost,
		billingCycle,
		type,
		categoryId: nullableNum(value.categoryId) ?? null,
		nextBillingDate,
		tokenUuid: nullableStr(value.tokenUuid) ?? null,
		ownerParticipates: typeof value.ownerParticipates === 'boolean' ? value.ownerParticipates : null,
		createdAt: str(value.createdAt) ?? '',
	};
}

/**
 * The Subscriptions list.
 *
 * The overview sums these into a recurring-commitment figure, so an unreadable
 * price has to fail the list rather than be skipped: a total that silently
 * omits one Subscription understates the commitment without saying so.
 */
export function parseSubscriptions(value: unknown): Subscription[] | null {
	return parseList(value, parseSubscription);
}

export type CategorySummary = { id: number; name: string; type: string };

export function parseCategories(value: unknown): CategorySummary[] | null {
	return parseList(value, (item) => {
		if (!isRecord(item)) return null;
		const id = num(item.id);
		const name = str(item.name);
		if (id === null || name === null) return null;
		return { id, name, type: str(item.type) ?? '' };
	});
}

export type ContactSummary = {
	id: number;
	name: string;
	phone: string | null;
	email: string | null;
};

export function parseContacts(value: unknown): ContactSummary[] | null {
	return parseList(value, (item) => {
		if (!isRecord(item)) return null;
		const id = num(item.id);
		const name = str(item.name);
		if (id === null || name === null) return null;
		return {
			id,
			name,
			phone: nullableStr(item.phone) ?? null,
			email: nullableStr(item.email) ?? null,
		};
	});
}

export type MemberResponse = {
	id: number;
	subscriptionId: number;
	contactId: number | null;
	contactName: string | null;
	shareAmount: number | null;
	createdAt: string;
};

export function parseMembers(value: unknown): MemberResponse[] | null {
	return parseList(value, (item) => {
		if (!isRecord(item)) return null;
		const id = num(item.id);
		const subscriptionId = num(item.subscriptionId);
		const shareAmount = nullableNum(item.shareAmount);
		// A member's share is money: absent is fine, unreadable is not.
		if (id === null || subscriptionId === null || shareAmount === undefined) return null;
		return {
			id,
			subscriptionId,
			contactId: nullableNum(item.contactId) ?? null,
			contactName: nullableStr(item.contactName) ?? null,
			shareAmount,
			createdAt: str(item.createdAt) ?? '',
		};
	});
}

export type PaymentRecord = {
	id: number;
	subscriptionId: number;
	memberId: number | null;
	billingDate: string;
	amount: number;
	status: string;
	paidDate: string | null;
	transactionId: number | null;
	createdAt: string;
};

export function parsePayments(value: unknown): PaymentRecord[] | null {
	return parseList(value, (item) => {
		if (!isRecord(item)) return null;
		const id = num(item.id);
		const subscriptionId = num(item.subscriptionId);
		const billingDate = isoDate(item.billingDate);
		const amount = num(item.amount);
		const status = str(item.status);
		// `memberId === null` means the Subscription Owner's own share, and
		// `transactionId === null` means the record is unsettled and offers a
		// link action; neither may be inferred from an unreadable value.
		const memberId = linkId(item.memberId);
		const transactionId = linkId(item.transactionId);
		const paidDate = nullableIsoDate(item.paidDate);
		if (
			id === null ||
			subscriptionId === null ||
			billingDate === null ||
			amount === null ||
			status === null ||
			memberId === undefined ||
			transactionId === undefined ||
			paidDate === undefined
		) {
			return null;
		}
		return {
			id,
			subscriptionId,
			memberId,
			billingDate,
			amount,
			status,
			paidDate,
			transactionId,
			createdAt: str(item.createdAt) ?? '',
		};
	});
}

export type TransactionResponse = {
	id: number;
	amount: number;
	direction: string;
	description: string;
	transactionDate: string;
	categoryId: number | null;
	categoryName: string | null;
	categoryHue: number | null;
	contactId: number | null;
	contactName: string | null;
	subscriptionId: number | null;
};

export function parseTransactions(value: unknown): TransactionResponse[] | null {
	return parseList(value, (item) => {
		if (!isRecord(item)) return null;
		const id = num(item.id);
		const amount = num(item.amount);
		const direction = str(item.direction);
		const transactionDate = isoDate(item.transactionDate);
		// A Transaction with no `subscriptionId` is offered as a link candidate,
		// so an unreadable one must fail rather than pass as unlinked.
		const subscriptionId = linkId(item.subscriptionId);
		if (
			id === null ||
			amount === null ||
			direction === null ||
			transactionDate === null ||
			subscriptionId === undefined
		) {
			return null;
		}
		return {
			id,
			amount,
			direction,
			description: str(item.description) ?? '',
			transactionDate,
			categoryId: nullableNum(item.categoryId) ?? null,
			categoryName: nullableStr(item.categoryName) ?? null,
			categoryHue: nullableNum(item.categoryHue) ?? null,
			contactId: nullableNum(item.contactId) ?? null,
			contactName: nullableStr(item.contactName) ?? null,
			subscriptionId,
		};
	});
}

/** Current estimate must contain a real amount and calendar period before display. */
export function parseCurrentCreditEstimate(value: unknown) {
	if (!isRecord(value)) return null;
	const periodStart = isoDate(value.periodStart);
	const periodEnd = isoDate(value.periodEnd);
	const dueDate = isoDate(value.dueDate);
	const estimatedBalance = num(value.estimatedBalance);
	if (periodStart === null || periodEnd === null || dueDate === null || estimatedBalance === null) return null;
	return { periodStart, periodEnd, dueDate, estimatedBalance };
}
