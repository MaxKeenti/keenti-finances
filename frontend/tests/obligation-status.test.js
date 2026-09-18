// @ts-nocheck
/**
 * Slice 1D: the shared obligation-status derivation (decision D2).
 *
 * Each of D2's three truth tables is covered row by row, plus the day-boundary
 * case that makes the calendar contract necessary. The fixtures' clocks supply
 * the instants and zones so these assertions and the browser scenarios cannot
 * disagree about what "today" was.
 */

import { describe, expect, test } from 'bun:test';
import {
	accountStatementPaymentStatus,
	asCalendarDay,
	billingGenerationStatus,
	compareDays,
	contributionSectionStatus,
	contributionStatus,
	isOutstandingStatement,
	obligationPriority,
	statementPaymentStatus,
	userToday,
} from '../src/lib/obligation-status';
import { MEXICO_CITY_EVENING, MEXICO_CITY_MIDDAY, TOKYO_SAME_INSTANT, instantOf } from './fixtures/clock';
import { loadScenario } from './fixtures/scenarios';

const MX_TODAY = '2026-09-07';
const YESTERDAY = '2026-09-06';
const TOMORROW = '2026-09-08';

describe('calendar contract', () => {
	test('a calendar date is validated, not merely shaped', () => {
		expect(asCalendarDay('2026-09-07')).toBe('2026-09-07');
		expect(asCalendarDay('2026-13-45')).toBeNull();
		expect(asCalendarDay('2026-02-30')).toBeNull();
		expect(asCalendarDay('2026-9-7')).toBeNull();
		expect(asCalendarDay(null)).toBeNull();
		expect(asCalendarDay(20260907)).toBeNull();
	});

	test('one instant is two different calendar days in two zones', () => {
		const instant = instantOf(MEXICO_CITY_EVENING);
		expect(userToday(MEXICO_CITY_EVENING.timeZone, instant)).toEqual({
			status: 'ok',
			day: MX_TODAY,
		});
		expect(userToday(TOKYO_SAME_INSTANT.timeZone, instant)).toEqual({
			status: 'ok',
			day: TOMORROW,
		});
	});

	test('a missing or invalid zone is unavailable rather than silently UTC', () => {
		const instant = instantOf(MEXICO_CITY_EVENING);
		expect(userToday(undefined, instant).status).toBe('unavailable');
		expect(userToday('', instant).status).toBe('unavailable');
		expect(userToday('Not/AZone', instant).status).toBe('unavailable');
		expect(userToday('UTC', new Date(Number.NaN)).status).toBe('unavailable');
	});

	test('a hand-built ok resolution is still validated, not trusted', () => {
		// Callers may assemble this shape themselves. A malformed day arriving
		// through it would otherwise skip the check every raw input goes through
		// and be compared as text: '2026-13-45' sorts after any real due date,
		// silently turning an upcoming statement into a past-due one.
		const malformed = { status: 'ok', day: '2026-13-45' };
		expect(billingGenerationStatus({ nextBillingDate: MX_TODAY, today: malformed }).state).toBe(
			'unavailable',
		);
		expect(
			statementPaymentStatus({ outstandingBalance: 100, dueDate: TOMORROW, today: malformed }).state,
		).toBe('outstanding-due-unknown');
	});

	test('"cannot tell" is not "the same day"', () => {
		expect(compareDays(YESTERDAY, MX_TODAY)).toBe(-1);
		expect(compareDays(MX_TODAY, MX_TODAY)).toBe(0);
		expect(compareDays(TOMORROW, MX_TODAY)).toBe(1);
		expect(compareDays('nope', MX_TODAY)).toBeNull();
		expect(compareDays(MX_TODAY, null)).toBeNull();
	});
});

describe('truth table A — billing generation', () => {
	const rows = [
		{ input: { nextBillingDate: MX_TODAY, today: MX_TODAY, subscriptionAvailable: false }, state: 'unavailable' },
		{ input: { nextBillingDate: null, today: MX_TODAY }, state: 'unavailable' },
		{ input: { nextBillingDate: MX_TODAY, today: { status: 'unavailable', reason: 'zone' } }, state: 'unavailable' },
		{ input: { nextBillingDate: YESTERDAY, today: MX_TODAY }, state: 'pending' },
		{ input: { nextBillingDate: MX_TODAY, today: MX_TODAY }, state: 'due-today' },
		{ input: { nextBillingDate: TOMORROW, today: MX_TODAY }, state: 'caught-up' },
	];

	for (const row of rows) {
		test(`${JSON.stringify(row.input)} → ${row.state}`, () => {
			expect(billingGenerationStatus(row.input).state).toBe(row.state);
		});
	}

	test('the same cursor is due today in Mexico City and pending in Tokyo', () => {
		const instant = instantOf(MEXICO_CITY_EVENING);
		const cursor = '2026-09-07';
		expect(
			billingGenerationStatus({
				nextBillingDate: cursor,
				today: userToday(MEXICO_CITY_EVENING.timeZone, instant),
			}).state,
		).toBe('due-today');
		expect(
			billingGenerationStatus({
				nextBillingDate: cursor,
				today: userToday(TOKYO_SAME_INSTANT.timeZone, instant),
			}).state,
		).toBe('pending');
	});

	test('the fixture cursors are yesterday, today and tomorrow in the User zone', () => {
		const scenario = loadScenario('FX-DATE-BOUNDARY-01');
		const today = userToday(scenario.clock.timeZone, instantOf(scenario.clock));
		const states = scenario.routes['GET /api/subscriptions'].map(
			(subscription) =>
				billingGenerationStatus({ nextBillingDate: subscription.nextBillingDate, today }).state,
		);
		expect(states).toEqual(['pending', 'due-today', 'caught-up']);
	});

	test('a readable cursor is reported even when the state is unavailable', () => {
		const result = billingGenerationStatus({ nextBillingDate: MX_TODAY, today: 'nonsense' });
		expect(result).toEqual({ state: 'unavailable', scheduledDate: MX_TODAY });
	});
});

describe('truth table B — generated contributions', () => {
	const rows = [
		{ name: 'section unavailable', input: { status: 'PAID', amount: 100, sectionAvailable: false }, state: 'unavailable' },
		{ name: 'invalid amount', input: { status: 'PAID', amount: null }, state: 'unavailable' },
		{ name: 'unknown status', input: { status: 'SETTLED_MAYBE', amount: 100 }, state: 'unavailable' },
		{ name: 'PAID', input: { status: 'PAID', amount: 100 }, state: 'received' },
		{ name: 'PENDING', input: { status: 'PENDING', amount: 100 }, state: 'awaiting' },
	];

	for (const row of rows) {
		test(`${row.name} → ${row.state}`, () => {
			expect(contributionStatus(row.input).state).toBe(row.state);
		});
	}

	test('a past billing date does not make a PENDING contribution overdue', () => {
		const result = contributionStatus({
			status: 'PENDING',
			amount: 149.5,
			billingDate: '2026-01-01',
			transactionId: null,
		});
		expect(result.state).toBe('awaiting');
		expect(result.billingDate).toBe('2026-01-01');
	});

	test('a PAID record with no linked Transaction is still received', () => {
		const result = contributionStatus({ status: 'PAID', amount: 99, transactionId: null });
		expect(result.state).toBe('received');
		expect(result.linkedTransactionId).toBeNull();
	});

	test('a zero amount is a real amount; a missing one is not', () => {
		expect(contributionStatus({ status: 'PAID', amount: 0 }).state).toBe('received');
		expect(contributionStatus({ status: 'PAID', amount: undefined }).state).toBe('unavailable');
	});

	test('a negative amount is invalid, not a contribution owed backwards', () => {
		// D2's first row: an invalid amount makes contributions unavailable.
		// "Awaiting −120" would announce a receipt nobody is expecting, and
		// "received −120" would report money leaving on a PAID record.
		const awaiting = contributionStatus({ status: 'PENDING', amount: -120 });
		expect(awaiting.state).toBe('unavailable');
		expect(awaiting.amount).toBeNull();
		expect(contributionStatus({ status: 'PAID', amount: -120 }).state).toBe('unavailable');
	});

	test('an empty loaded period is distinguishable from an unreadable one', () => {
		expect(contributionSectionStatus([])).toBe('no-records');
		expect(contributionSectionStatus(null)).toBe('unavailable');
		expect(contributionSectionStatus([{ id: 1 }])).toBe('has-records');
	});

	test('the populated fixture yields both received and awaiting contributions', () => {
		const scenario = loadScenario('FX-SUB-SHARED-POPULATED-01');
		const payments = scenario.routes['GET /api/subscriptions/9401/payments'];
		const states = payments.map(
			(payment) =>
				contributionStatus({
					status: payment.status,
					amount: payment.amount,
					billingDate: payment.billingDate,
					paidDate: payment.paidDate,
					transactionId: payment.transactionId,
				}).state,
		);
		expect(new Set(states)).toEqual(new Set(['received', 'awaiting']));
	});
});

describe('truth table C — confirmed statement payment', () => {
	const rows = [
		{ name: 'read failed', input: { outstandingBalance: 500, dueDate: MX_TODAY, statementAvailable: false }, state: 'unavailable' },
		{ name: 'amount unreadable', input: { outstandingBalance: 'mucho', dueDate: MX_TODAY }, state: 'unavailable' },
		{ name: 'covered', input: { outstandingBalance: 0, dueDate: YESTERDAY }, state: 'covered' },
		{ name: 'overpaid', input: { outstandingBalance: -25, dueDate: YESTERDAY }, state: 'covered' },
		{ name: 'due date invalid', input: { outstandingBalance: 500, dueDate: 'soon' }, state: 'outstanding-due-unknown' },
		{ name: 'past due', input: { outstandingBalance: 500, dueDate: YESTERDAY }, state: 'outstanding-past-due' },
		{ name: 'due today', input: { outstandingBalance: 500, dueDate: MX_TODAY }, state: 'outstanding-due-today' },
		{ name: 'upcoming', input: { outstandingBalance: 500, dueDate: TOMORROW }, state: 'outstanding-upcoming' },
	];

	for (const row of rows) {
		test(`${row.name} → ${row.state}`, () => {
			expect(statementPaymentStatus({ ...row.input, today: MX_TODAY }).state).toBe(row.state);
		});
	}

	test('an outstanding balance of zero stays covered on every date', () => {
		for (const day of [YESTERDAY, MX_TODAY, TOMORROW, '2026-09-09']) {
			expect(statementPaymentStatus({ outstandingBalance: 0, dueDate: '2026-09-08', today: day }).state).toBe(
				'covered',
			);
		}
	});

	test('a statement due 8 September is upcoming in Mexico City and due today in Tokyo', () => {
		const instant = instantOf(MEXICO_CITY_EVENING);
		const statement = { outstandingBalance: 640, dueDate: '2026-09-08' };
		expect(
			statementPaymentStatus({
				...statement,
				today: userToday(MEXICO_CITY_EVENING.timeZone, instant),
			}).state,
		).toBe('outstanding-upcoming');
		expect(
			statementPaymentStatus({
				...statement,
				today: userToday(TOKYO_SAME_INSTANT.timeZone, instant),
			}).state,
		).toBe('outstanding-due-today');
		expect(statementPaymentStatus({ ...statement, today: '2026-09-09' }).state).toBe(
			'outstanding-past-due',
		);
	});

	test('a reconciliation mismatch is carried alongside, not instead of, the payment state', () => {
		const scenario = loadScenario('FX-STMT-MISMATCH-01');
		const [statement] = scenario.routes['GET /api/accounts/9108/credit-statements'];
		const status = statementPaymentStatus({
			outstandingBalance: statement.outstandingBalance,
			dueDate: statement.dueDate,
			reconciliationMismatch: statement.reconciliationMismatch,
			today: MX_TODAY,
		});
		expect(status.state).toBe('outstanding-upcoming');
		expect(status.reconciliationMismatch).toBe(true);
		expect(status.outstanding).toBe(scenario.expected.outstandingBalance);
	});

	test('positive credit in the User’s favor coexists with an unpaid statement', () => {
		const scenario = loadScenario('FX-CREDIT-INFAVOR-STMT-01');
		const statements = scenario.routes['GET /api/accounts/9112/credit-statements'];
		const { status } = accountStatementPaymentStatus({
			statements,
			today: MX_TODAY,
			read: (statement) => statement,
		});
		expect(status.state).toBe('outstanding-upcoming');
		expect(status.outstanding).toBe(scenario.expected.outstandingBalance);
		// The signed account balance is a separate, also-true fact.
		const credit = scenario.routes['GET /api/accounts'].find((account) => account.id === 9112);
		expect(credit.balance).toBe(scenario.expected.creditInFavor);
	});

	test('a successfully loaded but empty statement list is estimated-only, not covered', () => {
		const { status, statement } = accountStatementPaymentStatus({
			statements: [],
			estimateAvailable: true,
			today: MX_TODAY,
			read: (item) => item,
		});
		expect(status.state).toBe('estimated-only');
		expect(statement).toBeNull();
	});

	test('an unreadable statement list is unavailable, not estimated-only', () => {
		const { status } = accountStatementPaymentStatus({
			statements: null,
			today: MX_TODAY,
			read: (item) => item,
		});
		expect(status.state).toBe('unavailable');
	});

	test('estimated-only needs the estimate read to have succeeded too', () => {
		// D2 reaches that row only when the *separate* estimate read succeeds.
		// With a failed estimate, nothing about this card's current cycle is
		// known, so "estimated statement, not confirmed" would reassure the User
		// on the strength of a request that never answered.
		const { status } = accountStatementPaymentStatus({
			statements: [],
			today: MX_TODAY,
			read: (item) => item,
			estimateAvailable: false,
		});
		expect(status.state).toBe('unavailable');
	});

	test('a mismatch survives a covered payment state', () => {
		// The statement is fully paid, so it is correctly kept out of the
		// attention slot — but the recorded activity still disagrees with the
		// bank's snapshot, and that review notice is independent of payment.
		const { status, mismatches } = accountStatementPaymentStatus({
			statements: [
				{ id: 7, outstandingBalance: 0, dueDate: YESTERDAY, reconciliationMismatch: true, mismatchAmount: 125.5 },
			],
			today: MX_TODAY,
			read: (item) => item,
		});
		expect(status.state).toBe('covered');
		expect(mismatches).toHaveLength(1);
		expect(mismatches[0].statement.id).toBe(7);
		expect(mismatches[0].status.mismatchAmount).toBe(125.5);
	});

	test('a mismatch on a statement the priority sort did not pick is still reported', () => {
		const statements = [
			// Loses the attention slot to the past-due statement below.
			{ id: 1, outstandingBalance: 100, dueDate: TOMORROW, reconciliationMismatch: true, mismatchAmount: 40 },
			{ id: 2, outstandingBalance: 100, dueDate: YESTERDAY, reconciliationMismatch: false, mismatchAmount: 0 },
		];
		const { statement, mismatches } = accountStatementPaymentStatus({
			statements,
			today: MX_TODAY,
			read: (item) => item,
		});
		expect(statement.id).toBe(2);
		expect(mismatches.map((entry) => entry.statement.id)).toEqual([1]);
	});

	test('an unreadable list reports no mismatches rather than a false all-clear', () => {
		const { mismatches } = accountStatementPaymentStatus({
			statements: null,
			today: MX_TODAY,
			read: (item) => item,
		});
		expect(mismatches).toEqual([]);
	});

	test('the statement needing attention wins, earliest due date first', () => {
		const statements = [
			{ id: 3, outstandingBalance: 0, dueDate: '2026-09-01' },
			{ id: 1, outstandingBalance: 100, dueDate: TOMORROW },
			{ id: 2, outstandingBalance: 100, dueDate: YESTERDAY },
		];
		const { statement } = accountStatementPaymentStatus({
			statements,
			today: MX_TODAY,
			read: (item) => item,
		});
		expect(statement.id).toBe(2);
	});
});

describe('combined presentation', () => {
	test('unavailable essential data outranks every obligation', () => {
		expect(obligationPriority('unavailable')).toBeLessThan(obligationPriority('outstanding-past-due'));
	});

	test('past due outranks due today, which outranks pending generation', () => {
		expect(obligationPriority('outstanding-past-due')).toBeLessThan(
			obligationPriority('outstanding-due-today'),
		);
		expect(obligationPriority('outstanding-due-today')).toBeLessThan(obligationPriority('pending'));
		expect(obligationPriority('pending')).toBeLessThan(obligationPriority('outstanding-upcoming'));
	});

	test('an awaiting contribution never outranks a confirmed payment obligation', () => {
		expect(obligationPriority('awaiting')).toBeGreaterThan(obligationPriority('outstanding-upcoming'));
	});

	test('only confirmed outstanding states count as money still owed', () => {
		expect(isOutstandingStatement('outstanding-past-due')).toBe(true);
		expect(isOutstandingStatement('outstanding-due-unknown')).toBe(true);
		expect(isOutstandingStatement('covered')).toBe(false);
		expect(isOutstandingStatement('estimated-only')).toBe(false);
		expect(isOutstandingStatement('unavailable')).toBe(false);
	});

	test('the midday fixture clock resolves to the same User day as the evening one', () => {
		expect(userToday(MEXICO_CITY_MIDDAY.timeZone, instantOf(MEXICO_CITY_MIDDAY))).toEqual({
			status: 'ok',
			day: MX_TODAY,
		});
	});
});
