// @ts-nocheck
/**
 * Slice 3C: money owed to the User — outstanding totals, the debtor
 * drill-down, and the Transaction one Debt Payment creates.
 *
 * These drive the real `/debts` and `/debts/[id]` loaders and actions against
 * the synthetic fixtures, so they check what the pages actually produce. They
 * assert amounts and single-record effects, not wording.
 */

import { describe, expect, test } from 'bun:test';
import { createFixtureBackend } from './fixtures/backend';
import { load as debtsLoad } from '../src/routes/debts/+page.server';
import { actions as debtActions } from '../src/routes/debts/[id]/+page.server';

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

describe('outstanding money owed to the User', () => {
	test('totals only what is still unpaid, grouped per debtor', async () => {
		const backend = createFixtureBackend('FX-DEBT-RECEIVABLES-01');
		const expected = backend.scenario.expected;
		const data = await runDebtsLoad(backend);

		expect(data.receivables.totalOutstanding).toBe(expected.totalOutstanding);
		expect(data.receivables.outstandingDebtCount).toBe(expected.outstandingDebtCount);
		expect(data.receivables.debtors).toHaveLength(expected.debtorCount);

		// The per-debtor figures must add up to the headline total; no debtor
		// total is computed by a route the page does not also show.
		const summed = data.receivables.debtors.reduce((total, debtor) => total + debtor.outstanding, 0);
		expect(summed).toBe(expected.totalOutstanding);

		// Largest first, which is the order the cards render in.
		expect(data.receivables.debtors[0].key).toBe(expected.topDebtorKey);
		expect(data.receivables.debtors[0].outstanding).toBe(expected.topDebtorOutstanding);

		const grouped = data.receivables.debtors.find((d) => d.key === expected.groupedDebtorKey);
		expect(grouped.outstanding).toBe(expected.groupedDebtorOutstanding);
		expect(grouped.debtCount).toBe(2);

		backend.assertNoUndeclaredRoutes();
	});

	test('a settled debt is excluded and a debt without a Contact stands alone', async () => {
		const backend = createFixtureBackend('FX-DEBT-RECEIVABLES-01');
		const expected = backend.scenario.expected;
		const data = await runDebtsLoad(backend);

		const settled = data.debts.find((debt) => debt.id === expected.settledDebtId);
		expect(settled.status).toBe('PAID');
		expect(settled.remaining).toBe(0);
		expect(
			data.receivables.debtors.some((debtor) => debtor.outstanding === settled.totalAmount),
		).toBe(false);

		const unlinked = data.receivables.debtors.find((d) => d.key === expected.unlinkedDebtorKey);
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

describe('debtor drill-down', () => {
	test('?debtor= opens one debtor without changing the totals', async () => {
		const backend = createFixtureBackend('FX-DEBT-RECEIVABLES-01');
		const expected = backend.scenario.expected;
		const data = await runDebtsLoad(backend, `?debtor=${expected.groupedDebtorKey}`);

		expect(data.debtorFilter).toBe(expected.groupedDebtorKey);
		// Drilling down is a view of the same records: the headline total still
		// describes everything owed, not the opened debtor alone.
		expect(data.receivables.totalOutstanding).toBe(expected.totalOutstanding);
		expect(data.debts).toHaveLength(5);
	});

	test('an unknown or paid-off debtor key falls back to showing everyone', async () => {
		const backend = createFixtureBackend('FX-DEBT-RECEIVABLES-01');
		const data = await runDebtsLoad(backend, '?debtor=contact-9599');
		expect(data.debtorFilter).toBeNull();
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
