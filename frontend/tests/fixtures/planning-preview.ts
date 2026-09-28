/**
 * A fixture stand-in for `POST /api/planning/preview` (D5, slice 5C).
 *
 * The preview cannot be a static body: its window depends on the clock and
 * its result on the submitted rows. This module answers it from a scenario's
 * declared server state, following the Java service's contract — structural
 * 400s, business reasons in a 200 envelope, independent timing and undated
 * Debt sections, and the same reason and note codes — so the page can be
 * driven in a browser and in tests without a real backend.
 *
 * It is test code, not a second calculator for the app: the page never
 * imports it. The authoritative arithmetic is `PlanningPreviewCalculator`
 * (backend), and `tests/planning-fixtures.test.js` pins this stand-in to the
 * approved D5 worked examples so the two cannot quietly disagree on them.
 *
 * Amounts are handled as integer cents (BigInt), never binary floats.
 */

import { userToday } from '../../src/lib/obligation-status';
import { addCalendarDays } from '../../src/lib/planning-preview';

export const PLANNING_PREVIEW_ROUTE_MARKER = 'fixturePlanningPreview';

export type FixtureReceiptKind = 'DEBT' | 'PAYMENT_RECORD';

export type FixtureStatement = {
	accountId: number;
	accountName: string;
	statementId: number;
	/** Due date as an offset from the User's today; `null` is a missing due date. */
	dueInDays: number | null;
	periodEndInDays: number;
	officialBalance: number;
	paidAmount: number;
	minimumPayment: number | null;
	avoidInterest: number | null;
	reconciliationMismatch?: boolean;
};

export type FixtureEstimate = {
	accountId: number;
	accountName: string;
	periodEndInDays: number;
	dueInDays: number | null;
	estimatedBalance: number;
};

export type FixtureUndatedDebt = {
	debtId: number;
	direction: 'INGRESS' | 'EGRESS';
	contactId: number | null;
	contactName: string | null;
	description: string | null;
	totalAmount: number;
	paidAmount: number;
	remaining: number;
};

export type PlanningPreviewSpec = {
	/** The User's zone as the server resolves it; `null` is an unusable zone. */
	timeZone: string | null;
	/** `null`: the projection snapshot could not be read. */
	baseline: {
		trackingActive: boolean;
		netBalance: number;
		boxes: Array<{ id: number; name: string; balance: number }>;
		creditInFavor: number | null;
	} | null;
	/**
	 * Server-side receipt state, keyed `KIND:id`. Absent keys are unknown,
	 * trashed or another User's (`RECEIPT_NOT_FOUND`). This may deliberately
	 * disagree with a scenario's catalog GETs to model a change between reads.
	 */
	receipts: Record<string, { eligible: true; amount: number } | { eligible: false }>;
	receiptsReadable?: boolean;
	timing:
		| { kind: 'notApplicable' }
		| { kind: 'unavailable' }
		| {
				kind: 'data';
				statements: FixtureStatement[];
				estimates?: FixtureEstimate[];
				/** Extra partial reasons, e.g. UNCONFIRMED_STATEMENT or STATEMENT_SCHEDULE_MISSING. */
				reasons?: Array<{ code: string; accountId: number | null; statementId: number | null }>;
		  };
	/** `null`: the undated Debt read failed. */
	undatedDebts: FixtureUndatedDebt[] | null;
	planEvaluationIncomplete?: boolean;
};

export function planningPreviewRoute(spec: PlanningPreviewSpec) {
	return { [PLANNING_PREVIEW_ROUTE_MARKER]: spec };
}

export function isPlanningPreviewRoute(
	body: unknown,
): body is { [PLANNING_PREVIEW_ROUTE_MARKER]: PlanningPreviewSpec } {
	return typeof body === 'object' && body !== null && PLANNING_PREVIEW_ROUTE_MARKER in body;
}

export type FixtureResponse = { status: number; body: unknown };

const MAX_CENTS = 999_999_999n;
const MAX_ROWS = 50;

class Structural extends Error {}

/**
 * Decimal text to cents. `'fraction'` has more than two decimals; `'unbounded'`
 * is exponent notation, which Jackson accepts but no bounded amount uses.
 * Anything else that is not a number is structural (a 400), like the backend.
 */
function cents(raw: unknown): bigint | 'fraction' | 'unbounded' {
	if (typeof raw !== 'string' && typeof raw !== 'number') throw new Structural('amount');
	const text = String(raw).trim();
	const plain = /^([+-]?)(\d+)(?:\.(\d+))?$/.exec(text);
	if (plain === null) {
		if (/^[+-]?(\d+\.?\d*|\.\d+)[eE][+-]?\d+$/.test(text)) return 'unbounded';
		throw new Structural('amount');
	}
	const [, sign, whole, fractionRaw = ''] = plain;
	const fraction = fractionRaw.replace(/0+$/, '');
	if (fraction.length > 2) return 'fraction';
	const value = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
	return sign === '-' ? -value : value;
}

function validAmount(value: bigint | 'fraction' | 'unbounded'): value is bigint {
	return typeof value === 'bigint' && value > 0n && value <= MAX_CENTS;
}

function toCents(amount: number): bigint {
	return BigInt(Math.round(amount * 100));
}

function fromCents(value: bigint): number {
	return Number(value) / 100;
}

function isoDate(raw: unknown): string {
	if (typeof raw !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(raw)) throw new Structural('date');
	const [year, month, day] = raw.split('-').map(Number);
	const date = new Date(Date.UTC(year, month - 1, day));
	if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) {
		throw new Structural('date');
	}
	return raw;
}

type Item = {
	amount: bigint | 'fraction' | 'unbounded';
	date: string;
	boxId: number | null;
	boxAmount: bigint | 'fraction' | 'unbounded' | null;
	confirmed: boolean;
};
type Selection = { kind: FixtureReceiptKind; id: number; date: string };

function parseRequest(text: string): { essentialsReviewed: boolean; items: Item[]; receipts: Selection[] } {
	let body: Record<string, unknown>;
	try {
		body = JSON.parse(text);
	} catch {
		throw new Structural('json');
	}
	if (typeof body !== 'object' || body === null || Array.isArray(body)) throw new Structural('body');
	if (body.horizonDays !== 30) throw new Structural('horizonDays');
	if (typeof body.essentialsReviewed !== 'boolean') throw new Structural('essentialsReviewed');
	if (!Array.isArray(body.items) || body.items.length > MAX_ROWS) throw new Structural('items');
	if (!Array.isArray(body.expectedReceipts) || body.expectedReceipts.length > MAX_ROWS) {
		throw new Structural('expectedReceipts');
	}
	const items = body.items.map((raw: Record<string, unknown>): Item => {
		if (typeof raw !== 'object' || raw === null) throw new Structural('item');
		if (raw.amount === null || raw.amount === undefined) throw new Structural('amount');
		if (typeof raw.notYetRecordedConfirmed !== 'boolean') throw new Structural('confirmed');
		if (raw.description !== undefined && raw.description !== null) {
			if (typeof raw.description !== 'string' || raw.description.length > 200) {
				throw new Structural('description');
			}
		}
		if (raw.boxId !== undefined && raw.boxId !== null && !Number.isInteger(raw.boxId)) {
			throw new Structural('boxId');
		}
		return {
			amount: cents(raw.amount),
			date: isoDate(raw.date),
			boxId: (raw.boxId as number | null | undefined) ?? null,
			boxAmount: raw.boxAmount === undefined || raw.boxAmount === null ? null : cents(raw.boxAmount),
			confirmed: raw.notYetRecordedConfirmed,
		};
	});
	const receipts = body.expectedReceipts.map((raw: Record<string, unknown>): Selection => {
		if (typeof raw !== 'object' || raw === null) throw new Structural('receipt');
		if (raw.recordKind !== 'DEBT' && raw.recordKind !== 'PAYMENT_RECORD') throw new Structural('recordKind');
		if (!Number.isInteger(raw.recordId)) throw new Structural('recordId');
		return { kind: raw.recordKind, id: raw.recordId as number, date: isoDate(raw.date) };
	});
	return { essentialsReviewed: body.essentialsReviewed, items, receipts };
}

type Problem = {
	reason: string;
	itemIndex: number | null;
	receiptIndex: number | null;
	boxId: number | null;
	shortfall: number | null;
};

const problem = (reason: string, fields: Partial<Problem> = {}): Problem => ({
	reason,
	itemIndex: null,
	receiptIndex: null,
	boxId: null,
	shortfall: null,
	...fields,
});

function timingSection(spec: PlanningPreviewSpec, today: string | null, to: string | null) {
	const empty = { dated: [], overdue: [], estimates: [] };
	if (spec.timing.kind === 'notApplicable' || spec.baseline?.trackingActive === false) {
		return { status: 'notApplicable', ...empty, reasons: [{ code: 'TRACKING_INACTIVE', accountId: null, statementId: null }] };
	}
	if (today === null || to === null) {
		return { status: 'unavailable', ...empty, reasons: [{ code: 'ZONE_UNAVAILABLE', accountId: null, statementId: null }] };
	}
	if (spec.timing.kind === 'unavailable') {
		return { status: 'unavailable', ...empty, reasons: [{ code: 'READ_FAILED', accountId: null, statementId: null }] };
	}
	const day = (offset: number | null) => (offset === null ? null : addCalendarDays(today, offset));
	const reasons = [...(spec.timing.reasons ?? [])];
	const dated: unknown[] = [];
	const overdue: unknown[] = [];
	for (const statement of spec.timing.statements) {
		const outstanding = toCents(statement.officialBalance) - toCents(statement.paidAmount);
		if (outstanding <= 0n) continue;
		const dueDate = day(statement.dueInDays);
		const row = {
			accountId: statement.accountId,
			accountName: statement.accountName,
			statementId: statement.statementId,
			periodStart: addCalendarDays(today, statement.periodEndInDays - 30),
			periodEnd: day(statement.periodEndInDays),
			dueDate,
			officialBalance: statement.officialBalance,
			paidAmount: statement.paidAmount,
			outstandingBalance: fromCents(outstanding),
			minimumPayment: statement.minimumPayment,
			avoidInterest: statement.avoidInterest,
			reconciliationMismatch: statement.reconciliationMismatch ?? false,
		};
		if (statement.reconciliationMismatch) {
			reasons.push({ code: 'RECONCILIATION_MISMATCH', accountId: statement.accountId, statementId: statement.statementId });
		}
		if (dueDate === null) {
			reasons.push({ code: 'STATEMENT_DUE_DATE_MISSING', accountId: statement.accountId, statementId: statement.statementId });
		} else if (dueDate < today) {
			overdue.push(row);
		} else if (dueDate <= to) {
			dated.push(row);
		}
	}
	const estimates = (spec.timing.estimates ?? []).map((estimate) => ({
		accountId: estimate.accountId,
		accountName: estimate.accountName,
		periodStart: addCalendarDays(today, estimate.periodEndInDays - 30),
		periodEnd: day(estimate.periodEndInDays),
		dueDate: day(estimate.dueInDays),
		estimatedBalance: estimate.estimatedBalance,
	}));
	return { status: reasons.length > 0 ? 'partial' : 'complete', dated, overdue, estimates, reasons };
}

/**
 * Answers one preview request against a scenario's server state, at `now`.
 */
export function respondToPlanningPreview(spec: PlanningPreviewSpec, bodyText: string, now: Date): FixtureResponse {
	let request: ReturnType<typeof parseRequest>;
	try {
		request = parseRequest(bodyText);
	} catch (error) {
		if (error instanceof Structural) return { status: 400, body: { error: `invalid ${error.message}` } };
		throw error;
	}

	const resolved = spec.timeZone === null ? null : userToday(spec.timeZone, now);
	const today = resolved?.status === 'ok' ? resolved.day : null;
	const to = today === null ? null : addCalendarDays(today, 29);
	const inside = (date: string) => today !== null && to !== null && date >= today && date <= to;
	const notes: string[] = spec.planEvaluationIncomplete ? ['PLAN_EVALUATION_INCOMPLETE'] : [];

	// Receipt resolution happens before calculation, in the service.
	const resolution: Problem[] = [];
	const firstIndex = new Map<string, number>();
	request.receipts.forEach((selection, index) => {
		const key = `${selection.kind}:${selection.id}`;
		if (firstIndex.has(key)) resolution.push(problem('DUPLICATE_RECEIPT', { receiptIndex: index }));
		else firstIndex.set(key, index);
	});
	const resolvedReceipts: Array<{ index: number; selection: Selection; amount: bigint }> = [];
	const baseline = spec.baseline;
	if (baseline !== null) {
		if (firstIndex.size > 0 && spec.receiptsReadable === false) {
			resolution.push(problem('RECEIPT_UNAVAILABLE'));
		} else {
			for (const [key, index] of firstIndex) {
				const state = spec.receipts[key];
				if (state === undefined) resolution.push(problem('RECEIPT_NOT_FOUND', { receiptIndex: index }));
				else if (!state.eligible) resolution.push(problem('RECEIPT_INELIGIBLE', { receiptIndex: index }));
				else resolvedReceipts.push({ index, selection: request.receipts[index], amount: toCents(state.amount) });
			}
		}
	}

	// The calculator.
	const problems: Problem[] = [];
	const net = baseline === null ? null : toCents(baseline.netBalance);
	const inBoxes = baseline === null ? null : baseline.boxes.reduce((sum, box) => sum + toCents(box.balance), 0n);
	if (baseline === null) problems.push(problem('BASELINE_UNAVAILABLE'));
	if (today === null) problems.push(problem('ZONE_UNAVAILABLE'));
	const boxBalances = new Map((baseline?.boxes ?? []).map((box) => [box.id, toCents(box.balance)]));
	const funding = new Map<number, bigint>();
	let expenses = 0n;
	let income = 0n;
	let projected: { netBalance: number; inBoxes: number; availableToSpend: number; perBox: unknown[] } | null = null;
	let status: 'complete' | 'partial' | 'unavailable' = 'unavailable';

	if (problems.length === 0 && net !== null && inBoxes !== null) {
		request.items.forEach((item, i) => {
			const amountValid = validAmount(item.amount);
			if (!amountValid) problems.push(problem('INVALID_AMOUNT', { itemIndex: i, boxId: item.boxId }));
			if (!inside(item.date)) problems.push(problem('DATE_OUT_OF_WINDOW', { itemIndex: i, boxId: item.boxId }));
			const allocated = item.boxAmount === null || item.boxAmount === 0n ? 0n : item.boxAmount;
			const fundingValid = allocated === 0n || validAmount(allocated);
			if (!fundingValid) problems.push(problem('INVALID_FUNDING', { itemIndex: i, boxId: item.boxId }));
			const exceeds =
				fundingValid && amountValid && typeof allocated === 'bigint' && allocated > (item.amount as bigint);
			if (exceeds) problems.push(problem('FUNDING_EXCEEDS_COST', { itemIndex: i, boxId: item.boxId }));
			if (item.boxId === null && fundingValid && typeof allocated === 'bigint' && allocated > 0n) {
				problems.push(problem('BOX_REQUIRED', { itemIndex: i }));
			} else if (item.boxId !== null && !boxBalances.has(item.boxId)) {
				problems.push(problem('BOX_NOT_FOUND', { itemIndex: i, boxId: item.boxId }));
			} else if (item.boxId !== null && fundingValid && !exceeds) {
				funding.set(item.boxId, (funding.get(item.boxId) ?? 0n) + (allocated as bigint));
			}
			if (amountValid) expenses += item.amount as bigint;
		});
		for (const receipt of resolvedReceipts) {
			if (!validAmount(receipt.amount)) problems.push(problem('INVALID_AMOUNT', { receiptIndex: receipt.index }));
			else income += receipt.amount;
			if (!inside(receipt.selection.date)) {
				problems.push(problem('DATE_OUT_OF_WINDOW', { receiptIndex: receipt.index }));
			}
		}
		for (const [id, amount] of [...funding].sort(([a], [b]) => a - b)) {
			const shortfall = amount - (boxBalances.get(id) as bigint);
			if (shortfall > 0n) problems.push(problem('BOX_CAPACITY_EXCEEDED', { boxId: id, shortfall: fromCents(shortfall) }));
		}
		if (problems.length === 0) {
			const totalFunding = [...funding.values()].reduce((sum, value) => sum + value, 0n);
			const projectedNet = net - expenses + income;
			const projectedBoxes = inBoxes - totalFunding;
			projected = {
				netBalance: fromCents(projectedNet),
				inBoxes: fromCents(projectedBoxes),
				availableToSpend: fromCents(projectedNet - projectedBoxes),
				perBox: (baseline?.boxes ?? []).map((box) => ({
					boxId: box.id,
					name: box.name,
					balance: box.balance,
					projectedBalance: fromCents(toCents(box.balance) - (funding.get(box.id) ?? 0n)),
				})),
			};
			if (!request.essentialsReviewed) problems.push(problem('ESSENTIALS_NOT_REVIEWED'));
			request.items.forEach((item, i) => {
				if (!item.confirmed) {
					problems.push(problem('COST_NOT_CONFIRMED_UNRECORDED', { itemIndex: i, boxId: item.boxId }));
				}
			});
			status = problems.length === 0 ? 'complete' : 'partial';
		}
	}

	const forceUnavailable = resolution.length > 0;
	const missingInputs = [
		...problems.filter(
			(entry) =>
				!(forceUnavailable && ['ESSENTIALS_NOT_REVIEWED', 'COST_NOT_CONFIRMED_UNRECORDED'].includes(entry.reason)),
		),
		...resolution,
	];
	if (forceUnavailable) {
		status = 'unavailable';
		projected = null;
	}
	const includedReceipts =
		projected === null
			? []
			: resolvedReceipts.map((receipt) => ({
					receiptIndex: receipt.index,
					recordKind: receipt.selection.kind,
					recordId: receipt.selection.id,
					amount: fromCents(receipt.amount),
					date: receipt.selection.date,
				}));
	if (includedReceipts.length > 0) notes.push('RECEIPTS_IF_RECEIVED');
	notes.push('LEDGER_TOTAL_NOT_CASH', 'NO_AUTOMATIC_TRANSACTION_MATCHING', 'UNENTERED_COSTS_EXCLUDED');
	if (baseline !== null && baseline.creditInFavor !== null && baseline.creditInFavor > 0) {
		notes.push('CREDIT_IN_FAVOR_IN_BASELINE');
	}

	return {
		status: 200,
		body: {
			generatedAt: now.toISOString(),
			timeZone: today === null ? null : spec.timeZone,
			window: today === null ? null : { from: today, to },
			baseline:
				baseline === null || net === null || inBoxes === null
					? null
					: {
							netBalance: fromCents(net),
							inBoxes: fromCents(inBoxes),
							availableToSpend: fromCents(net - inBoxes),
							source: baseline.trackingActive ? 'accounts' : 'transactions',
							creditInFavor: baseline.creditInFavor,
						},
			projected,
			status,
			missingInputs,
			includedReceipts,
			timing: timingSection(spec, today, to),
			undatedDebts: spec.undatedDebts,
			undatedDebtsStatus: spec.undatedDebts === null ? 'unavailable' : 'available',
			notes,
		},
	};
}
