// @ts-nocheck
/**
 * Slice 1B: the Debt Payment form must not open in an error state.
 *
 * These tests drive the real `/debts/[id]` loader and action against the
 * Slice 0A debt fixtures, so they check what the page actually produces
 * rather than a re-implementation of superforms.
 */

import { describe, expect, test } from 'bun:test';
import { createFixtureBackend } from './fixtures/backend';
import { load as debtLoad, actions as debtActions } from '../src/routes/debts/[id]/+page.server';

function cookies() {
	const store = new Map();
	return { get: (name) => store.get(name), set: (name, value) => store.set(name, value) };
}

function runLoad(backend, id) {
	return debtLoad({
		params: { id: String(id) },
		fetch: backend.fetch,
		cookies: cookies(),
		parent: async () => ({ preferences: { locale: 'es', timeZone: 'America/Mexico_City' } }),
	});
}

function formRequest(fields) {
	const body = new FormData();
	for (const [name, value] of Object.entries(fields)) body.append(name, String(value));
	return new Request('http://app.test/debts/9901?/recordPayment', { method: 'POST', body });
}

function runRecordPayment(backend, id, fields) {
	return debtActions.recordPayment({
		params: { id: String(id) },
		request: formRequest(fields),
		fetch: backend.fetch,
		cookies: cookies(),
	});
}

describe('debt payment form initial state', () => {
	test('an untouched form carries no validation errors', async () => {
		const backend = createFixtureBackend('FX-DEBT-000-01');
		const data = await runLoad(backend, 9901);

		// `categoryId: 0` means "not chosen yet"; it must not be reported as a
		// required-field error before the User has touched anything.
		expect(data.form.errors).toEqual({});
		expect(data.form.valid).toBe(false);
		expect(data.form.data.categoryId).toBe(0);
		expect(data.form.data.amount).toBe(data.debt.remaining);
		expect(backend.requests.every((request) => request.method === 'GET')).toBe(true);
	});

	test('a partly paid debt also opens clean, prefilled with what is left', async () => {
		const backend = createFixtureBackend('FX-DEBT-010-01');
		const data = await runLoad(backend, 9902);

		expect(data.form.errors).toEqual({});
		expect(data.form.data.amount).toBe(900);
	});
});

describe('debt payment validation after submission', () => {
	test('submitting without a Category still fails with the required-field error', async () => {
		const backend = createFixtureBackend('FX-DEBT-000-01');
		const result = await runRecordPayment(backend, 9901, {
			amount: '100',
			paymentDate: '2026-09-07',
			categoryId: '0',
			notes: '',
		});

		expect(result.status).toBe(400);
		expect(result.data.form.errors.categoryId?.length).toBeGreaterThan(0);
		// An invalid submission never reaches the backend.
		expect(backend.requests.some((request) => request.method === 'POST')).toBe(false);
	});

	test('a non-positive amount is rejected after submission', async () => {
		const backend = createFixtureBackend('FX-DEBT-000-01');
		const result = await runRecordPayment(backend, 9901, {
			amount: '0',
			paymentDate: '2026-09-07',
			categoryId: '9801',
			notes: '',
		});

		expect(result.status).toBe(400);
		expect(result.data.form.errors.amount?.length).toBeGreaterThan(0);
	});

	test('a complete submission records exactly one Debt Payment request', async () => {
		const backend = createFixtureBackend('FX-DEBT-000-01', {
			routes: {
				'POST /api/debts/9901/payments': {
					id: 9705,
					debtId: 9901,
					amount: 100,
					paymentDate: '2026-09-07',
					transactionId: 9610,
					notes: null,
					createdAt: '2026-09-07T18:00:00Z',
				},
			},
		});
		const result = await runRecordPayment(backend, 9901, {
			amount: '100',
			paymentDate: '2026-09-07',
			categoryId: '9801',
			notes: '',
		});

		expect(result.form.valid).toBe(true);
		const writes = backend.requests.filter((request) => request.method !== 'GET');
		expect(writes.map((request) => request.key)).toEqual(['POST /api/debts/9901/payments']);
	});
});
