// @ts-nocheck
/**
 * Slice 5C: the planning page's server loader, option catalogs, guidance
 * copy.
 *
 * The loader runs against the fixture backend exactly as in production. It
 * must read complete catalogs (not the dashboard's capped expected-money
 * list), mark a failed catalog unavailable instead of empty, and never write.
 */

import { describe, expect, test } from 'bun:test';
import { readFileSync } from 'node:fs';
import { createFixtureBackend } from './fixtures/backend';
import { instantOf } from './fixtures/clock';
import { loadScenario } from './fixtures/scenarios';
import { load as planningLoad } from '../src/routes/planning/+page.server';
import { userToday } from '../src/lib/obligation-status';
import { SUBSCRIPTION_READ_CONCURRENCY, mapWithConcurrency } from '../src/lib/server/planning-options';
import {
	KNOWN_MISSING_INPUT_REASONS,
	KNOWN_NOTES,
	KNOWN_TIMING_REASONS,
	localIssueMessage,
	missingInputMessage,
	noteMessage,
	timingReasonMessage,
} from '../src/lib/planning-labels';


function cookies() {
	return { get: () => undefined, set: () => {} };
}

async function runLoader(backend, timeZone = 'America/Mexico_City') {
	const data = await planningLoad({
		fetch: backend.fetch,
		cookies: cookies(),
		parent: async () => ({ obligationToday: userToday(timeZone, instantOf(backend.scenario.clock)) }),
	});
	backend.assertNoUndeclaredRoutes();
	// Loading the page reads; it never posts a preview or writes anything.
	expect(backend.requests.every((request) => request.method === 'GET')).toBe(true);
	return data;
}

const ids = (section) => section.data.map((option) => option.id);

describe('planning loader', () => {
	test('offers every active Box, including one with no plan, from the Box list', async () => {
		const data = await runLoader(createFixtureBackend('FX-HORIZON-UI-01'));
		expect(data.options.boxes).toEqual({
			status: 'ok',
			data: [
				{ id: 9240, name: 'Renta sintética H', balance: 2_500 },
				{ id: 9241, name: 'Viajes sintéticos H', balance: 500 },
			],
		});
	});

	test('offers only eligible receipts: ACTIVE INGRESS Debts and PENDING Member records', async () => {
		const scenario = loadScenario('FX-HORIZON-UI-01');
		const data = await runLoader(createFixtureBackend('FX-HORIZON-UI-01'));
		expect(ids(data.options.debts).join(',')).toBe(scenario.expected.selectableDebtIds);
		expect(ids(data.options.paymentRecords).join(',')).toBe(scenario.expected.selectablePaymentRecordIds);
		expect(data.options.paymentRecords.data[0]).toMatchObject({
			subscriptionName: 'Streaming sintético H',
			memberName: 'Miembro sintético A',
		});
	});

	test('the window is thirty days from the User’s own today', async () => {
		const data = await runLoader(createFixtureBackend('FX-HORIZON-UI-01'));
		expect(data.window).toEqual({ from: '2026-09-20', to: '2026-10-19' });
	});

	test('an unusable zone yields no window rather than UTC', async () => {
		const data = await runLoader(createFixtureBackend('FX-HORIZON-ZONE-01'), 'Mars/Olympus_Mons');
		expect(data.window).toBeNull();
	});

	test('long catalogs are complete, not capped like the dashboard', async () => {
		const expected = loadScenario('FX-HORIZON-LONG-01').expected;
		const data = await runLoader(createFixtureBackend('FX-HORIZON-LONG-01'));
		expect(data.options.debts.data).toHaveLength(expected.selectableDebts);
		expect(data.options.paymentRecords.data).toHaveLength(expected.selectablePaymentRecords);
		expect(data.options.boxes.data).toHaveLength(expected.boxes);
	});

	test('each failed catalog is unavailable on its own, never an empty list', async () => {
		for (const [route, section] of [
			['GET /api/debts', 'debts'],
			['GET /api/boxes', 'boxes'],
			['GET /api/subscriptions', 'paymentRecords'],
			['GET /api/subscriptions/9447/payments', 'paymentRecords'],
		]) {
			const backend = createFixtureBackend('FX-HORIZON-UI-01', {
				failures: { [route]: { kind: 'status', status: 500 } },
			});
			const data = await runLoader(backend);
			expect(data.options[section].status).toBe('unavailable');
			for (const other of ['boxes', 'debts', 'paymentRecords'].filter((name) => name !== section)) {
				expect(data.options[other].status).toBe('ok');
			}
		}
	});

	test('an unreachable backend is unavailable too', async () => {
		const backend = createFixtureBackend('FX-HORIZON-UI-01', {
			failures: { 'GET /api/debts': { kind: 'unreachable' } },
		});
		expect((await runLoader(backend)).options.debts).toEqual({ status: 'unavailable', reason: 'unreachable' });
	});

	test('unreadable Member names keep the records, unnamed', async () => {
		const backend = createFixtureBackend('FX-HORIZON-UI-01', {
			failures: { 'GET /api/subscriptions/9440/members': { kind: 'status', status: 503 } },
		});
		const data = await runLoader(backend);
		expect(ids(data.options.paymentRecords)).toEqual([9443, 9444]);
		expect(data.options.paymentRecords.data.every((option) => option.memberName === null)).toBe(true);
	});

	test('per-Subscription reads are bounded, complete and kept in order', async () => {
		// Every tab return reloads these catalogs, so many Subscriptions must not
		// fan out into one burst of requests.
		const scenario = loadScenario('FX-HORIZON-UI-01');
		const template = scenario.routes['GET /api/subscriptions'][0];
		const payments = scenario.routes['GET /api/subscriptions/9440/payments'];
		const members = scenario.routes['GET /api/subscriptions/9440/members'];
		const count = SUBSCRIPTION_READ_CONCURRENCY * 3 + 1;
		const subscriptions = Array.from({ length: count }, (_, index) => ({
			...template,
			id: 95_000 + index,
			name: `Sintética ${index}`,
		}));
		const routes = { 'GET /api/subscriptions': subscriptions };
		for (const subscription of subscriptions) {
			routes[`GET /api/subscriptions/${subscription.id}/payments`] = payments.map((record) => ({
				...record,
				id: record.id * 1_000 + subscription.id,
				subscriptionId: subscription.id,
			}));
			routes[`GET /api/subscriptions/${subscription.id}/members`] = members;
		}
		const backend = createFixtureBackend('FX-HORIZON-UI-01', { routes });

		let inFlight = 0;
		let peak = 0;
		const perSubscription = /\/api\/subscriptions\/\d+\//;
		const slowFetch = async (input, init) => {
			if (!perSubscription.test(String(input))) return backend.fetch(input, init);
			inFlight += 1;
			peak = Math.max(peak, inFlight);
			try {
				await new Promise((resolve) => setTimeout(resolve, 1));
				return await backend.fetch(input, init);
			} finally {
				inFlight -= 1;
			}
		};
		const data = await runLoader({ ...backend, fetch: slowFetch });

		// Two reads (payments, Members) per Subscription in flight at most.
		expect(peak).toBeGreaterThan(2);
		expect(peak).toBeLessThanOrEqual(SUBSCRIPTION_READ_CONCURRENCY * 2);
		expect(backend.requests.filter((request) => perSubscription.test(request.url))).toHaveLength(count * 2);
		expect(data.options.paymentRecords.status).toBe('ok');
		expect(new Set(data.options.paymentRecords.data.map((option) => option.subscriptionId)).size).toBe(count);
	});

	test('bounded mapping keeps input order and never exceeds its limit', async () => {
		let inFlight = 0;
		let peak = 0;
		const result = await mapWithConcurrency([5, 1, 4, 2, 3, 0], 2, async (value) => {
			inFlight += 1;
			peak = Math.max(peak, inFlight);
			await new Promise((resolve) => setTimeout(resolve, value));
			inFlight -= 1;
			return value * 10;
		});
		expect(result).toEqual([50, 10, 40, 20, 30, 0]);
		expect(peak).toBe(2);
		expect(await mapWithConcurrency([], 4, async (value) => value)).toEqual([]);
	});

	test('a malformed Debt row fails the catalog rather than hiding one Debt', async () => {
		const debts = loadScenario('FX-HORIZON-UI-01').routes['GET /api/debts'];
		const backend = createFixtureBackend('FX-HORIZON-UI-01', {
			routes: { 'GET /api/debts': [...debts, { id: 9999, direction: 'INGRESS', status: 'ACTIVE', remaining: '10' }] },
		});
		expect((await runLoader(backend)).options.debts.status).toBe('unavailable');
	});
});

/** Enum constants declared in a Java source file's `enum Name { ... }`. */
function javaEnum(file, name) {
	const source = readFileSync(
		new URL(`../../backend/src/main/java/com/keenti/finances/${file}`, import.meta.url),
		'utf8',
	);
	const body = source.slice(source.indexOf(`enum ${name} {`) + `enum ${name} {`.length);
	return body
		.slice(0, body.indexOf('}'))
		.replace(/\/\/.*$/gm, '')
		.split(',')
		.map((token) => token.trim())
		.filter(Boolean);
}

describe('guidance for every server code', () => {
	const context = {
		window: { from: '2026-09-20', to: '2026-10-19' },
		date: (value) => value,
		money: (value) => `$${value}`,
		boxName: () => 'Renta',
		accountName: () => 'Tarjeta',
	};
	const unknownMissing = missingInputMessage(
		{ reason: 'NOT_A_CODE', itemIndex: 0, receiptIndex: null, boxId: null, shortfall: null },
		context,
	);

	test('the known code lists are exactly the backend enums', () => {
		expect([...KNOWN_MISSING_INPUT_REASONS].sort()).toEqual(
			javaEnum('domain/model/PlanningPreviewCalculator.java', 'Reason').sort(),
		);
		expect([...KNOWN_TIMING_REASONS].sort()).toEqual(
			javaEnum('domain/model/PlanningPreview.java', 'TimingReasonCode').sort(),
		);
		expect([...KNOWN_NOTES].sort()).toEqual(javaEnum('domain/model/PlanningPreview.java', 'Note').sort());
	});

	test('every projection reason has its own guidance, naming the row it concerns', () => {
		for (const reason of KNOWN_MISSING_INPUT_REASONS) {
			const onCost = missingInputMessage(
				{ reason, itemIndex: 2, receiptIndex: null, boxId: 9240, shortfall: 500 },
				context,
			);
			// Dedicated guidance never falls back to echoing the raw code.
			expect(onCost).not.toContain(reason);
			expect(onCost.length).toBeGreaterThan(10);
		}
		const rowSpecific = ['DATE_OUT_OF_WINDOW', 'INVALID_AMOUNT', 'BOX_NOT_FOUND', 'COST_NOT_CONFIRMED_UNRECORDED'];
		for (const reason of rowSpecific) {
			const one = missingInputMessage({ reason, itemIndex: 0, receiptIndex: null, boxId: null, shortfall: null }, context);
			const three = missingInputMessage({ reason, itemIndex: 2, receiptIndex: null, boxId: null, shortfall: null }, context);
			expect(one).not.toBe(three);
		}
		const capacity = missingInputMessage(
			{ reason: 'BOX_CAPACITY_EXCEEDED', itemIndex: null, receiptIndex: null, boxId: 9240, shortfall: 500 },
			context,
		);
		expect(capacity).toContain('Renta');
		expect(capacity).toContain('$500');
	});

	test('an unknown code is shown, not hidden', () => {
		expect(unknownMissing).toContain('NOT_A_CODE');
		expect(timingReasonMessage({ code: 'NEW_TIMING', accountId: null, statementId: null }, context)).toContain(
			'NEW_TIMING',
		);
		expect(noteMessage('NEW_NOTE', 0)).toContain('NEW_NOTE');
	});

	test('every timing reason and note has dedicated text', () => {
		for (const code of KNOWN_TIMING_REASONS) {
			expect(timingReasonMessage({ code, accountId: 9120, statementId: 9310 }, context)).not.toContain(code);
		}
		for (const code of KNOWN_NOTES) expect(noteMessage(code, 3)).not.toContain(code);
	});

	test('every local issue has guidance', () => {
		for (const issue of [
			'AMOUNT_REQUIRED',
			'AMOUNT_FORMAT',
			'DATE_REQUIRED',
			'DESCRIPTION_TOO_LONG',
			'BOX_AMOUNT_REQUIRED',
			'BOX_AMOUNT_FORMAT',
			'BOX_UNAVAILABLE',
			'RECEIPT_DATE_REQUIRED',
		]) {
			expect(localIssueMessage(issue, context).length).toBeGreaterThan(10);
		}
	});

	test('every planning message exists in both languages', () => {
		const en = JSON.parse(readFileSync(new URL('../messages/en.json', import.meta.url), 'utf8'));
		const es = JSON.parse(readFileSync(new URL('../messages/es.json', import.meta.url), 'utf8'));
		const planning = (messages) => Object.keys(messages).filter((key) => key.includes('planning')).sort();
		expect(planning(en)).toEqual(planning(es));
		expect(planning(en).length).toBeGreaterThan(100);
	});
});

