/**
 * Shared obligation-status derivation (Slice 1D, decision D2).
 *
 * Three different facts live here and stay apart: generating billing, receiving
 * a Subscription Member's contribution, and paying a confirmed Credit
 * Statement. Compressing them into one "paid/unpaid" badge would state
 * something about the User's money that no single input supports.
 *
 * Every function is pure and takes its calendar inputs explicitly. Nothing here
 * reads the ambient clock, the browser's zone, or `Date.parse` on a date-only
 * string: `new Date('2026-09-07')` is UTC midnight, which is 6 September in
 * Mexico City, so parsing a calendar date into an instant is how a due date
 * silently becomes "past due" a day early. Dates are compared as text, which
 * for zero-padded `YYYY-MM-DD` is the same ordering as the calendar.
 *
 * A missing or malformed date, zone, amount, or status is never guessed: it
 * produces an `unavailable` state whose presentation is Retry/review, not an
 * overdue label.
 */

/** An ISO calendar date, `YYYY-MM-DD`. */
export type CalendarDay = string;

export type DayResolution =
	| { status: 'ok'; day: CalendarDay }
	| { status: 'unavailable'; reason: 'zone' | 'date' };

const CALENDAR_DAY = /^(\d{4})-(\d{2})-(\d{2})$/;

/**
 * Validates a calendar date.
 *
 * The shape alone is not enough: `2026-13-45` matches the pattern, and
 * JavaScript would roll it over into a real but wrong day rather than refuse
 * it.
 */
export function asCalendarDay(value: unknown): CalendarDay | null {
	if (typeof value !== 'string') return null;
	const match = CALENDAR_DAY.exec(value);
	if (match === null) return null;

	const [, year, month, day] = match.map(Number);
	const date = new Date(Date.UTC(year, month - 1, day));
	const real =
		date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
	return real ? value : null;
}

/**
 * The User's calendar day for one captured instant.
 *
 * Both inputs are required and explicit: the same instant is 7 September in
 * America/Mexico_City and 8 September in Asia/Tokyo, so "today" is only
 * reproducible when the zone travels with the instant. An unusable zone is
 * reported rather than silently replaced with UTC or the server's zone, either
 * of which would answer with a different day.
 */
export function userToday(timeZone: unknown, instant: Date): DayResolution {
	if (typeof timeZone !== 'string' || timeZone.length === 0) {
		return { status: 'unavailable', reason: 'zone' };
	}
	if (!(instant instanceof Date) || Number.isNaN(instant.getTime())) {
		return { status: 'unavailable', reason: 'date' };
	}

	let parts: Intl.DateTimeFormatPart[];
	try {
		parts = new Intl.DateTimeFormat('en-US', {
			timeZone,
			year: 'numeric',
			month: '2-digit',
			day: '2-digit',
		}).formatToParts(instant);
	} catch {
		return { status: 'unavailable', reason: 'zone' };
	}

	const found = new Map(parts.map((part) => [part.type, part.value]));
	const day = asCalendarDay(`${found.get('year')}-${found.get('month')}-${found.get('day')}`);
	return day === null ? { status: 'unavailable', reason: 'zone' } : { status: 'ok', day };
}

/**
 * Accepts either a resolved day or a raw value, so callers can pass either.
 *
 * A resolution that says `ok` is still validated rather than trusted: callers
 * may hand-build one, and a malformed day arriving through that shape would
 * otherwise skip the check every other input goes through and be compared as
 * text against a real due date.
 */
function today(value: unknown): CalendarDay | null {
	if (typeof value === 'object' && value !== null && 'status' in value) {
		const resolution = value as DayResolution;
		return resolution.status === 'ok' ? asCalendarDay(resolution.day) : null;
	}
	return asCalendarDay(value);
}

/**
 * Calendar-date comparison, or `null` when either side is unusable.
 *
 * `null` is deliberately not `0`: "we cannot tell" and "the same day" lead to
 * different statuses.
 */
export function compareDays(left: unknown, right: unknown): -1 | 0 | 1 | null {
	const a = asCalendarDay(left);
	const b = asCalendarDay(right);
	if (a === null || b === null) return null;
	return a < b ? -1 : a > b ? 1 : 0;
}

/* -------------------------------------------------------------------------
 * Truth table A — billing generation
 * ---------------------------------------------------------------------- */

export type BillingGenerationState =
	/** Subscription read, date, or zone unavailable. */
	| 'unavailable'
	/** The generation cursor is before today. */
	| 'pending'
	/** The generation cursor is today. */
	| 'due-today'
	/** The cursor is in the future; nothing to generate. */
	| 'caught-up';

export type BillingGeneration = {
	state: BillingGenerationState;
	/** The cursor itself, when it is readable. Never a substituted date. */
	scheduledDate: CalendarDay | null;
};

/**
 * Derives billing-generation state from the cursor and the User's today.
 *
 * `nextBillingDate` is a generation cursor. It is not evidence that a provider
 * charged anything, that a Subscription Member owes anything, or that a
 * payment is late — only that Keenti has not yet written Payment Records up to
 * that date.
 */
export function billingGenerationStatus(input: {
	nextBillingDate: unknown;
	today: unknown;
	/** Pass `false` when the Subscription itself could not be read. */
	subscriptionAvailable?: boolean;
}): BillingGeneration {
	const cursor = asCalendarDay(input.nextBillingDate);
	const now = today(input.today);
	if (input.subscriptionAvailable === false || cursor === null || now === null) {
		return { state: 'unavailable', scheduledDate: cursor };
	}

	if (cursor < now) return { state: 'pending', scheduledDate: cursor };
	if (cursor === now) return { state: 'due-today', scheduledDate: cursor };
	return { state: 'caught-up', scheduledDate: cursor };
}

/* -------------------------------------------------------------------------
 * Truth table B — generated contribution records
 * ---------------------------------------------------------------------- */

export type ContributionState =
	/** Section unreadable, amount invalid, or status not one we know. */
	| 'unavailable'
	/** `PAID`: the contribution was received. */
	| 'received'
	/** `PENDING`: still awaiting the Member's contribution. */
	| 'awaiting';

export type Contribution = {
	state: ContributionState;
	amount: number | null;
	billingDate: CalendarDay | null;
	paidDate: CalendarDay | null;
	/** The linked INGRESS Transaction, when the record names one. */
	linkedTransactionId: number | null;
};

/**
 * Derives one Payment Record's contribution state.
 *
 * A `PENDING` record whose billing date has passed stays *awaiting*, not
 * overdue: no contribution due date or grace period is agreed anywhere in the
 * contract, so "late" would be an invented policy. A `PAID` record with no
 * linked Transaction is still received — a missing link is a missing link.
 *
 * A negative amount is an invalid amount, not a contribution owed backwards:
 * showing it as "awaiting −$120" would state a receipt nobody expects.
 */
export function contributionStatus(input: {
	status: unknown;
	amount: unknown;
	billingDate?: unknown;
	paidDate?: unknown;
	transactionId?: number | null;
	/** Pass `false` when the payment section itself could not be read. */
	sectionAvailable?: boolean;
}): Contribution {
	const amount =
		typeof input.amount === 'number' && Number.isFinite(input.amount) && input.amount >= 0
			? input.amount
			: null;
	const billingDate = asCalendarDay(input.billingDate);
	const paidDate = asCalendarDay(input.paidDate);
	const linkedTransactionId = typeof input.transactionId === 'number' ? input.transactionId : null;
	const base = { amount, billingDate, paidDate, linkedTransactionId };

	if (input.sectionAvailable === false || amount === null) return { ...base, state: 'unavailable' };
	if (input.status === 'PAID') return { ...base, state: 'received' };
	if (input.status === 'PENDING') return { ...base, state: 'awaiting' };
	return { ...base, state: 'unavailable' };
}

export type ContributionSectionState =
	| 'unavailable'
	/** The section loaded and this period genuinely has no records. */
	| 'no-records'
	| 'has-records';

/** Distinguishes "no Payment Records for this period" from "we could not look". */
export function contributionSectionStatus(records: unknown[] | null): ContributionSectionState {
	if (records === null) return 'unavailable';
	return records.length === 0 ? 'no-records' : 'has-records';
}

/* -------------------------------------------------------------------------
 * Truth table C — confirmed statement payment
 * ---------------------------------------------------------------------- */

export type StatementPaymentState =
	/** Read failed, confirmation metadata malformed, or amount unreadable. */
	| 'unavailable'
	/** The list loaded; nothing is confirmed, only estimated. */
	| 'estimated-only'
	/** Confirmed and `outstandingBalance <= 0`. */
	| 'covered'
	/** Confirmed and outstanding, but the due date is unusable. */
	| 'outstanding-due-unknown'
	| 'outstanding-past-due'
	| 'outstanding-due-today'
	| 'outstanding-upcoming';

export type StatementPayment = {
	state: StatementPaymentState;
	/** `outstandingBalance`, i.e. official balance minus allocated payments. */
	outstanding: number | null;
	dueDate: CalendarDay | null;
	/** Independent review notice; never replaces the payment state. */
	reconciliationMismatch: boolean;
	/** How far recorded activity and the confirmed snapshot differ, when flagged. */
	mismatchAmount: number | null;
};

/**
 * Derives a confirmed Credit Statement's payment state.
 *
 * The figure is the backend's `outstandingBalance` — never the current signed
 * credit balance, the available credit, the minimum payment, or the
 * avoid-interest amount. Those are separate labeled facts, and a positive
 * current credit balance can coexist with an unpaid historical statement.
 *
 * "Past due date" describes the remaining confirmed balance and its date. It
 * does not assert bank delinquency, interest, fees, or whether a minimum
 * payment satisfied the bank.
 */
export function statementPaymentStatus(input: {
	outstandingBalance: unknown;
	dueDate: unknown;
	today: unknown;
	reconciliationMismatch?: unknown;
	mismatchAmount?: unknown;
	/** Pass `false` when the statement read failed or its metadata is malformed. */
	statementAvailable?: boolean;
}): StatementPayment {
	const outstanding =
		typeof input.outstandingBalance === 'number' && Number.isFinite(input.outstandingBalance)
			? input.outstandingBalance
			: null;
	const dueDate = asCalendarDay(input.dueDate);
	const reconciliationMismatch = input.reconciliationMismatch === true;
	const mismatchAmount =
		typeof input.mismatchAmount === 'number' && Number.isFinite(input.mismatchAmount)
			? input.mismatchAmount
			: null;
	const base = { outstanding, dueDate, reconciliationMismatch, mismatchAmount };

	if (input.statementAvailable === false || outstanding === null) {
		return { ...base, state: 'unavailable' };
	}
	if (outstanding <= 0) return { ...base, state: 'covered' };

	const order = compareDays(dueDate, today(input.today));
	if (order === null) return { ...base, state: 'outstanding-due-unknown' };
	if (order < 0) return { ...base, state: 'outstanding-past-due' };
	if (order === 0) return { ...base, state: 'outstanding-due-today' };
	return { ...base, state: 'outstanding-upcoming' };
}

/** A statement whose recorded activity disagrees with the bank's snapshot. */
export type StatementMismatch<T> = { statement: T; status: StatementPayment };

export type AccountStatementPayment<T> = {
	/** The one statement that most needs attention, per truth table C. */
	status: StatementPayment;
	statement: T | null;
	/**
	 * Every flagged statement, not just the selected one.
	 *
	 * `reconciliationMismatch` is an independent review notice (decision D2):
	 * it is not a payment state, so it must survive both a `covered` status and
	 * the priority selection above. A card whose past-due statement is chosen
	 * for the attention slot can still have a mismatch on an older, fully paid
	 * statement, and dropping it would silently retract a review the User was
	 * already told about.
	 */
	mismatches: StatementMismatch<T>[];
};

function emptyStatus(state: StatementPaymentState): StatementPayment {
	return {
		state,
		outstanding: null,
		dueDate: null,
		reconciliationMismatch: false,
		mismatchAmount: null,
	};
}

/**
 * The statement state for a whole Credit Financial Account.
 *
 * `statements === null` means the confirmed-statement list could not be read.
 * An empty list that loaded successfully is a fact — nothing is confirmed — but
 * "estimated statement, not confirmed" is a claim about the *estimate*, which
 * is a separate read. Decision D2 only reaches that row when that read also
 * succeeded, so `estimateAvailable` must say so; otherwise nothing is known and
 * the state is unavailable rather than a reassurance built on a failed request.
 */
export function accountStatementPaymentStatus<T>(input: {
	statements: T[] | null;
	today: unknown;
	read: (statement: T) => {
		outstandingBalance: unknown;
		dueDate: unknown;
		reconciliationMismatch?: unknown;
		mismatchAmount?: unknown;
	};
	/** Pass `false` when the separate current-estimate read failed. */
	estimateAvailable?: boolean;
}): AccountStatementPayment<T> {
	if (input.statements === null) {
		return { status: emptyStatus('unavailable'), statement: null, mismatches: [] };
	}

	const evaluated = input.statements.map((statement) => ({
		statement,
		status: statementPaymentStatus({ ...input.read(statement), today: input.today }),
	}));
	const mismatches = evaluated.filter((entry) => entry.status.reconciliationMismatch);

	const attention = evaluated
		.filter((entry) => entry.status.state !== 'covered')
		.sort((left, right) => comparePriority(left, right));
	const selected = attention[0] ?? evaluated[0] ?? null;
	if (selected !== null) {
		return { status: selected.status, statement: selected.statement, mismatches };
	}

	return {
		status: emptyStatus(input.estimateAvailable === true ? 'estimated-only' : 'unavailable'),
		statement: null,
		mismatches,
	};
}

/* -------------------------------------------------------------------------
 * Combined presentation
 * ---------------------------------------------------------------------- */

/**
 * Attention order for a single slot: unavailable essential data first, then
 * past-due confirmed statements, due-today statements, pending generation,
 * then future obligations. Lower sorts earlier.
 */
export function obligationPriority(
	state: StatementPaymentState | BillingGenerationState | ContributionState,
): number {
	switch (state) {
		case 'unavailable':
			return 0;
		case 'outstanding-past-due':
			return 1;
		case 'outstanding-due-today':
			return 2;
		case 'outstanding-due-unknown':
			return 3;
		case 'pending':
			return 4;
		case 'due-today':
			return 5;
		case 'outstanding-upcoming':
			return 6;
		case 'awaiting':
			return 7;
		default:
			return 8;
	}
}

/** Within equal priority, earlier due date wins; ties break on a stable key. */
function comparePriority<T>(
	left: { status: StatementPayment; statement: T },
	right: { status: StatementPayment; statement: T },
): number {
	const byPriority = obligationPriority(left.status.state) - obligationPriority(right.status.state);
	if (byPriority !== 0) return byPriority;

	const leftDue = left.status.dueDate ?? '';
	const rightDue = right.status.dueDate ?? '';
	if (leftDue !== rightDue) return leftDue < rightDue ? -1 : 1;

	const leftId = (left.statement as { id?: number }).id ?? 0;
	const rightId = (right.statement as { id?: number }).id ?? 0;
	return leftId - rightId;
}

/** True when the state describes a confirmed statement still owing money. */
export function isOutstandingStatement(state: StatementPaymentState): boolean {
	return (
		state === 'outstanding-past-due' ||
		state === 'outstanding-due-today' ||
		state === 'outstanding-upcoming' ||
		state === 'outstanding-due-unknown'
	);
}
