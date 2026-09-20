// @ts-nocheck
/**
 * Debts in both directions — outstanding totals per side, the counterpart
 * drill-down, and the Transaction one Debt Payment creates.
 *
 * These drive the real `/debts` and `/debts/[id]` loaders and actions against
 * the synthetic fixtures, so they check what the pages actually produce. They
 * assert amounts and single-record effects, not wording.
 */

import { describe, expect, test } from 'bun:test';
import { stringify } from 'devalue';
import { createFixtureBackend } from './fixtures/backend';
import { load as debtsLoad } from '../src/routes/debts/+page.server';
import {
	load as debtDetailLoad,
	actions as debtActions,
} from '../src/routes/debts/[id]/+page.server';
import { counterpartKey, summarizeDebts } from '../src/lib/debts';

function cookies() {
	const store = new Map();
	return { get: (name) => store.get(name), set: (name, value) => store.set(name, value) };
}

function runDebtsLoad(backend, search = '') {
	return debtsLoad({
		url: new URL(`http://app.test/debts${search}`),
		fetch: backend.fetch,
		cookies: cookies(),
		parent: async () => ({ preferences: { locale: 'es', timeZone: 'America/Mexico_City' } }),
	});
}

function runDebtDetailLoad(backend, id) {
	return debtDetailLoad({
		params: { id: String(id) },
		fetch: backend.fetch,
		cookies: cookies(),
		parent: async () => ({ preferences: { locale: 'es', timeZone: 'America/Mexico_City' } }),
	});
}

function runRecordPayment(backend, id, fields) {
	const body = new FormData();
	for (const [name, value] of Object.entries(fields)) body.append(name, String(value));
	return debtActions.recordPayment({
		params: { id: String(id) },
		request: new Request(`http://app.test/debts/${id}?/recordPayment`, { method: 'POST', body }),
		fetch: backend.fetch,
		cookies: cookies(),
	});
}

// The form is `dataType: 'json'` so the Box funding array survives the round
// trip; that is the encoding Superforms actually posts, and a urlencoded body
// would flatten the array into strings.
function runRecordJsonPayment(backend, id, payload) {
	const body = new FormData();
	body.set('__superform_json', stringify({ boxFunding: [], ...payload }));
	return debtActions.recordPayment({
		params: { id: String(id) },
		request: new Request(`http://app.test/debts/${id}?/recordPayment`, { method: 'POST', body }),
		fetch: backend.fetch,
		cookies: cookies(),
	});
}

function paymentBodySentTo(backend, id) {
	const write = backend.requests.find(
		(request) => request.key === `POST /api/debts/${id}/payments`,
	);
	return write ? JSON.parse(write.body) : null;
}

describe('outstanding money owed to the User', () => {
	test('totals only what is still unpaid, grouped per debtor', async () => {
		const backend = createFixtureBackend('FX-DEBT-RECEIVABLES-01');
		const expected = backend.scenario.expected;
		const data = await runDebtsLoad(backend);
		const owedToYou = data.summary.owedToYou;

		expect(owedToYou.totalOutstanding).toBe(expected.totalOutstanding);
		expect(owedToYou.outstandingDebtCount).toBe(expected.outstandingDebtCount);
		expect(owedToYou.counterparts).toHaveLength(expected.debtorCount);

		// The per-debtor figures must add up to the headline total; no debtor
		// total is computed by a route the page does not also show.
		const summed = owedToYou.counterparts.reduce((total, debtor) => total + debtor.outstanding, 0);
		expect(summed).toBe(expected.totalOutstanding);

		// Largest first, which is the order the cards render in.
		expect(owedToYou.counterparts[0].key).toBe(expected.topDebtorKey);
		expect(owedToYou.counterparts[0].outstanding).toBe(expected.topDebtorOutstanding);

		const grouped = owedToYou.counterparts.find((d) => d.key === expected.groupedDebtorKey);
		expect(grouped.outstanding).toBe(expected.groupedDebtorOutstanding);
		expect(grouped.debtCount).toBe(2);

		// Nothing is owed by the User in this scenario, and an empty side is
		// zero rather than a total borrowed from the other one.
		expect(data.summary.youOwe.totalOutstanding).toBe(0);
		expect(data.summary.youOwe.counterparts).toHaveLength(0);

		backend.assertNoUndeclaredRoutes();
	});

	test('a settled debt is excluded and a debt without a Contact stands alone', async () => {
		const backend = createFixtureBackend('FX-DEBT-RECEIVABLES-01');
		const expected = backend.scenario.expected;
		const data = await runDebtsLoad(backend);
		const owedToYou = data.summary.owedToYou;

		const settled = data.debts.find((debt) => debt.id === expected.settledDebtId);
		expect(settled.status).toBe('PAID');
		expect(settled.remaining).toBe(0);
		expect(
			owedToYou.counterparts.some((debtor) => debtor.outstanding === settled.totalAmount),
		).toBe(false);

		const unlinked = owedToYou.counterparts.find((d) => d.key === expected.unlinkedDebtorKey);
		expect(unlinked.contactId).toBeNull();
		expect(unlinked.debtCount).toBe(1);
	});

	test('a failed debt read cannot become an empty or zero receivables summary', async () => {
		const backend = createFixtureBackend('FX-DEBT-RECEIVABLES-01', {
			failures: { 'GET /api/debts': { kind: 'status', status: 500 } },
		});
		await expect(runDebtsLoad(backend)).rejects.toMatchObject({ status: 502 });
	});

	test('reading the page issues no writes', async () => {
		const backend = createFixtureBackend('FX-DEBT-RECEIVABLES-01');
		await runDebtsLoad(backend);
		expect(backend.requests.every((request) => request.method === 'GET')).toBe(true);
	});
});

describe('counterpart drill-down', () => {
	test('?counterpart= opens one counterpart without changing the totals', async () => {
		const backend = createFixtureBackend('FX-DEBT-RECEIVABLES-01');
		const expected = backend.scenario.expected;
		const data = await runDebtsLoad(backend, `?counterpart=${expected.groupedDebtorKey}`);

		expect(data.counterpartFilter).toBe(expected.groupedDebtorKey);
		// Drilling down is a view of the same records: the headline total still
		// describes everything owed, not the opened counterpart alone.
		expect(data.summary.owedToYou.totalOutstanding).toBe(expected.totalOutstanding);
		expect(data.debts).toHaveLength(5);
	});

	test('an unknown or settled counterpart key falls back to showing everyone', async () => {
		const backend = createFixtureBackend('FX-DEBT-RECEIVABLES-01');
		const data = await runDebtsLoad(backend, '?counterpart=in-contact-9599');
		expect(data.counterpartFilter).toBeNull();
		expect(data.directionFilter).toBe('ALL');
	});

	test('an opened counterpart decides the direction shown, whatever ?direction= says', async () => {
		const backend = createFixtureBackend('FX-DEBT-BIDIRECTIONAL-01');
		const expected = backend.scenario.expected;
		const data = await runDebtsLoad(
			backend,
			`?counterpart=${expected.youOweKey}&direction=INGRESS`,
		);

		expect(data.counterpartFilter).toBe(expected.youOweKey);
		expect(data.directionFilter).toBe('EGRESS');
	});
});

describe('debts in both directions', () => {
	test('each side totals only its own debts', async () => {
		const backend = createFixtureBackend('FX-DEBT-BIDIRECTIONAL-01');
		const expected = backend.scenario.expected;
		const data = await runDebtsLoad(backend);
		const { owedToYou, youOwe } = data.summary;

		expect(owedToYou.totalOutstanding).toBe(expected.owedToYouTotal);
		expect(owedToYou.outstandingDebtCount).toBe(expected.owedToYouDebtCount);
		expect(owedToYou.counterparts).toHaveLength(expected.owedToYouCounterpartCount);

		expect(youOwe.totalOutstanding).toBe(expected.youOweTotal);
		expect(youOwe.outstandingDebtCount).toBe(expected.youOweDebtCount);
		expect(youOwe.counterparts).toHaveLength(expected.youOweCounterpartCount);

		// The two sides are never netted into one figure.
		expect(owedToYou.totalOutstanding).not.toBe(
			expected.youOweTotal - expected.owedToYouTotal,
		);

		backend.assertNoUndeclaredRoutes();
	});

	test('one Contact on both sides is two counterparts, not one net balance', async () => {
		const backend = createFixtureBackend('FX-DEBT-BIDIRECTIONAL-01');
		const expected = backend.scenario.expected;
		const data = await runDebtsLoad(backend);

		const owed = data.summary.owedToYou.counterparts.find((c) => c.key === expected.owedToYouKey);
		const owing = data.summary.youOwe.counterparts.find((c) => c.key === expected.youOweKey);

		expect(owed.contactId).toBe(expected.sharedContactId);
		expect(owing.contactId).toBe(expected.sharedContactId);
		expect(owed.direction).toBe('INGRESS');
		expect(owing.direction).toBe('EGRESS');
		expect(owed.outstanding).toBe(expected.owedToYouTotal);
		expect(owing.outstanding).toBe(expected.youOweOutstandingForSharedContact);
	});

	test('a debt the User owes with no Contact stands alone on its own side', async () => {
		const backend = createFixtureBackend('FX-DEBT-BIDIRECTIONAL-01');
		const expected = backend.scenario.expected;
		const data = await runDebtsLoad(backend);

		const unlinked = data.summary.youOwe.counterparts.find(
			(c) => c.key === expected.unlinkedYouOweKey,
		);
		expect(unlinked.contactId).toBeNull();
		expect(unlinked.debtCount).toBe(1);
		expect(
			data.summary.owedToYou.counterparts.some((c) => c.key === expected.unlinkedYouOweKey),
		).toBe(false);
	});

	test('a settled debt is excluded from its side', async () => {
		const backend = createFixtureBackend('FX-DEBT-BIDIRECTIONAL-01');
		const expected = backend.scenario.expected;
		const data = await runDebtsLoad(backend);

		const settled = data.debts.find((debt) => debt.id === expected.settledDebtId);
		expect(settled.status).toBe('PAID');
		expect(data.summary.owedToYou.outstandingDebtCount).toBe(expected.owedToYouDebtCount);
	});

	test('?direction= narrows to one side and is ignored when unrecognized', async () => {
		const backend = createFixtureBackend('FX-DEBT-BIDIRECTIONAL-01');
		expect((await runDebtsLoad(backend, '?direction=EGRESS')).directionFilter).toBe('EGRESS');
		expect((await runDebtsLoad(backend, '?direction=INGRESS')).directionFilter).toBe('INGRESS');
		expect((await runDebtsLoad(backend, '?direction=sideways')).directionFilter).toBe('ALL');
		expect((await runDebtsLoad(backend)).directionFilter).toBe('ALL');
	});

	test('narrowing to one side never changes the other side\'s total', async () => {
		const backend = createFixtureBackend('FX-DEBT-BIDIRECTIONAL-01');
		const expected = backend.scenario.expected;
		const data = await runDebtsLoad(backend, '?direction=EGRESS');

		expect(data.summary.owedToYou.totalOutstanding).toBe(expected.owedToYouTotal);
		expect(data.summary.youOwe.totalOutstanding).toBe(expected.youOweTotal);
	});
});

describe('recording a Debt Payment', () => {
	test('one payment issues one request and reports the one Transaction it created', async () => {
		const backend = createFixtureBackend('FX-DEBT-010-01', {
			routes: {
				'POST /api/debts/9902/payments': {
					id: 9706,
					debtId: 9902,
					amount: 250,
					paymentDate: '2026-09-07',
					transactionId: 9611,
					notes: null,
					createdAt: '2026-09-07T18:00:00Z',
				},
			},
		});

		const result = await runRecordPayment(backend, 9902, {
			amount: '250',
			paymentDate: '2026-09-07',
			categoryId: '9801',
			notes: '',
		});

		// ADR-0005: exactly one INGRESS Transaction, created by the backend.
		expect(result.recordedPayment).toEqual({
			debtId: 9902,
			paymentId: 9706,
			amount: 250,
			transactionId: 9611,
		});
		const writes = backend.requests.filter((request) => request.method !== 'GET');
		expect(writes.map((request) => request.key)).toEqual(['POST /api/debts/9902/payments']);
	});

	test('an unreported Transaction id stays null rather than being guessed', async () => {
		const backend = createFixtureBackend('FX-DEBT-010-01', {
			routes: {
				'POST /api/debts/9902/payments': {
					id: 9707,
					debtId: 9902,
					amount: 250,
					paymentDate: '2026-09-07',
					transactionId: null,
					notes: null,
					createdAt: '2026-09-07T18:00:00Z',
				},
			},
		});

		const result = await runRecordPayment(backend, 9902, {
			amount: '250',
			paymentDate: '2026-09-07',
			categoryId: '9801',
			notes: '',
		});

		expect(result.recordedPayment.transactionId).toBeNull();
	});

	test('a failed payment reports no Transaction', async () => {
		const backend = createFixtureBackend('FX-DEBT-010-01', {
			failures: { 'POST /api/debts/9902/payments': { kind: 'status', status: 500 } },
		});

		const result = await runRecordPayment(backend, 9902, {
			amount: '250',
			paymentDate: '2026-09-07',
			categoryId: '9801',
			notes: '',
		});

		expect(result.status).toBe(502);
		expect(result.data.recordedPayment).toBeUndefined();
	});
});

describe('a debt the User owes', () => {
	test('its detail page carries the direction the payment form has to follow', async () => {
		const backend = createFixtureBackend('FX-DEBT-BIDIRECTIONAL-01');
		const expected = backend.scenario.expected;
		const data = await runDebtDetailLoad(backend, expected.egressDebtId);

		expect(data.debt.direction).toBe('EGRESS');
		// The page filters this list by the Direction, so an EGRESS Category has
		// to be reachable or the form would open with nothing to choose.
		expect(data.categories.some((category) => category.type === 'EGRESS')).toBe(true);
		expect(data.form.data.amount).toBe(data.debt.remaining);
	});

	test('recording a payment posts once and reports the one Transaction', async () => {
		const backend = createFixtureBackend('FX-DEBT-BIDIRECTIONAL-01', {
			routes: {
				'POST /api/debts/9911/payments': {
					id: 9711,
					debtId: 9911,
					amount: 300,
					paymentDate: '2026-09-07',
					transactionId: 9614,
					notes: null,
					createdAt: '2026-09-07T18:00:00Z',
				},
			},
		});

		const result = await runRecordPayment(backend, 9911, {
			amount: '300',
			paymentDate: '2026-09-07',
			categoryId: '9802',
			notes: '',
		});

		expect(result.recordedPayment).toEqual({
			debtId: 9911,
			paymentId: 9711,
			amount: 300,
			transactionId: 9614,
		});
		const writes = backend.requests.filter((request) => request.method !== 'GET');
		expect(writes.map((request) => request.key)).toEqual(['POST /api/debts/9911/payments']);
	});
});

describe('grouping debts by counterpart', () => {
	// Debts recorded before the two directions existed carry no `direction`,
	// and back then every Debt was money owed to the User.
	test('a debt with no direction is money owed to the User', () => {
		const summary = summarizeDebts(
			[{ id: 1, contactId: 7, direction: undefined, remaining: 100, status: 'ACTIVE' }],
			() => 'Contacto',
		);

		expect(summary.owedToYou.totalOutstanding).toBe(100);
		expect(summary.youOwe.totalOutstanding).toBe(0);
		expect(summary.owedToYou.counterparts[0].direction).toBe('INGRESS');
	});

	test('the same Contact on either side gets a different key', () => {
		const owed = { id: 1, contactId: 7, direction: 'INGRESS', remaining: 100, status: 'ACTIVE' };
		const owing = { id: 2, contactId: 7, direction: 'EGRESS', remaining: 100, status: 'ACTIVE' };

		expect(counterpartKey(owed)).not.toBe(counterpartKey(owing));

		const summary = summarizeDebts([owed, owing], () => 'Contacto');
		expect(summary.owedToYou.counterparts).toHaveLength(1);
		expect(summary.youOwe.counterparts).toHaveLength(1);
	});

	test('a debt with no Contact is keyed by its own id, per side', () => {
		const owing = { id: 42, contactId: null, direction: 'EGRESS', remaining: 50, status: 'ACTIVE' };
		expect(counterpartKey(owing)).toBe('out-debt-42');
	});
});

describe('paying a debt the User owes from a Box', () => {
	const paymentRoute = {
		'POST /api/debts/9911/payments': {
			id: 9712,
			debtId: 9911,
			amount: 300,
			paymentDate: '2026-09-07',
			transactionId: 9615,
			notes: null,
			createdAt: '2026-09-07T18:00:00Z',
		},
	};

	test('the detail page offers the Boxes the funding editor draws from', async () => {
		const backend = createFixtureBackend('FX-DEBT-BIDIRECTIONAL-01');
		const expected = backend.scenario.expected;
		const data = await runDebtDetailLoad(backend, expected.egressDebtId);

		const box = data.boxes.find((candidate) => candidate.id === expected.fundingBoxId);
		expect(box.balance).toBe(expected.fundingBoxBalance);
		// The form opens with nothing allocated: funding is something the User
		// asks for, never a default drawn from their reserved money.
		expect(data.form.data.boxFunding).toEqual([]);
	});

	test('the chosen allocation is forwarded to the backend unchanged', async () => {
		const backend = createFixtureBackend('FX-DEBT-BIDIRECTIONAL-01', { routes: paymentRoute });
		const expected = backend.scenario.expected;

		const result = await runRecordJsonPayment(backend, expected.egressDebtId, {
			amount: 300,
			paymentDate: '2026-09-07',
			categoryId: 9802,
			accountId: '',
			notes: '',
			boxFunding: [{ boxId: expected.fundingBoxId, amount: 180 }],
		});

		expect(result.recordedPayment.transactionId).toBe(9615);
		expect(paymentBodySentTo(backend, expected.egressDebtId).boxFunding).toEqual([
			{ boxId: expected.fundingBoxId, amount: 180 },
		]);
	});

	test('a payment with no allocation posts an empty funding list, not a missing one', async () => {
		const backend = createFixtureBackend('FX-DEBT-BIDIRECTIONAL-01', { routes: paymentRoute });
		const expected = backend.scenario.expected;

		await runRecordJsonPayment(backend, expected.egressDebtId, {
			amount: 300,
			paymentDate: '2026-09-07',
			categoryId: 9802,
			accountId: '',
			notes: '',
		});

		expect(paymentBodySentTo(backend, expected.egressDebtId).boxFunding).toEqual([]);
	});

	test('funding beyond the payment amount is rejected before any request is made', async () => {
		const backend = createFixtureBackend('FX-DEBT-BIDIRECTIONAL-01', { routes: paymentRoute });
		const expected = backend.scenario.expected;

		const result = await runRecordJsonPayment(backend, expected.egressDebtId, {
			amount: 300,
			paymentDate: '2026-09-07',
			categoryId: 9802,
			accountId: '',
			notes: '',
			boxFunding: [{ boxId: expected.fundingBoxId, amount: 400 }],
		});

		expect(result.status).toBe(400);
		expect(paymentBodySentTo(backend, expected.egressDebtId)).toBeNull();
	});

	test('a Box that cannot cover its line is reported as a conflict, not a dead end', async () => {
		const backend = createFixtureBackend('FX-DEBT-BIDIRECTIONAL-01', {
			failures: { 'POST /api/debts/9911/payments': { kind: 'status', status: 409 } },
		});
		const expected = backend.scenario.expected;

		const result = await runRecordJsonPayment(backend, expected.egressDebtId, {
			amount: 300,
			paymentDate: '2026-09-07',
			categoryId: 9802,
			accountId: '',
			notes: '',
			boxFunding: [{ boxId: expected.fundingBoxId, amount: 180 }],
		});

		expect(result.status).toBe(409);
		expect(result.data.form.message).toBeTruthy();
		expect(result.data.recordedPayment).toBeUndefined();
	});

	test('the same Box twice is rejected before any request is made', async () => {
		const backend = createFixtureBackend('FX-DEBT-BIDIRECTIONAL-01', { routes: paymentRoute });
		const expected = backend.scenario.expected;

		const result = await runRecordJsonPayment(backend, expected.egressDebtId, {
			amount: 300,
			paymentDate: '2026-09-07',
			categoryId: 9802,
			accountId: '',
			notes: '',
			boxFunding: [
				{ boxId: expected.fundingBoxId, amount: 100 },
				{ boxId: expected.fundingBoxId, amount: 100 },
			],
		});

		expect(result.status).toBe(400);
		expect(paymentBodySentTo(backend, expected.egressDebtId)).toBeNull();
	});
});
