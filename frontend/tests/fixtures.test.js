// @ts-nocheck
import { describe, expect, test } from 'bun:test';
import { createFixtureBackend } from './fixtures/backend';
import { loadScenario, scenarioIds } from './fixtures/scenarios';
import {
	MEXICO_CITY_EVENING,
	TOKYO_SAME_INSTANT,
	instantOf,
	withFixtureClock,
} from './fixtures/clock';
import { dateInTimeZone } from '../src/lib/formatting';

async function readJson(backend, path) {
	const response = await backend.fetch(`http://backend${path}`);
	expect(response.ok).toBe(true);
	return response.json();
}

describe('fixture scenarios', () => {
	test('every scenario has a unique id and a declared clock', () => {
		const ids = scenarioIds();
		expect(new Set(ids).size).toBe(ids.length);

		for (const id of ids) {
			const scenario = loadScenario(id);
			expect(scenario.clock.timeZone.length).toBeGreaterThan(0);
			expect(Number.isNaN(instantOf(scenario.clock).getTime())).toBe(false);
		}
	});

	test('loading a scenario twice yields equal but independent data', () => {
		const first = loadScenario('FX-BAL-OVERRESERVED-01');
		const second = loadScenario('FX-BAL-OVERRESERVED-01');

		expect(second).toEqual(first);

		first.routes['GET /api/accounts'][0].balance = 999_999;
		expect(loadScenario('FX-BAL-OVERRESERVED-01').routes['GET /api/accounts'][0].balance).toBe(
			3_200,
		);
		expect(second.routes['GET /api/accounts'][0].balance).toBe(3_200);
	});

	test('an unknown scenario id fails loudly', () => {
		expect(() => loadScenario('FX-DOES-NOT-EXIST')).toThrow(/Unknown fixture scenario/);
	});
});

describe('balance fixtures', () => {
	test('excessive Box reservations produce a negative Available to Spend', async () => {
		const backend = createFixtureBackend('FX-BAL-OVERRESERVED-01');
		const { expected } = backend.scenario;
		const summary = await readJson(backend, '/api/dashboard/summary?year=2026');

		expect(expected.moneyHeld + expected.creditInFavor).toBe(expected.netBalance);
		expect(expected.netBalance - expected.inBoxes).toBe(expected.availableToSpend);
		expect(summary.netBalance).toBe(expected.netBalance);
		expect(summary.inBoxes).toBe(expected.inBoxes);
		expect(summary.availableToSpend).toBe(expected.availableToSpend);
		expect(summary.netBalance).toBeGreaterThan(0);
		expect(summary.availableToSpend).toBeLessThan(0);
	});

	test('a negative Net Balance is a different state from over-reserving', async () => {
		const backend = createFixtureBackend('FX-BAL-NEGNET-01');
		const summary = await readJson(backend, '/api/dashboard/summary?year=2026');

		expect(summary.netBalance).toBeLessThan(0);
		expect(summary.inBoxes).toBe(0);
		expect(summary.availableToSpend).toBe(summary.netBalance);
	});

	test('a legitimate zero reports active tracking with zero totals', async () => {
		const backend = createFixtureBackend('FX-BAL-ZERO-01');
		const status = await readJson(backend, '/api/accounts/status');
		const summary = await readJson(backend, '/api/dashboard/summary?year=2026');

		expect(status.active).toBe(true);
		expect(summary.netBalance).toBe(0);
		expect(summary.availableToSpend).toBe(0);
	});

	test('tracking inactive and active are separate fixtures', async () => {
		const inactive = createFixtureBackend('FX-TRACK-INACTIVE-01');
		const active = createFixtureBackend('FX-TRACK-ACTIVE-01');

		expect((await readJson(inactive, '/api/accounts/status')).setupRequired).toBe(true);
		expect((await readJson(active, '/api/accounts/status')).active).toBe(true);
		expect((await readJson(active, '/api/accounts')).length).toBe(1);
	});

	test('an empty Box coexists with a negative Available to Spend', async () => {
		const backend = createFixtureBackend('FX-BOX-EMPTY-NEGAVAIL-01');
		const boxes = await readJson(backend, '/api/boxes');
		const summary = await readJson(backend, '/api/dashboard/summary?year=2026');
		const emptyBox = boxes.find((box) => box.id === backend.scenario.expected.emptyBoxId);

		expect(emptyBox.balance).toBe(0);
		expect(summary.availableToSpend).toBe(-700);
		expect(summary.netBalance - summary.inBoxes).toBe(summary.availableToSpend);
	});

	test('a genuine statement mismatch is reported by the record, not inferred', async () => {
		const backend = createFixtureBackend('FX-STMT-MISMATCH-01');
		const [statement] = await readJson(backend, '/api/accounts/9108/credit-statements');

		expect(statement.reconciliationMismatch).toBe(true);
		expect(statement.mismatchAmount).toBe(125.5);
		expect(statement.outstandingBalance).toBe(1_250);
	});
});

describe('subscription and debt fixtures', () => {
	test('a populated shared subscription has members and payments', async () => {
		const backend = createFixtureBackend('FX-SUB-SHARED-POPULATED-01');

		expect((await readJson(backend, '/api/subscriptions/9401/members')).length).toBe(2);
		expect((await readJson(backend, '/api/subscriptions/9401/payments')).length).toBe(3);
	});

	test('an empty subscription is genuinely empty rather than unavailable', async () => {
		const backend = createFixtureBackend('FX-SUB-EMPTY-01');
		const response = await backend.fetch('http://backend/api/subscriptions/9402/members');

		expect(response.status).toBe(200);
		expect(await response.json()).toEqual([]);
	});

	test('debt fixtures cover 0%, 10% and 100% progress', async () => {
		for (const [id, debtId, percent] of [
			['FX-DEBT-000-01', 9901, 0],
			['FX-DEBT-010-01', 9902, 10],
			['FX-DEBT-100-01', 9903, 100],
		]) {
			const backend = createFixtureBackend(id);
			const debt = await readJson(backend, `/api/debts/${debtId}`);

			expect((debt.totalPaid / debt.totalAmount) * 100).toBe(percent);
			expect(debt.remaining).toBe(debt.totalAmount - debt.totalPaid);
		}
	});
});

describe('controlled clock', () => {
	test('the boundary fixture resolves to different calendar days per time zone', () => {
		const { expected } = loadScenario('FX-DATE-BOUNDARY-01');

		expect(dateInTimeZone(MEXICO_CITY_EVENING.timeZone, instantOf(MEXICO_CITY_EVENING))).toBe(
			expected.localToday,
		);
		expect(dateInTimeZone(TOKYO_SAME_INSTANT.timeZone, instantOf(TOKYO_SAME_INSTANT))).toBe(
			expected.utcToday,
		);
	});

	test('yesterday, today and tomorrow obligations bracket the local day', async () => {
		const backend = createFixtureBackend('FX-DATE-BOUNDARY-01');
		const { expected } = backend.scenario;
		const subscriptions = await readJson(backend, '/api/subscriptions');
		const [statement] = await readJson(backend, '/api/accounts/9110/credit-statements');

		expect(subscriptions.map((s) => s.nextBillingDate)).toEqual([
			expected.yesterday,
			expected.localToday,
			expected.tomorrow,
		]);
		expect(statement.dueDate).toBe(expected.yesterday);
	});

	test('the pinned clock is scoped to the callback and then restored', async () => {
		const before = Date.now();

		const pinned = await withFixtureClock(MEXICO_CITY_EVENING, () => Date.now());
		expect(pinned).toBe(instantOf(MEXICO_CITY_EVENING).getTime());
		expect(new Date().getTime()).toBeGreaterThanOrEqual(before);

		await expect(
			withFixtureClock(MEXICO_CITY_EVENING, () => {
				throw new Error('boom');
			}),
		).rejects.toThrow('boom');
		expect(Date.now()).toBeGreaterThanOrEqual(before);
	});
});

describe('fixture backend isolation', () => {
	test('an undeclared route fails loudly instead of returning empty data', async () => {
		const backend = createFixtureBackend('FX-BAL-ZERO-01');

		await expect(backend.fetch('http://backend/api/subscriptions')).rejects.toThrow(
			/does not declare GET \/api\/subscriptions/,
		);
	});

	test('undeclared routes are recorded so an outage test can prove none were called', async () => {
		const backend = createFixtureBackend('FX-BAL-ZERO-01');

		expect(backend.undeclaredRequests).toEqual([]);
		expect(() => backend.assertNoUndeclaredRoutes()).not.toThrow();

		await expect(backend.fetch('http://backend/api/subscriptions')).rejects.toThrow();

		expect(backend.undeclaredRequests.map((request) => request.key)).toEqual([
			'GET /api/subscriptions',
		]);
		expect(() => backend.assertNoUndeclaredRoutes()).toThrow(
			/undeclared route\(s\): GET \/api\/subscriptions/,
		);
	});

	test('an injected outage is not mistaken for an undeclared route', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', {
			failures: { 'GET /api/boxes/summary': { kind: 'unreachable' } },
		});

		await expect(backend.fetch('http://backend/api/boxes/summary')).rejects.toBeInstanceOf(
			TypeError,
		);

		expect(backend.undeclaredRequests).toEqual([]);
		expect(() => backend.assertNoUndeclaredRoutes()).not.toThrow();
	});

	test('a caller that swallows the rejection still leaves the undeclared call recorded', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', {
			failures: { 'GET /api/boxes/summary': { kind: 'unreachable' } },
		});

		// How a loader behaves: both calls reject, and both look identical to it.
		for (const path of ['/api/boxes/summary', '/api/boxes/sumary']) {
			try {
				await backend.fetch(`http://backend${path}`);
			} catch {
				// Treated as "unavailable", exactly as the loader would.
			}
		}

		expect(backend.undeclaredRequests.map((request) => request.key)).toEqual([
			'GET /api/boxes/sumary',
		]);
		expect(() => backend.assertNoUndeclaredRoutes()).toThrow(/GET \/api\/boxes\/sumary/);
	});

	test('reset clears recorded undeclared routes', async () => {
		const backend = createFixtureBackend('FX-BAL-ZERO-01');

		await expect(backend.fetch('http://backend/api/subscriptions')).rejects.toThrow();
		backend.reset();

		expect(backend.undeclaredRequests).toEqual([]);
		expect(() => backend.assertNoUndeclaredRoutes()).not.toThrow();
	});

	test('requests are recorded and reset without leaking between uses', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01');

		await backend.fetch('http://backend/api/accounts/status');
		expect(backend.requests.map((request) => request.key)).toEqual(['GET /api/accounts/status']);

		backend.reset();
		expect(backend.requests).toEqual([]);
	});

	test('two backends for the same scenario do not share state', async () => {
		const first = createFixtureBackend('FX-TRACK-ACTIVE-01');
		const second = createFixtureBackend('FX-TRACK-ACTIVE-01');

		first.failRoute('GET /api/accounts/status', { kind: 'status', status: 500 });

		expect((await first.fetch('http://backend/api/accounts/status')).status).toBe(500);
		expect((await second.fetch('http://backend/api/accounts/status')).status).toBe(200);
		expect(second.requests.length).toBe(1);
	});

	test('reset clears failures injected after construction but keeps the initial ones', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', {
			failures: { 'GET /api/accounts': { kind: 'status', status: 503 } },
		});

		backend.failRoute('GET /api/accounts/status', { kind: 'status', status: 500 });
		backend.reset();

		expect((await backend.fetch('http://backend/api/accounts/status')).status).toBe(200);
		expect((await backend.fetch('http://backend/api/accounts')).status).toBe(503);
	});
});

describe('failure injection', () => {
	test('a failed summary is a real error rather than a fabricated zero', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', {
			failures: { 'GET /api/dashboard/summary': { kind: 'status', status: 500 } },
		});

		const failed = await backend.fetch('http://backend/api/dashboard/summary?year=2026');
		expect(failed.ok).toBe(false);
		expect(failed.status).toBe(500);

		// The successful sections of the same scenario stay usable.
		expect((await readJson(backend, '/api/accounts')).length).toBe(1);
	});

	test('an unreachable backend rejects the way a network failure does', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', {
			failures: { 'GET /api/boxes/summary': { kind: 'unreachable' } },
		});

		await expect(backend.fetch('http://backend/api/boxes/summary')).rejects.toBeInstanceOf(
			TypeError,
		);
	});

	test('subscription sections can fail partially while others succeed', async () => {
		const backend = createFixtureBackend('FX-SUB-SHARED-POPULATED-01', {
			failures: { 'GET /api/subscriptions/9401/payments': { kind: 'status', status: 502 } },
		});

		expect((await readJson(backend, '/api/subscriptions/9401/members')).length).toBe(2);
		expect((await backend.fetch('http://backend/api/subscriptions/9401/payments')).status).toBe(502);
		expect((await readJson(backend, '/api/subscriptions/9401')).id).toBe(9401);
	});

	test('a healed route serves its fixture body again', async () => {
		const backend = createFixtureBackend('FX-BAL-ZERO-01', {
			failures: { 'GET /api/accounts/status': { kind: 'status', status: 503 } },
		});

		expect((await backend.fetch('http://backend/api/accounts/status')).status).toBe(503);
		backend.healRoute('GET /api/accounts/status');
		expect((await readJson(backend, '/api/accounts/status')).active).toBe(true);
	});
});
