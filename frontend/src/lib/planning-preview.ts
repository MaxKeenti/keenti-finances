/**
 * The 30-day planning preview (decision D5, slice 5C): draft, request,
 * response and staleness rules, kept free of Svelte so they can be tested.
 *
 * Four rules this module keeps:
 *
 * - The server is the only calculator. Nothing here adds, subtracts or nets an
 *   amount: every figure the page shows comes from one `POST
 *   /api/planning/preview` response, and a request never carries a balance or
 *   a receipt amount.
 * - A result belongs to exactly the draft it was calculated from. Any edit,
 *   hidden tab or refresh clears it, and a response that arrives after that is
 *   discarded rather than shown against inputs it does not describe.
 * - Nothing is guessed. A receipt's scenario date and a cost's date are always
 *   typed by the User; an unreadable response is a failure, never a zero.
 * - The draft lives only in page memory. Nothing here touches storage.
 */

import { asCalendarDay, type CalendarDay, type DayResolution } from '$lib/obligation-status';

export const PLANNING_HORIZON_DAYS = 30;
export const MAX_PLANNING_COSTS = 50;
export const MAX_PLANNING_RECEIPTS = 50;
export const MAX_COST_DESCRIPTION = 200;

/** The inclusive calendar window `[today, today + 29]` in the User's zone. */
export type PlanningWindow = { from: CalendarDay; to: CalendarDay };

/** Adds calendar days to an ISO date without passing through any time zone. */
export function addCalendarDays(day: CalendarDay, days: number): CalendarDay {
	const [year, month, date] = day.split('-').map(Number);
	const shifted = new Date(Date.UTC(year, month - 1, date + days));
	const pad = (value: number) => String(value).padStart(2, '0');
	return `${shifted.getUTCFullYear()}-${pad(shifted.getUTCMonth() + 1)}-${pad(shifted.getUTCDate())}`;
}

/**
 * The window the form offers before the server has answered.
 *
 * It is a hint for the date inputs only: the server resolves its own window
 * from one captured instant and reports any row outside it. An unresolved day
 * (missing or invalid zone) has no window at all — never a UTC stand-in.
 */
export function planningWindow(today: DayResolution): PlanningWindow | null {
	if (today.status !== 'ok') return null;
	const from = asCalendarDay(today.day);
	if (from === null) return null;
	return { from, to: addCalendarDays(from, PLANNING_HORIZON_DAYS - 1) };
}

/* ------------------------------------------------------------------ Draft */

export type ReceiptKind = 'DEBT' | 'PAYMENT_RECORD';

export type CostDraft = {
	/** Stable per-row identity for keyed rendering and focus; never sent. */
	key: number;
	/** Exactly what the User typed; sent as text so no cent is rounded away. */
	amount: string;
	date: string;
	description: string;
	/** `''` means no Box: the whole cost comes out of Available to Spend. */
	boxId: string;
	boxAmount: string;
	notYetRecordedConfirmed: boolean;
};

export type ReceiptDraft = {
	key: number;
	recordKind: ReceiptKind;
	recordId: number;
	/** The User's scenario date. Empty until typed: an arrival date is never assumed. */
	date: string;
};

export type PlanningDraft = {
	essentialsReviewed: boolean;
	costs: CostDraft[];
	receipts: ReceiptDraft[];
	/** Next row key; keys are never reused within a page visit. */
	nextKey: number;
};

export function emptyDraft(): PlanningDraft {
	return { essentialsReviewed: false, costs: [], receipts: [], nextKey: 1 };
}

export function hasDraftContent(draft: PlanningDraft): boolean {
	return draft.costs.length > 0 || draft.receipts.length > 0 || draft.essentialsReviewed;
}

export function addCost(draft: PlanningDraft): PlanningDraft {
	if (draft.costs.length >= MAX_PLANNING_COSTS) return draft;
	const cost: CostDraft = {
		key: draft.nextKey,
		amount: '',
		date: '',
		description: '',
		boxId: '',
		boxAmount: '',
		notYetRecordedConfirmed: false,
	};
	return { ...draft, costs: [...draft.costs, cost], nextKey: draft.nextKey + 1 };
}

export function updateCost(
	draft: PlanningDraft,
	key: number,
	patch: Partial<Omit<CostDraft, 'key'>>,
): PlanningDraft {
	return {
		...draft,
		costs: draft.costs.map((cost) => {
			if (cost.key !== key) return cost;
			const next = { ...cost, ...patch };
			// Choosing "no Box" removes the funding with it, so a hidden amount
			// can never be submitted without the Box it would come from.
			if (patch.boxId === '') next.boxAmount = '';
			return next;
		}),
	};
}

export function removeCost(draft: PlanningDraft, key: number): PlanningDraft {
	return { ...draft, costs: draft.costs.filter((cost) => cost.key !== key) };
}

export function addReceipt(
	draft: PlanningDraft,
	recordKind: ReceiptKind,
	recordId: number,
): PlanningDraft {
	if (draft.receipts.length >= MAX_PLANNING_RECEIPTS) return draft;
	if (isReceiptSelected(draft, recordKind, recordId)) return draft;
	const receipt: ReceiptDraft = { key: draft.nextKey, recordKind, recordId, date: '' };
	return { ...draft, receipts: [...draft.receipts, receipt], nextKey: draft.nextKey + 1 };
}

export function updateReceiptDate(draft: PlanningDraft, key: number, date: string): PlanningDraft {
	return {
		...draft,
		receipts: draft.receipts.map((receipt) => (receipt.key === key ? { ...receipt, date } : receipt)),
	};
}

export function removeReceipt(draft: PlanningDraft, key: number): PlanningDraft {
	return { ...draft, receipts: draft.receipts.filter((receipt) => receipt.key !== key) };
}

export function isReceiptSelected(
	draft: PlanningDraft,
	recordKind: ReceiptKind,
	recordId: number,
): boolean {
	return draft.receipts.some(
		(receipt) => receipt.recordKind === recordKind && receipt.recordId === recordId,
	);
}

/**
 * Withdraws every review confirmation, keeping what the User typed.
 *
 * Used when the page may have fallen behind the ledger — a hidden tab, a
 * refresh — because a cost confirmed as unrecorded may have been recorded in
 * the meantime, and there is no automatic matching to notice it.
 */
export function resetConfirmations(draft: PlanningDraft): PlanningDraft {
	return {
		...draft,
		essentialsReviewed: false,
		costs: draft.costs.map((cost) => ({ ...cost, notYetRecordedConfirmed: false })),
	};
}

/**
 * Drops selected receipts that a freshly loaded catalog no longer offers.
 *
 * A receipt disappears from the catalog when it was paid, settled, deleted
 * or its Subscription trashed. Keeping it would only earn a server rejection;
 * dropping it silently would change the scenario behind the User's back, so
 * the removed rows are returned for the page to name. An unavailable catalog
 * proves nothing either way and removes nothing.
 */
export function reconcileReceipts(
	draft: PlanningDraft,
	available: { debts: Set<number> | null; paymentRecords: Set<number> | null },
): { draft: PlanningDraft; removed: ReceiptDraft[] } {
	const removed: ReceiptDraft[] = [];
	const receipts = draft.receipts.filter((receipt) => {
		const ids = receipt.recordKind === 'DEBT' ? available.debts : available.paymentRecords;
		if (ids === null || ids.has(receipt.recordId)) return true;
		removed.push(receipt);
		return false;
	});
	return removed.length === 0 ? { draft, removed } : { draft: { ...draft, receipts }, removed };
}

/* ------------------------------------------------------- Local validation */

/**
 * Problems the form can see before asking the server.
 *
 * Only inputs that cannot be sent — or that would be sent without a fact the
 * User must supply — are checked here. Money rules (zero, over the limit,
 * fractional cents, funding above cost, Box capacity) and window membership
 * are the server's to judge, and its reasons are shown against the same rows.
 */
export type LocalIssue =
	| 'AMOUNT_REQUIRED'
	| 'AMOUNT_FORMAT'
	| 'DATE_REQUIRED'
	| 'DESCRIPTION_TOO_LONG'
	| 'BOX_AMOUNT_REQUIRED'
	| 'BOX_AMOUNT_FORMAT'
	| 'BOX_UNAVAILABLE'
	| 'RECEIPT_DATE_REQUIRED';

/** Unsigned decimal text. Digits only, so `1e9`, `-5` and `1,000` are refused here. */
const DECIMAL_TEXT = /^\d{1,15}(\.\d{1,15})?$/;

/** A real `YYYY-MM-DD` date: the shape the server's `LocalDate` accepts. */
function isDate(value: string): boolean {
	return asCalendarDay(value) !== null;
}

export type LocalValidation = {
	costs: Map<number, LocalIssue[]>;
	receipts: Map<number, LocalIssue[]>;
	/** True when every row can be sent as typed. */
	ready: boolean;
};

export function validateDraft(
	draft: PlanningDraft,
	boxIds: Set<number> | null,
): LocalValidation {
	const costs = new Map<number, LocalIssue[]>();
	const receipts = new Map<number, LocalIssue[]>();

	for (const cost of draft.costs) {
		const issues: LocalIssue[] = [];
		const amount = cost.amount.trim();
		if (amount === '') issues.push('AMOUNT_REQUIRED');
		else if (!DECIMAL_TEXT.test(amount)) issues.push('AMOUNT_FORMAT');
		if (!isDate(cost.date)) issues.push('DATE_REQUIRED');
		if (cost.description.trim().length > MAX_COST_DESCRIPTION) issues.push('DESCRIPTION_TOO_LONG');
		if (cost.boxId !== '') {
			const boxAmount = cost.boxAmount.trim();
			if (boxAmount === '') issues.push('BOX_AMOUNT_REQUIRED');
			else if (!DECIMAL_TEXT.test(boxAmount)) issues.push('BOX_AMOUNT_FORMAT');
			// Like receipts, an unavailable catalog proves nothing: the chosen Box
			// is kept and sent, and the server reports `BOX_NOT_FOUND` if it is gone.
			if (boxIds !== null && !boxIds.has(Number(cost.boxId))) issues.push('BOX_UNAVAILABLE');
		}
		if (issues.length > 0) costs.set(cost.key, issues);
	}

	for (const receipt of draft.receipts) {
		if (!isDate(receipt.date)) receipts.set(receipt.key, ['RECEIPT_DATE_REQUIRED']);
	}

	return {
		costs,
		receipts,
		ready:
			costs.size === 0 &&
			receipts.size === 0 &&
			draft.costs.length <= MAX_PLANNING_COSTS &&
			draft.receipts.length <= MAX_PLANNING_RECEIPTS,
	};
}

/* ---------------------------------------------------------------- Request */

export type PlanningPreviewRequest = {
	horizonDays: number;
	essentialsReviewed: boolean;
	items: Array<{
		amount: string;
		date: string;
		description: string | null;
		boxId: number | null;
		boxAmount: string | null;
		notYetRecordedConfirmed: boolean;
	}>;
	expectedReceipts: Array<{ recordId: number; recordKind: ReceiptKind; date: string }>;
};

/**
 * The request body for a draft that passed `validateDraft`.
 *
 * Amounts travel as decimal text, exactly as typed, so the server — not a
 * binary float — decides whether a value has fractional cents. A receipt
 * carries identity and date only: the server reloads its amount.
 */
export function buildPreviewRequest(draft: PlanningDraft): PlanningPreviewRequest {
	return {
		horizonDays: PLANNING_HORIZON_DAYS,
		essentialsReviewed: draft.essentialsReviewed,
		items: draft.costs.map((cost) => {
			const description = cost.description.trim();
			const funded = cost.boxId !== '';
			return {
				amount: cost.amount.trim(),
				date: cost.date,
				description: description === '' ? null : description,
				boxId: funded ? Number(cost.boxId) : null,
				boxAmount: funded ? cost.boxAmount.trim() : null,
				notYetRecordedConfirmed: cost.notYetRecordedConfirmed,
			};
		}),
		expectedReceipts: draft.receipts.map((receipt) => ({
			recordId: receipt.recordId,
			recordKind: receipt.recordKind,
			date: receipt.date,
		})),
	};
}

/* --------------------------------------------------------------- Response */

export type ProjectionStatus = 'complete' | 'partial' | 'unavailable';
export type TimingStatus = 'complete' | 'partial' | 'unavailable' | 'notApplicable';

export type PreviewBaseline = {
	netBalance: number;
	inBoxes: number;
	availableToSpend: number;
	source: 'accounts' | 'transactions';
	/** Inside Net Balance and not cash; `null` before account tracking. */
	creditInFavor: number | null;
};

export type PreviewBoxProjection = {
	boxId: number;
	name: string;
	balance: number;
	projectedBalance: number;
};

export type PreviewProjected = {
	netBalance: number;
	inBoxes: number;
	availableToSpend: number;
	perBox: PreviewBoxProjection[];
};

export type PreviewMissingInput = {
	reason: string;
	itemIndex: number | null;
	receiptIndex: number | null;
	boxId: number | null;
	shortfall: number | null;
};

export type PreviewIncludedReceipt = {
	receiptIndex: number;
	recordKind: ReceiptKind;
	recordId: number;
	amount: number;
	date: CalendarDay;
};

export type PreviewStatementDue = {
	accountId: number;
	accountName: string;
	statementId: number;
	periodStart: CalendarDay | null;
	periodEnd: CalendarDay | null;
	dueDate: CalendarDay | null;
	officialBalance: number;
	paidAmount: number;
	outstandingBalance: number;
	minimumPayment: number | null;
	avoidInterest: number | null;
	reconciliationMismatch: boolean;
};

export type PreviewStatementEstimate = {
	accountId: number;
	accountName: string;
	periodStart: CalendarDay | null;
	periodEnd: CalendarDay | null;
	dueDate: CalendarDay | null;
	estimatedBalance: number;
};

export type PreviewTimingReason = {
	code: string;
	accountId: number | null;
	statementId: number | null;
};

export type PreviewTiming = {
	status: TimingStatus;
	dated: PreviewStatementDue[];
	overdue: PreviewStatementDue[];
	estimates: PreviewStatementEstimate[];
	reasons: PreviewTimingReason[];
};

export type PreviewUndatedDebt = {
	debtId: number;
	direction: 'INGRESS' | 'EGRESS';
	contactId: number | null;
	contactName: string | null;
	description: string | null;
	totalAmount: number;
	paidAmount: number;
	remaining: number;
};

export type PlanningPreviewResponse = {
	generatedAt: string;
	timeZone: string | null;
	window: PlanningWindow | null;
	baseline: PreviewBaseline | null;
	projected: PreviewProjected | null;
	status: ProjectionStatus;
	missingInputs: PreviewMissingInput[];
	includedReceipts: PreviewIncludedReceipt[];
	timing: PreviewTiming;
	/** `null` when the Debt read failed; `[]` means none are outstanding. */
	undatedDebts: PreviewUndatedDebt[] | null;
	notes: string[];
};

/**
 * The response fields this page reads, per record, in the backend's own
 * names. `tests/planning-preview.test.js` compares these against the Java
 * records so a renamed field fails a test instead of rendering as missing.
 */
export const PREVIEW_RESPONSE_FIELDS = {
	envelope: [
		'generatedAt',
		'timeZone',
		'window',
		'baseline',
		'projected',
		'status',
		'missingInputs',
		'includedReceipts',
		'timing',
		'undatedDebts',
		'undatedDebtsStatus',
		'notes',
	],
	window: ['from', 'to'],
	baseline: ['netBalance', 'inBoxes', 'availableToSpend', 'source', 'creditInFavor'],
	projected: ['netBalance', 'inBoxes', 'availableToSpend', 'perBox'],
	perBox: ['boxId', 'name', 'balance', 'projectedBalance'],
	missingInput: ['reason', 'itemIndex', 'receiptIndex', 'boxId', 'shortfall'],
	includedReceipt: ['receiptIndex', 'recordKind', 'recordId', 'amount', 'date'],
	timing: ['status', 'dated', 'overdue', 'estimates', 'reasons'],
	statementDue: [
		'accountId',
		'accountName',
		'statementId',
		'periodStart',
		'periodEnd',
		'dueDate',
		'officialBalance',
		'paidAmount',
		'outstandingBalance',
		'minimumPayment',
		'avoidInterest',
		'reconciliationMismatch',
	],
	statementEstimate: [
		'accountId',
		'accountName',
		'periodStart',
		'periodEnd',
		'dueDate',
		'estimatedBalance',
	],
	timingReason: ['code', 'accountId', 'statementId'],
	undatedDebt: [
		'debtId',
		'direction',
		'contactId',
		'contactName',
		'description',
		'totalAmount',
		'paidAmount',
		'remaining',
	],
} as const;

/** The request fields this page sends, for the same contract comparison. */
export const PREVIEW_REQUEST_FIELDS = {
	envelope: ['horizonDays', 'essentialsReviewed', 'items', 'expectedReceipts'],
	item: ['amount', 'date', 'description', 'boxId', 'boxAmount', 'notYetRecordedConfirmed'],
	expectedReceipt: ['recordId', 'recordKind', 'date'],
} as const;

type Json = Record<string, unknown>;

function isRecord(value: unknown): value is Json {
	return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function money(value: unknown): number | null {
	return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function integer(value: unknown): number | null {
	return typeof value === 'number' && Number.isInteger(value) ? value : null;
}

/** `null` for absent/null; `undefined` for present-but-unreadable (fails the record). */
function optional<T>(value: unknown, read: (value: unknown) => T | null): T | null | undefined {
	if (value === undefined || value === null) return null;
	return read(value) ?? undefined;
}

function text(value: unknown): string | null {
	return typeof value === 'string' ? value : null;
}

function list<T>(value: unknown, parse: (item: unknown) => T | null): T[] | null {
	if (!Array.isArray(value)) return null;
	const parsed: T[] = [];
	for (const item of value) {
		const one = parse(item);
		if (one === null) return null;
		parsed.push(one);
	}
	return parsed;
}

function parseWindow(value: unknown): PlanningWindow | null {
	if (!isRecord(value)) return null;
	const from = asCalendarDay(value.from);
	const to = asCalendarDay(value.to);
	return from === null || to === null ? null : { from, to };
}

function parseBaseline(value: unknown): PreviewBaseline | null {
	if (!isRecord(value)) return null;
	const netBalance = money(value.netBalance);
	const inBoxes = money(value.inBoxes);
	const availableToSpend = money(value.availableToSpend);
	const creditInFavor = optional(value.creditInFavor, money);
	const source = value.source;
	if (netBalance === null || inBoxes === null || availableToSpend === null) return null;
	if (creditInFavor === undefined || (source !== 'accounts' && source !== 'transactions')) return null;
	return { netBalance, inBoxes, availableToSpend, source, creditInFavor };
}

function parseBoxProjection(value: unknown): PreviewBoxProjection | null {
	if (!isRecord(value)) return null;
	const boxId = integer(value.boxId);
	const name = text(value.name);
	const balance = money(value.balance);
	const projectedBalance = money(value.projectedBalance);
	if (boxId === null || name === null || balance === null || projectedBalance === null) return null;
	return { boxId, name, balance, projectedBalance };
}

function parseProjected(value: unknown): PreviewProjected | null {
	if (!isRecord(value)) return null;
	const netBalance = money(value.netBalance);
	const inBoxes = money(value.inBoxes);
	const availableToSpend = money(value.availableToSpend);
	const perBox = list(value.perBox, parseBoxProjection);
	if (netBalance === null || inBoxes === null || availableToSpend === null || perBox === null) {
		return null;
	}
	return { netBalance, inBoxes, availableToSpend, perBox };
}

function parseMissingInput(value: unknown): PreviewMissingInput | null {
	if (!isRecord(value)) return null;
	const reason = text(value.reason);
	const itemIndex = optional(value.itemIndex, integer);
	const receiptIndex = optional(value.receiptIndex, integer);
	const boxId = optional(value.boxId, integer);
	const shortfall = optional(value.shortfall, money);
	if (reason === null) return null;
	if (itemIndex === undefined || receiptIndex === undefined) return null;
	if (boxId === undefined || shortfall === undefined) return null;
	return { reason, itemIndex, receiptIndex, boxId, shortfall };
}

function parseReceiptKind(value: unknown): ReceiptKind | null {
	return value === 'DEBT' || value === 'PAYMENT_RECORD' ? value : null;
}

function parseIncludedReceipt(value: unknown): PreviewIncludedReceipt | null {
	if (!isRecord(value)) return null;
	const receiptIndex = integer(value.receiptIndex);
	const recordKind = parseReceiptKind(value.recordKind);
	const recordId = integer(value.recordId);
	const amount = money(value.amount);
	const date = asCalendarDay(value.date);
	if (receiptIndex === null || recordKind === null || recordId === null) return null;
	if (amount === null || date === null) return null;
	return { receiptIndex, recordKind, recordId, amount, date };
}

function parseStatementDue(value: unknown): PreviewStatementDue | null {
	if (!isRecord(value)) return null;
	const accountId = integer(value.accountId);
	const accountName = text(value.accountName);
	const statementId = integer(value.statementId);
	const officialBalance = money(value.officialBalance);
	const paidAmount = money(value.paidAmount);
	const outstandingBalance = money(value.outstandingBalance);
	const minimumPayment = optional(value.minimumPayment, money);
	const avoidInterest = optional(value.avoidInterest, money);
	if (accountId === null || accountName === null || statementId === null) return null;
	if (officialBalance === null || paidAmount === null || outstandingBalance === null) return null;
	if (minimumPayment === undefined || avoidInterest === undefined) return null;
	if (typeof value.reconciliationMismatch !== 'boolean') return null;
	return {
		accountId,
		accountName,
		statementId,
		// An unusable date changes a row's label, not whether the amount is owed.
		periodStart: asCalendarDay(value.periodStart),
		periodEnd: asCalendarDay(value.periodEnd),
		dueDate: asCalendarDay(value.dueDate),
		officialBalance,
		paidAmount,
		outstandingBalance,
		minimumPayment,
		avoidInterest,
		reconciliationMismatch: value.reconciliationMismatch,
	};
}

function parseStatementEstimate(value: unknown): PreviewStatementEstimate | null {
	if (!isRecord(value)) return null;
	const accountId = integer(value.accountId);
	const accountName = text(value.accountName);
	const estimatedBalance = money(value.estimatedBalance);
	if (accountId === null || accountName === null || estimatedBalance === null) return null;
	return {
		accountId,
		accountName,
		periodStart: asCalendarDay(value.periodStart),
		periodEnd: asCalendarDay(value.periodEnd),
		dueDate: asCalendarDay(value.dueDate),
		estimatedBalance,
	};
}

function parseTimingReason(value: unknown): PreviewTimingReason | null {
	if (!isRecord(value)) return null;
	const code = text(value.code);
	const accountId = optional(value.accountId, integer);
	const statementId = optional(value.statementId, integer);
	if (code === null || accountId === undefined || statementId === undefined) return null;
	return { code, accountId, statementId };
}

const TIMING_STATUSES = new Set<TimingStatus>(['complete', 'partial', 'unavailable', 'notApplicable']);

function parseTiming(value: unknown): PreviewTiming | null {
	if (!isRecord(value)) return null;
	const status = value.status as TimingStatus;
	const dated = list(value.dated, parseStatementDue);
	const overdue = list(value.overdue, parseStatementDue);
	const estimates = list(value.estimates, parseStatementEstimate);
	const reasons = list(value.reasons, parseTimingReason);
	if (!TIMING_STATUSES.has(status)) return null;
	if (dated === null || overdue === null || estimates === null || reasons === null) return null;
	return { status, dated, overdue, estimates, reasons };
}

function parseUndatedDebt(value: unknown): PreviewUndatedDebt | null {
	if (!isRecord(value)) return null;
	const debtId = integer(value.debtId);
	const direction = value.direction;
	const contactId = optional(value.contactId, integer);
	const contactName = optional(value.contactName, text);
	const description = optional(value.description, text);
	const totalAmount = money(value.totalAmount);
	const paidAmount = money(value.paidAmount);
	const remaining = money(value.remaining);
	if (debtId === null || (direction !== 'INGRESS' && direction !== 'EGRESS')) return null;
	if (contactId === undefined || contactName === undefined || description === undefined) return null;
	if (totalAmount === null || paidAmount === null || remaining === null) return null;
	return { debtId, direction, contactId, contactName, description, totalAmount, paidAmount, remaining };
}

const PROJECTION_STATUSES = new Set<ProjectionStatus>(['complete', 'partial', 'unavailable']);

/**
 * Validates a 200 preview envelope.
 *
 * Anything unreadable fails the whole response rather than one field
 * degrading to zero or to "none". Three contract invariants are checked, not
 * trusted: an unavailable projection is `null` and an available one is not
 * (and has its baseline), and undated Debts are `null` exactly when the server
 * says they are unavailable.
 */
export function parsePreviewResponse(value: unknown): PlanningPreviewResponse | null {
	if (!isRecord(value)) return null;
	const generatedAt = text(value.generatedAt);
	if (generatedAt === null || Number.isNaN(Date.parse(generatedAt))) return null;

	const timeZone = optional(value.timeZone, text);
	const window = optional(value.window, parseWindow);
	const baseline = optional(value.baseline, parseBaseline);
	const projected = optional(value.projected, parseProjected);
	const status = value.status as ProjectionStatus;
	const missingInputs = list(value.missingInputs, parseMissingInput);
	const includedReceipts = list(value.includedReceipts, parseIncludedReceipt);
	const timing = parseTiming(value.timing);
	const notes = list(value.notes, text);
	if (timeZone === undefined || window === undefined) return null;
	if (baseline === undefined || projected === undefined) return null;
	if (!PROJECTION_STATUSES.has(status)) return null;
	if (missingInputs === null || includedReceipts === null || timing === null || notes === null) {
		return null;
	}
	if ((status === 'unavailable') !== (projected === null)) return null;
	// A projection is always relative to a baseline; one without it could not be
	// shown as a comparison and is not a response this contract produces.
	if (projected !== null && baseline === null) return null;

	let undatedDebts: PreviewUndatedDebt[] | null;
	if (value.undatedDebtsStatus === 'unavailable') {
		if (value.undatedDebts !== null && value.undatedDebts !== undefined) return null;
		undatedDebts = null;
	} else if (value.undatedDebtsStatus === 'available') {
		undatedDebts = list(value.undatedDebts, parseUndatedDebt);
		if (undatedDebts === null) return null;
	} else {
		return null;
	}

	return {
		generatedAt,
		timeZone,
		window,
		baseline,
		projected,
		status,
		missingInputs,
		includedReceipts,
		timing,
		undatedDebts,
		notes,
	};
}

/* ------------------------------------------------------- Missing inputs */

export type GroupedMissingInputs = {
	/** Keyed by zero-based request row, which is the draft's row order. */
	byCost: Map<number, PreviewMissingInput[]>;
	byReceipt: Map<number, PreviewMissingInput[]>;
	/** Box-level (capacity) and whole-form reasons. */
	general: PreviewMissingInput[];
};

export function groupMissingInputs(missing: PreviewMissingInput[]): GroupedMissingInputs {
	const byCost = new Map<number, PreviewMissingInput[]>();
	const byReceipt = new Map<number, PreviewMissingInput[]>();
	const general: PreviewMissingInput[] = [];
	for (const input of missing) {
		if (input.itemIndex !== null) {
			byCost.set(input.itemIndex, [...(byCost.get(input.itemIndex) ?? []), input]);
		} else if (input.receiptIndex !== null) {
			byReceipt.set(input.receiptIndex, [...(byReceipt.get(input.receiptIndex) ?? []), input]);
		} else {
			general.push(input);
		}
	}
	return { byCost, byReceipt, general };
}

/* ------------------------------------------------------------ Row fields */

/** The inputs of one cost row, as named in their element ids. */
export type CostField = 'amount' | 'date' | 'description' | 'box' | 'box-amount' | 'confirm';

export const LOCAL_ISSUE_FIELD: Record<LocalIssue, CostField> = {
	AMOUNT_REQUIRED: 'amount',
	AMOUNT_FORMAT: 'amount',
	DATE_REQUIRED: 'date',
	DESCRIPTION_TOO_LONG: 'description',
	BOX_AMOUNT_REQUIRED: 'box-amount',
	BOX_AMOUNT_FORMAT: 'box-amount',
	BOX_UNAVAILABLE: 'box',
	RECEIPT_DATE_REQUIRED: 'date',
};

/** The cost input a server reason is about; `null` when it names no one field. */
export function serverCostField(reason: string): CostField | null {
	switch (reason) {
		case 'INVALID_AMOUNT':
			return 'amount';
		case 'DATE_OUT_OF_WINDOW':
			return 'date';
		case 'INVALID_FUNDING':
		case 'FUNDING_EXCEEDS_COST':
		case 'BOX_REQUIRED':
			return 'box-amount';
		case 'BOX_NOT_FOUND':
			return 'box';
		case 'COST_NOT_CONFIRMED_UNRECORDED':
			return 'confirm';
		default:
			return null;
	}
}

/**
 * The inputs of one cost row that are actually wrong, so each can carry its
 * own `aria-invalid` instead of the whole row being marked. A Box-amount
 * problem lands on the Box select while no Box is chosen, because the amount
 * input exists only with a Box.
 */
export function invalidCostFields(
	cost: Pick<CostDraft, 'boxId'>,
	local: readonly LocalIssue[],
	server: readonly Pick<PreviewMissingInput, 'reason'>[],
): Set<CostField> {
	const fields = new Set<CostField>();
	const fieldOf = (field: CostField): CostField =>
		field === 'box-amount' && cost.boxId === '' ? 'box' : field;
	for (const issue of local) fields.add(fieldOf(LOCAL_ISSUE_FIELD[issue]));
	for (const input of server) {
		const field = serverCostField(input.reason);
		if (field !== null) fields.add(fieldOf(field));
	}
	return fields;
}

/* ------------------------------------------------------ Request lifecycle */

export type PreviewFailure =
	/** The request never completed. */
	| 'unreachable'
	/** 401/403: the session ended. */
	| 'auth'
	/** Another 4xx: the server refused the request's shape. */
	| 'rejected'
	/** 5xx, including the proxy's 502/504. */
	| 'server'
	/** A 200 whose body failed validation. */
	| 'invalid';

/** Why the last result was withdrawn, so the page can say so. */
export type ClearReason = 'edited' | 'returned' | 'refreshed';

export type PreviewState =
	| { kind: 'idle'; cleared: ClearReason | null }
	| { kind: 'loading' }
	| {
			kind: 'result';
			response: PlanningPreviewResponse;
			/** The draft rows the response indexes into, frozen at submission. */
			submitted: PlanningDraft;
	  }
	| { kind: 'failed'; failure: PreviewFailure };

export type PreviewFetch = (input: string, init: RequestInit) => Promise<Response>;

/**
 * Owns the one in-flight preview and decides which response may be shown.
 *
 * Every `clear` advances a generation and aborts the outstanding request. A
 * response is accepted only when it belongs to the latest `calculate` call
 * *and* nothing was cleared since it began, so a slow older response can never
 * reappear over newer inputs — even if the transport ignores the abort.
 */
export class PlanningPreviewSession {
	#state: PreviewState = { kind: 'idle', cleared: null };
	#generation = 0;
	#latest = 0;
	#controller: AbortController | null = null;
	#listeners = new Set<(state: PreviewState) => void>();

	get state(): PreviewState {
		return this.#state;
	}

	subscribe(listener: (state: PreviewState) => void): () => void {
		this.#listeners.add(listener);
		listener(this.#state);
		return () => this.#listeners.delete(listener);
	}

	#set(state: PreviewState) {
		this.#state = state;
		for (const listener of this.#listeners) listener(state);
	}

	/**
	 * Withdraws any result and cancels any request.
	 *
	 * Always advances the generation, even when nothing is shown, because a
	 * request may be in flight. A plain edit never replaces a more specific
	 * notice already shown, and an edit after a failure simply dismisses it.
	 */
	clear(reason: ClearReason) {
		this.#generation += 1;
		this.#controller?.abort();
		this.#controller = null;
		const current = this.#state;
		if (current.kind === 'idle') {
			if (reason !== 'edited') this.#set({ kind: 'idle', cleared: reason });
			return;
		}
		const dismissedFailure = current.kind === 'failed' && reason === 'edited';
		this.#set({ kind: 'idle', cleared: dismissedFailure ? null : reason });
	}

	/**
	 * Requests a fresh preview for `draft`. Resolves once this call's outcome
	 * is known; returns whether its response was the one applied.
	 */
	async calculate(draft: PlanningDraft, fetchFn: PreviewFetch): Promise<boolean> {
		this.#controller?.abort();
		const controller = new AbortController();
		this.#controller = controller;
		const generation = this.#generation;
		const ticket = ++this.#latest;
		// A plain copy, not `structuredClone`: the page's draft may be a reactive
		// proxy, and the rows must not change under a response that indexes them.
		const submitted: PlanningDraft = {
			...draft,
			costs: draft.costs.map((cost) => ({ ...cost })),
			receipts: draft.receipts.map((receipt) => ({ ...receipt })),
		};
		const current = () => ticket === this.#latest && generation === this.#generation;
		this.#set({ kind: 'loading' });

		let response: Response;
		try {
			response = await fetchFn('/api/planning/preview', {
				method: 'POST',
				headers: { 'content-type': 'application/json', accept: 'application/json' },
				body: JSON.stringify(buildPreviewRequest(submitted)),
				cache: 'no-store',
				signal: controller.signal,
			});
		} catch {
			if (!current()) return false;
			this.#finish({ kind: 'failed', failure: 'unreachable' });
			return true;
		}

		let body: unknown = null;
		let readable = true;
		try {
			body = await response.json();
		} catch {
			readable = false;
		}
		if (!current()) return false;

		if (!response.ok) {
			const failure: PreviewFailure =
				response.status === 401 || response.status === 403
					? 'auth'
					: response.status >= 500
						? 'server'
						: 'rejected';
			this.#finish({ kind: 'failed', failure });
			return true;
		}
		const parsed = readable ? parsePreviewResponse(body) : null;
		this.#finish(
			parsed === null ? { kind: 'failed', failure: 'invalid' } : { kind: 'result', response: parsed, submitted },
		);
		return true;
	}

	#finish(state: PreviewState) {
		this.#controller = null;
		this.#set(state);
	}
}

/**
 * What happens when the page may have fallen behind the ledger: the tab was
 * hidden (the User may have recorded a Transaction elsewhere) or the User
 * asked to refresh. Any result or in-flight request is withdrawn and every
 * confirmation reset, so the next calculation needs them again. Typed values
 * are kept. Returns the draft to use from now on.
 *
 * A hidden tab with nothing on screen and nothing typed shows no notice; it
 * still cancels anything in flight.
 */
export function withdrawForStaleness(
	session: PlanningPreviewSession,
	draft: PlanningDraft,
	reason: 'returned' | 'refreshed',
): PlanningDraft {
	const somethingShown = session.state.kind !== 'idle' || hasDraftContent(draft);
	session.clear(reason === 'returned' && !somethingShown ? 'edited' : reason);
	return resetConfirmations(draft);
}
