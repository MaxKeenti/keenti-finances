// @ts-nocheck
/**
 * Slice 2A: mobile navigation preferences and the Boxes overview's plan state,
 * plus the Slice 0B fixtures those checks stand on.
 *
 * The overview test drives the real `/boxes` loader against the fixture
 * backend, so it checks the orchestration that runs on the server.
 */

import { describe, expect, test } from 'bun:test';
import { createFixtureBackend } from './fixtures/backend';
import { loadScenario } from './fixtures/scenarios';
import {
	DASHBOARD_HREF,
	DEFAULT_PINNED_HREFS,
	resolveMobileNavHrefs,
} from '../src/lib/navigation';
import { boxPlanState } from '../src/lib/types/box-plans';
import { load as boxesLoad } from '../src/routes/boxes/+page.server';

function cookies(values = {}) {
	const store = new Map(Object.entries(values));
	return { get: (name) => store.get(name), set: (name, value) => store.set(name, value), store };
}

async function runBoxes(backend) {
	const data = await boxesLoad({ fetch: backend.fetch, cookies: cookies() });
	backend.assertNoUndeclaredRoutes();
	expect(backend.requests.every((request) => request.method === 'GET')).toBe(true);
	return data;
}

describe('mobile navigation preferences', () => {
	test('a User who pinned nothing gets Dashboard, Transactions, Boxes', () => {
		expect(resolveMobileNavHrefs(undefined)).toEqual([DASHBOARD_HREF, ...DEFAULT_PINNED_HREFS]);
		expect(resolveMobileNavHrefs('')).toEqual([DASHBOARD_HREF, ...DEFAULT_PINNED_HREFS]);
	});

	test('every explicit pin survives the migration, in the stored order', () => {
		expect(resolveMobileNavHrefs('/debts,/subscriptions,/accounts')).toEqual([
			'/',
			'/debts',
			'/subscriptions',
			'/accounts',
		]);
	});

	test('a single explicit pin is not padded out with the new defaults', () => {
		expect(resolveMobileNavHrefs('/subscriptions')).toEqual(['/', '/subscriptions']);
	});

	test('a stored Dashboard pin is de-duplicated, not shown twice', () => {
		expect(resolveMobileNavHrefs('/')).toEqual(['/']);
		expect(resolveMobileNavHrefs('/,/transactions,/boxes')).toEqual(['/', '/transactions', '/boxes']);
		const resolved = resolveMobileNavHrefs('/,/debts');
		expect(resolved).toEqual(['/', '/debts']);
		expect(resolved.filter((href) => href === DASHBOARD_HREF)).toHaveLength(1);
	});

	test('unknown destinations are ignored without discarding the real ones', () => {
		expect(resolveMobileNavHrefs('/nope,/boxes,,/debts')).toEqual(['/', '/boxes', '/debts']);
	});

	test('resolving never rewrites the stored preference', () => {
		const stored = '/transactions,/subscriptions,/debts';
		resolveMobileNavHrefs(stored);
		expect(stored).toBe('/transactions,/subscriptions,/debts');
	});
});

describe('Boxes overview plan state', () => {
	test('an active Saving Goal is reported with its type and status', async () => {
		const backend = createFixtureBackend('FX-PLAN-GOAL-ACTIVE-01');
		const data = await runBoxes(backend);

		expect(boxPlanState(data.planSections[9210])).toMatchObject({
			kind: 'active',
			plan: { type: 'SAVING_GOAL', status: 'ACTIVE' },
		});
	});

	test('an overdue Saving Goal keeps its own status', async () => {
		const backend = createFixtureBackend('FX-PLAN-GOAL-OVERDUE-01');
		const data = await runBoxes(backend);

		expect(boxPlanState(data.planSections[9211])).toMatchObject({
			kind: 'active',
			plan: { type: 'SAVING_GOAL', status: 'OVERDUE' },
		});
	});

	test('a completed goal leaves the Box with no active plan', async () => {
		const backend = createFixtureBackend('FX-PLAN-GOAL-COMPLETED-01');
		const data = await runBoxes(backend);

		expect(boxPlanState(data.planSections[9212])).toEqual({ kind: 'none' });
	});

	test('an underfunded Spending Budget is reported as an active budget', async () => {
		const backend = createFixtureBackend('FX-PLAN-BUDGET-UNDER-01');
		const data = await runBoxes(backend);

		expect(boxPlanState(data.planSections[9213])).toMatchObject({
			kind: 'active',
			plan: { type: 'SPENDING_BUDGET', status: 'ACTIVE' },
		});
	});

	test('an empty plan list is no plan; a 404 remains unavailable', async () => {
		const backend = createFixtureBackend('FX-BOX-NOPLAN-01');
		const data = await runBoxes(backend);
		expect(boxPlanState(data.planSections[9214])).toEqual({ kind: 'none' });

		const absent = createFixtureBackend('FX-BOX-NOPLAN-01', {
			failures: { 'GET /api/boxes/9214/plans': { kind: 'status', status: 404 } },
		});
		const absentData = await runBoxes(absent);
		expect(boxPlanState(absentData.planSections[9214])).toEqual({ kind: 'unavailable' });
	});

	test('a failed plan list is unavailable, and does not make the Box look unplanned', async () => {
		const backend = createFixtureBackend('FX-BOX-NOPLAN-01', {
			failures: { 'GET /api/boxes/9215/plans': { kind: 'status', status: 500 } },
		});
		const data = await runBoxes(backend);

		expect(data.planSections[9215]).toMatchObject({ status: 'unavailable', reason: 'error' });
		expect(boxPlanState(data.planSections[9215])).toEqual({ kind: 'unavailable' });
		// The sibling Box is unaffected: one failure cannot discard the other.
		expect(boxPlanState(data.planSections[9214])).toEqual({ kind: 'none' });
	});

	test('an unreachable plan list is unavailable, not empty', async () => {
		const backend = createFixtureBackend('FX-BOX-NOPLAN-01', {
			failures: { 'GET /api/boxes/9215/plans': { kind: 'unreachable' } },
		});
		const data = await runBoxes(backend);

		expect(data.planSections[9215]).toMatchObject({ status: 'unavailable', reason: 'unreachable' });
	});

	test('a malformed plan payload is unavailable rather than a Box with no plan', async () => {
		const backend = createFixtureBackend('FX-PLAN-GOAL-ACTIVE-01', {
			routes: { 'GET /api/boxes/9210/plans': [{ id: 9251, boxId: 9210, type: 'MYSTERY' }] },
		});
		const data = await runBoxes(backend);

		expect(data.planSections[9210]).toMatchObject({ status: 'unavailable', reason: 'invalid' });
		expect(boxPlanState(data.planSections[9210])).toEqual({ kind: 'unavailable' });
	});

	test('the archived list is a separate route key from the active one', async () => {
		const backend = createFixtureBackend('FX-BOX-NOPLAN-01');
		const data = await runBoxes(backend);

		expect(data.boxes.map((box) => box.id)).toEqual([9214, 9215]);
		expect(data.archivedBoxes).toEqual([]);
		expect(backend.requests.map((request) => request.key)).toContain(
			'GET /api/boxes?archived=true',
		);
	});
});

describe('Slice 0B fixture values', () => {
	test('the plan fixtures assert the documented figures', () => {
		expect(loadScenario('FX-PLAN-GOAL-ACTIVE-01').expected).toMatchObject({
			boxBalance: 3_000,
			targetAmount: 12_000,
			remainingAmount: 9_000,
			progressPercent: 25,
		});
		expect(loadScenario('FX-PLAN-GOAL-OVERDUE-01').expected).toMatchObject({
			boxBalance: 1_200,
			remainingAmount: 3_800,
			arrears: 800,
		});
		expect(loadScenario('FX-PLAN-BUDGET-UNDER-01').expected).toMatchObject({
			desiredBalance: 2_000,
			boxBalance: 450,
			suggestedTopUp: 1_550,
		});
	});

	test('the underfunded budget top-up is desired balance minus Box balance', () => {
		const { expected } = loadScenario('FX-PLAN-BUDGET-UNDER-01');
		expect(expected.desiredBalance - expected.boxBalance).toBe(expected.suggestedTopUp);
	});

	test('the owner-participating split covers the Owner and both Members', () => {
		const { expected } = loadScenario('FX-SUB-SHARED-OWNER-PARTIAL-01');
		expect(expected.cost / expected.splitCount).toBe(expected.shareAmount);
		expect(expected.memberCount * expected.shareAmount).toBe(expected.expectedContributions);
		expect(expected.collectedContributions + expected.outstandingContributions).toBe(
			expected.expectedContributions,
		);
		expect(expected.ownShare).toBe(expected.shareAmount);
	});

	test('the middleman split excludes the Owner from the cost', () => {
		const { expected } = loadScenario('FX-SUB-SHARED-MIDDLEMAN-PARTIAL-01');
		expect(expected.ownerParticipates).toBe(false);
		expect(expected.cost / expected.splitCount).toBe(expected.shareAmount);
		expect(expected.expectedContributions).toBe(expected.cost);
		expect(expected.ownShare).toBe(0);
	});

	test('credit in favor and an unpaid confirmed statement are separate figures', () => {
		const { expected } = loadScenario('FX-CREDIT-INFAVOR-STMT-01');
		expect(expected.creditLimit + expected.creditInFavor).toBe(expected.availableCredit);
		// The outstanding statement payment is an obligation, never subtracted
		// from Net Balance.
		expect(expected.netBalance).toBe(1_085);
		expect(expected.outstandingBalance).toBe(640);
	});

	test('FX-DASH-NEG-01 holds the arithmetic Phase 4 asserts', () => {
		const { expected } = loadScenario('FX-DASH-NEG-01');
		expect(expected.moneyHeld + expected.creditInFavor).toBeCloseTo(expected.netBalance, 2);
		expect(expected.netBalance - expected.inBoxes).toBeCloseTo(expected.availableToSpend, 2);
		expect(expected.creditLimit + expected.creditInFavor).toBeCloseTo(expected.availableCredit, 2);
		expect(expected.outstandingStatementPayment).toBe(310.25);
	});

	test('a scenario is a deep copy, so 0B data cannot leak between tests', () => {
		const first = loadScenario('FX-DASH-NEG-01');
		first.expected.netBalance = 1;
		expect(loadScenario('FX-DASH-NEG-01').expected.netBalance).toBe(4_176);
	});
});
