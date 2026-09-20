// @ts-nocheck
/**
 * Phase 4 — the composed dashboard read model, at request level.
 *
 * These drive the production dashboard loader with the synthetic fixture
 * backend and assert what the page is entitled to claim. Nothing here reaches a
 * real service, and every figure comes from `scenarios.ts`, which is the source
 * of truth the manifest and these assertions share.
 *
 * The questions under test are the ones the acceptance criteria ask: does the
 * arithmetic hold and stay visible, does a separately-identified obligation
 * stay out of the totals, is a genuine zero still distinguishable from a failed
 * read, and does an unreadable value ever arrive as a zero or a healthy status.
 */

import { describe, expect, test } from 'bun:test';
import { createFixtureBackend } from './fixtures/backend';
import { loadScenario, UNAVAILABLE_SECTION } from './fixtures/scenarios';
import { load as dashboardLoad } from '../src/routes/+page.server';
import {
	accountStatementPaymentStatus,
	billingGenerationStatus,
	contributionStatus,
} from '../src/lib/obligation-status';
import { availableToSpendExplanation } from '../src/lib/balance-presentation';

function cookies(values = {}) {
	const store = new Map(Object.entries(values));
	return { get: (name) => store.get(name), set: (name, value) => store.set(name, value), store };
}

async function runDashboard(backend, year = 2026) {
	const data = await dashboardLoad({
		fetch: backend.fetch,
		url: new URL(`http://app.test/?year=${year}`),
		cookies: cookies({ PARAGLIDE_LOCALE: 'es' }),
	});
	backend.assertNoUndeclaredRoutes();
	return data;
}

/** Replaces one section of a scenario's overview body, leaving the rest intact. */
function withSection(scenarioId, name, section) {
	const body = loadScenario(scenarioId).routes['GET /api/dashboard/overview'];
	return { 'GET /api/dashboard/overview': { ...body, [name]: section } };
}

describe('FX-DASH-NEG-01 — the acceptance arithmetic', () => {
	test('money held plus credit in favor is the Net Balance', async () => {
		const backend = createFixtureBackend('FX-DASH-NEG-01');
		const data = await runDashboard(backend);
		const { expected } = backend.scenario;
		const position = data.overview.data.position.data;

		expect(position.moneyHeld).toBe(expected.moneyHeld);
		expect(position.creditInFavor).toBe(expected.creditInFavor);
		expect(position.netBalance).toBe(expected.netBalance);
		expect(position.moneyHeld + position.creditInFavor).toBe(position.netBalance);
		// Credit in the User's favour is not credit debt; the two are separate
		// sums so one card being overpaid cannot mask another being owed.
		expect(position.creditDebt).toBe(0);
	});

	test('Net Balance minus In Boxes is Available to Spend, and it is negative here', async () => {
		const backend = createFixtureBackend('FX-DASH-NEG-01');
		const data = await runDashboard(backend);
		const { expected } = backend.scenario;
		const position = data.overview.data.position.data;

		expect(position.inBoxes).toBe(expected.inBoxes);
		expect(position.availableToSpend).toBe(expected.availableToSpend);
		expect(position.netBalance - position.inBoxes).toBe(position.availableToSpend);

		// Over-reserving, not a negative recorded position: Net Balance is
		// positive and the Boxes reserve more than it.
		const explanation = availableToSpendExplanation(position);
		expect(explanation.state).toBe('over-reserved');
		expect(explanation.shortfall).toBe(1_124);
		expect(explanation.negativeNet).toBeNull();
	});

	test('the confirmed statement is a separate obligation, never subtracted twice', async () => {
		const backend = createFixtureBackend('FX-DASH-NEG-01');
		const data = await runDashboard(backend);
		const { expected } = backend.scenario;
		const { position, attention } = data.overview.data;

		const { status } = accountStatementPaymentStatus({
			statements: attention.data.statements,
			today: '2026-09-07',
			read: (statement) => statement,
		});
		expect(status.outstanding).toBe(expected.outstandingStatementPayment);
		expect(status.state).toBe('outstanding-upcoming');

		// The purchases behind it already moved Net Balance. Subtracting the
		// statement again would make 4,176.00 into 3,865.75.
		expect(position.data.netBalance).toBe(4_176);
		expect(position.data.availableToSpend).toBe(-1_124);
	});

	test('available credit is limit-derived capacity and enters no total', async () => {
		const backend = createFixtureBackend('FX-DASH-NEG-01');
		const data = await runDashboard(backend);
		const { expected } = backend.scenario;
		const position = data.overview.data.position.data;

		expect(position.availableCredit).toBe(expected.availableCredit);
		expect(expected.creditLimit + expected.creditInFavor).toBe(expected.availableCredit);
		// Capacity is not money held: adding it would turn 4,176.00 into 13,231.50.
		expect(position.moneyHeld).toBe(4_120.5);
		expect(position.netBalance).toBe(4_176);
		expect(position.inBoxes).toBe(5_300);
	});

	test('expected money is reported without being added to available money', async () => {
		const backend = createFixtureBackend('FX-DASH-NEG-01');
		const data = await runDashboard(backend);
		const { position, expected } = data.overview.data;

		expect(expected.data.debtsOutstanding).toBe(900);
		expect(expected.data.contributionsOutstanding).toBe(150);
		// 1,050.00 is recorded as owed and not received; Available to Spend is
		// unchanged by it, and stays below zero.
		expect(position.data.availableToSpend).toBe(-1_124);
	});

	test('every expected row links back to the record it came from', async () => {
		const backend = createFixtureBackend('FX-DASH-NEG-01');
		const data = await runDashboard(backend);
		const { debts, contributions } = data.overview.data.expected.data;

		expect(debts.map((debt) => debt.debtId)).toEqual([9909]);
		expect(contributions.map((row) => row.paymentRecordId)).toEqual([9428]);
		expect(contributions[0].subscriptionId).toBe(9430);
		// A contribution belongs to a Subscription Member; a Personal
		// Subscription's own record has no member and is never listed here.
		expect(contributions.every((row) => row.memberId !== null)).toBe(true);
	});

	test('a pending contribution stays awaiting, never overdue', async () => {
		// No contribution due date or grace period is agreed anywhere in the
		// contract (decision D2), so "late" would be invented policy — even for
		// a billing date eight days in the past.
		const backend = createFixtureBackend('FX-DASH-NEG-01');
		const data = await runDashboard(backend);
		const [contribution] = data.overview.data.expected.data.contributions;

		const status = contributionStatus({
			status: 'PENDING',
			amount: contribution.amount,
			billingDate: contribution.billingDate,
		});
		expect(status.state).toBe('awaiting');
	});

	test('plan guidance carries the plans’ own figures and suggests nothing automatic', async () => {
		const backend = createFixtureBackend('FX-DASH-NEG-01');
		const data = await runDashboard(backend);
		const plans = data.overview.data.plans.data;

		const budget = plans.items.find((plan) => plan.type === 'SPENDING_BUDGET');
		const goal = plans.items.find((plan) => plan.type === 'SAVING_GOAL');
		expect(budget.suggestedTopUp).toBe(500);
		expect(budget.desiredBalance - budget.boxBalance).toBe(budget.suggestedTopUp);
		expect(goal.remainingAmount).toBe(4_200);
		expect(goal.suggestedContribution).toBe(1_400);
		expect(goal.targetAmount - goal.boxBalance).toBe(goal.remainingAmount);
		// The Saving Goal's suggestion is capped at what the goal still needs, so
		// a nearly-complete goal is never asked for a full commitment.
		expect(goal.suggestedContribution).toBe(
			Math.min(goal.currentCommitment, goal.remainingAmount),
		);

		// Reserved money is already inside In Boxes; the plans section reports it
		// rather than adding a second claim on the same money.
		expect(plans.reservedInPlannedBoxes).toBe(5_300);
		expect(plans.inBoxes).toBe(data.overview.data.position.data.inBoxes);
	});

	test('reading the dashboard issues no writes', async () => {
		const backend = createFixtureBackend('FX-DASH-NEG-01');
		await runDashboard(backend);

		expect(backend.requests.every((request) => request.method === 'GET')).toBe(true);
	});
});

describe('the year selector scopes history only', () => {
	test('a different year leaves the current-position totals unchanged', async () => {
		const backend = createFixtureBackend('FX-DASH-NEG-01');
		const thisYear = await runDashboard(backend, 2026);
		const lastYear = await runDashboard(backend, 2025);

		expect(lastYear.year).toBe(2025);
		expect(lastYear.overview.data.position.data).toEqual(
			thisYear.overview.data.position.data,
		);
		expect(backend.requests.map((request) => request.url)).toEqual([
			'http://localhost:8080/api/dashboard/overview?year=2026',
			'http://localhost:8080/api/dashboard/overview?year=2025',
		]);
	});

	test('an unparseable year still loads the page', async () => {
		const backend = createFixtureBackend('FX-DASH-NEG-01');
		const data = await dashboardLoad({
			fetch: backend.fetch,
			url: new URL('http://app.test/?year=hace%20mucho'),
			cookies: cookies(),
		});

		expect(data.overview.status).toBe('ok');
		expect(Number.isInteger(data.year)).toBe(true);
		// The unusable year is dropped here rather than forwarded. Sending
		// `year=NaN` would earn a 400 and take all five sections down — including
		// the current position, which the year does not scope at all.
		expect(backend.requests.map((request) => request.url)).toEqual([
			'http://localhost:8080/api/dashboard/overview?year=current',
		]);
	});

	test('a year outside the accepted bounds falls back rather than failing the page', async () => {
		for (const raw of ['0', '1899', '10000', '-2026']) {
			const backend = createFixtureBackend('FX-DASH-NEG-01');
			const data = await dashboardLoad({
				fetch: backend.fetch,
				url: new URL(`http://app.test/?year=${encodeURIComponent(raw)}`),
				cookies: cookies(),
			});

			expect(data.overview.status).toBe('ok');
			expect(backend.requests.map((request) => request.url)).toEqual([
				'http://localhost:8080/api/dashboard/overview?year=current',
			]);
		}
	});

	test('a fractional year falls back to the user-local current year', async () => {
		const backend = createFixtureBackend('FX-DASH-NEG-01');
		await dashboardLoad({
			fetch: backend.fetch,
			url: new URL('http://app.test/?year=2026.5'),
			cookies: cookies(),
		});
		expect(backend.requests.map((request) => request.url)).toEqual([
			'http://localhost:8080/api/dashboard/overview?year=current',
		]);
	});

	// `current` is resolved against the User's own time zone by the backend,
	// which is the only side that knows it. The year the page labels its history
	// with is then the year the response says it answered for, not the server's
	// calendar year — the two differ for hours around every New Year.
	test('the displayed year comes from the response that scoped it', async () => {
		const backend = createFixtureBackend('FX-DASH-NEG-01');
		const data = await dashboardLoad({
			fetch: backend.fetch,
			url: new URL('http://app.test/?year=nope'),
			cookies: cookies(),
		});

		expect(data.year).toBe(data.overview.data.history.data.year);
	});
});

describe('genuine zero, empty setup, and unavailable are three different states', () => {
	test('a new User is offered setup rather than described as having nothing', async () => {
		const backend = createFixtureBackend('FX-TRACK-INACTIVE-01');
		const data = await runDashboard(backend);
		const position = data.overview.data.position.data;

		expect(position.setupRequired).toBe(true);
		expect(position.trackingActive).toBe(false);
		// No account breakdown exists before activation, and D1 forbids guessing
		// one. `null` says that; `0` would claim the User holds nothing.
		expect(position.moneyHeld).toBeNull();
		expect(position.creditDebt).toBeNull();
		expect(position.creditInFavor).toBeNull();
		expect(position.availableCredit).toBeNull();
	});

	test('a legitimate zero is an ok section with real zeros', async () => {
		const backend = createFixtureBackend('FX-BAL-ZERO-01');
		const data = await runDashboard(backend);

		expect(data.overview.data.position).toMatchObject({
			status: 'ok',
			data: { netBalance: 0, inBoxes: 0, availableToSpend: 0, moneyHeld: 0 },
		});
	});

	test('an unavailable section carries no figures a reader could mistake for zero', async () => {
		const backend = createFixtureBackend('FX-BAL-ZERO-01', {
			routes: withSection('FX-BAL-ZERO-01', 'position', UNAVAILABLE_SECTION),
		});
		const data = await runDashboard(backend);

		expect(data.overview.data.position).toEqual({ status: 'unavailable', reason: 'error' });
		expect(JSON.stringify(data.overview.data.position)).not.toContain('netBalance');
	});

	test('an empty attention section is “nothing to do”, an unavailable one is not', async () => {
		const available = createFixtureBackend('FX-BAL-ZERO-01');
		const withData = await runDashboard(available);
		expect(withData.overview.data.attention.data.statements).toEqual([]);
		expect(withData.overview.data.attention.data.partial).toBe(false);

		const failed = createFixtureBackend('FX-BAL-ZERO-01', {
			routes: withSection('FX-BAL-ZERO-01', 'attention', UNAVAILABLE_SECTION),
		});
		const withoutData = await runDashboard(failed);
		expect(withoutData.overview.data.attention.status).toBe('unavailable');
		expect(JSON.stringify(withoutData.overview.data.attention)).not.toContain('statements');
	});
});

describe('unknown and malformed values never become a healthy status', () => {
	test('active tracking cannot present missing credit figures as zero', async () => {
		const body = loadScenario('FX-DASH-NEG-01').routes['GET /api/dashboard/overview'];
		const backend = createFixtureBackend('FX-DASH-NEG-01', {
			routes: withSection('FX-DASH-NEG-01', 'position', {
				...body.position, data: { ...body.position.data, creditDebt: null },
			}),
		});
		const data = await runDashboard(backend);
		expect(data.overview.data.position.status).toBe('unavailable');
		expect(data.overview.data.history.status).toBe('ok');
	});

	test('missing history year does not produce year zero', async () => {
		const body = loadScenario('FX-DASH-NEG-01').routes['GET /api/dashboard/overview'];
		const backend = createFixtureBackend('FX-DASH-NEG-01', {
			routes: withSection('FX-DASH-NEG-01', 'history', {
				...body.history, data: { ...body.history.data, year: null },
			}),
		});
		const data = await runDashboard(backend);
		expect(data.overview.data.history.status).toBe('unavailable');
		expect(data.overview.data.position.status).toBe('ok');
	});

	test('a section whose body is malformed is invalid, not partially trusted', async () => {
		const backend = createFixtureBackend('FX-DASH-NEG-01', {
			routes: withSection('FX-DASH-NEG-01', 'expected', {
				status: 'ok',
				reason: null,
				data: { debtsOutstanding: 'novecientos', debts: [] },
			}),
		});
		const data = await runDashboard(backend);

		expect(data.overview.data.expected).toEqual({ status: 'unavailable', reason: 'invalid' });
		expect(data.overview.data.position.status).toBe('ok');
	});

	test('an unreadable generation cursor is unavailable, not caught up', async () => {
		const body = loadScenario('FX-DASH-NEG-01').routes['GET /api/dashboard/overview'];
		const backend = createFixtureBackend('FX-DASH-NEG-01', {
			routes: withSection('FX-DASH-NEG-01', 'attention', {
				...body.attention,
				data: {
					...body.attention.data,
					billing: [
						{
							subscriptionId: 9430,
							name: 'Suscripción sintética P4',
							subscriptionType: 'PERSONAL',
							nextBillingDate: 'el martes',
						},
					],
				},
			}),
		});
		const data = await runDashboard(backend);
		const [cursor] = data.overview.data.attention.data.billing;

		expect(cursor.nextBillingDate).toBeNull();
		expect(billingGenerationStatus({ nextBillingDate: cursor.nextBillingDate, today: '2026-09-07' }).state).toBe(
			'unavailable',
		);
	});

	test('a plan with an unreadable suggested top-up fails its section', async () => {
		const body = loadScenario('FX-DASH-NEG-01').routes['GET /api/dashboard/overview'];
		const backend = createFixtureBackend('FX-DASH-NEG-01', {
			routes: withSection('FX-DASH-NEG-01', 'plans', {
				...body.plans,
				data: {
					...body.plans.data,
					items: [{ ...body.plans.data.items[0], suggestedTopUp: 'quinientos' }],
				},
			}),
		});
		const data = await runDashboard(backend);

		// A top-up of zero would read as "this Box is fully funded".
		expect(data.overview.data.plans).toEqual({ status: 'unavailable', reason: 'invalid' });
	});

	test('a body that is not an object at all fails the whole overview', async () => {
		const backend = createFixtureBackend('FX-DASH-NEG-01', {
			routes: { 'GET /api/dashboard/overview': ['nope'] },
		});
		const data = await runDashboard(backend);

		expect(data.overview).toEqual({ status: 'unavailable', reason: 'invalid' });
	});
});

describe('the User’s calendar day decides every obligation label', () => {
	const cursors = () =>
		loadScenario('FX-DATE-BOUNDARY-01').routes['GET /api/dashboard/overview'].attention.data;

	test('the same cursors read differently in Mexico City and Tokyo', () => {
		const { billing, statements } = cursors();
		const states = (today) =>
			billing.map((entry) => billingGenerationStatus({ nextBillingDate: entry.nextBillingDate, today }).state);

		// 2026-09-08T04:30:00Z is 7 September in Mexico City and 8 September in
		// Tokyo. Same instant, same data, different day — and different labels.
		expect(states('2026-09-07')).toEqual(['pending', 'due-today', 'caught-up']);
		expect(states('2026-09-08')).toEqual(['pending', 'pending', 'due-today']);

		const statementState = (today) =>
			accountStatementPaymentStatus({ statements, today, read: (statement) => statement }).status.state;
		expect(statementState('2026-09-07')).toBe('outstanding-past-due');
		expect(statementState('2026-09-06')).toBe('outstanding-due-today');
		expect(statementState('2026-09-05')).toBe('outstanding-upcoming');
	});

	test('an unusable time zone produces no label rather than a guessed one', () => {
		const { billing, statements } = cursors();
		const unavailable = { status: 'unavailable', reason: 'zone' };

		expect(
			billing.map((entry) => billingGenerationStatus({ nextBillingDate: entry.nextBillingDate, today: unavailable }).state),
		).toEqual(['unavailable', 'unavailable', 'unavailable']);

		const { status } = accountStatementPaymentStatus({
			statements,
			today: unavailable,
			read: (statement) => statement,
		});
		// The amount owed survives; only its day-relative label is withheld.
		expect(status.state).toBe('outstanding-due-unknown');
		expect(status.outstanding).toBe(500);
	});
});
