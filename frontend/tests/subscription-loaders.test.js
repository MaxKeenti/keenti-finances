// @ts-nocheck
/**
 * Slice 3B at request level: the Subscriptions overview loader.
 *
 * These drive the production loader with the synthetic 0A/0B fixture backend.
 * The subject is what the page is *given* when a read fails — before this
 * slice, a failed Subscriptions request became `[]` and the page totalled it
 * into a confident 0.00 monthly commitment.
 */

import { describe, expect, test } from 'bun:test';
import { createFixtureBackend } from './fixtures/backend';
import { HARNESS_ROUTES } from './fixtures/harness-routes';
import { load as subscriptionsLoad } from '../src/routes/subscriptions/+page.server';
import { load as subscriptionLoad } from '../src/routes/subscriptions/[id]/+page.server';
import { priceEquivalents } from '../src/lib/subscription-summary';
import { billingGenerationStatus } from '../src/lib/obligation-status';
import { loadScenario } from './fixtures/scenarios';

function cookies(values = {}) {
	const store = new Map(Object.entries(values));
	return { get: (name) => store.get(name), set: (name, value) => store.set(name, value), store };
}

async function runList(backend) {
	backend.declareDefaultRoutes(HARNESS_ROUTES);
	const data = await subscriptionsLoad({
		fetch: backend.fetch,
		cookies: cookies(),
		parent: async () => ({ preferences: { timeZone: 'America/Mexico_City', locale: 'es' } }),
	});
	backend.assertNoUndeclaredRoutes();
	return data;
}

async function runDetail(backend, id) {
	const data = await subscriptionLoad({
		params: { id },
		fetch: backend.fetch,
		cookies: cookies(),
	});
	backend.assertNoUndeclaredRoutes();
	return data;
}

/** Visiting a page must never write. */
function expectReadOnly(backend) {
	expect(backend.requests.every((request) => request.method === 'GET')).toBe(true);
}

describe('subscriptions overview loader', () => {
	test('a populated list resolves with its member lists attached', async () => {
		const backend = createFixtureBackend('FX-SUB-LIST-01');
		const data = await runList(backend);

		expect(data.subscriptions.status).toBe('ok');
		expect(data.subscriptions.data).toHaveLength(3);

		const shared = data.subscriptions.data.find((sub) => sub.id === 9408);
		expect(shared.members).toEqual({
			status: 'ok',
			data: loadScenario('FX-SUB-LIST-01').routes['GET /api/subscriptions/9408/members'],
		});
		// A Personal Subscription has no member list to read at all.
		expect(data.subscriptions.data.find((sub) => sub.id === 9410).members).toBeUndefined();
		expectReadOnly(backend);
	});

	test('a failed subscriptions read is unavailable, never an empty list', async () => {
		const backend = createFixtureBackend('FX-SUB-LIST-01', {
			failures: { 'GET /api/subscriptions': { kind: 'status', status: 500 } },
		});
		const data = await runList(backend);

		expect(data.subscriptions).toEqual({ status: 'unavailable', reason: 'error' });
		// The page therefore has no total to show, rather than 0.00.
		expect(priceEquivalents(null)).toBeNull();
		// No member list is requested for a list that could not be read.
		expect(backend.requests.some((request) => request.url.includes('/members'))).toBe(false);
	});

	test('an unreachable backend is unavailable rather than an empty commitment', async () => {
		const backend = createFixtureBackend('FX-SUB-LIST-01', {
			failures: { 'GET /api/subscriptions': { kind: 'unreachable' } },
		});
		const data = await runList(backend);

		expect(data.subscriptions).toEqual({ status: 'unavailable', reason: 'unreachable' });
	});

	test('an unreadable price fails the list instead of silently understating the total', async () => {
		const backend = createFixtureBackend('FX-SUB-LIST-01', {
			routes: {
				'GET /api/subscriptions': [
					{ id: 9408, name: 'Sin precio', cost: null, billingCycle: 'MONTHLY', type: 'PERSONAL', nextBillingDate: '2026-09-15' },
				],
			},
		});
		const data = await runList(backend);

		expect(data.subscriptions).toEqual({ status: 'unavailable', reason: 'invalid' });
	});

	test('one failed member list does not empty another subscription’s members', async () => {
		const backend = createFixtureBackend('FX-SUB-LIST-01', {
			failures: { 'GET /api/subscriptions/9408/members': { kind: 'status', status: 503 } },
		});
		const data = await runList(backend);

		const failed = data.subscriptions.data.find((sub) => sub.id === 9408);
		const loaded = data.subscriptions.data.find((sub) => sub.id === 9409);
		expect(failed.members).toEqual({ status: 'unavailable', reason: 'error' });
		// A genuinely member-less Subscription still reports an ok empty list.
		expect(loaded.members).toEqual({ status: 'ok', data: [] });
		// The prices were readable, so the totals survive the member outage.
		expect(priceEquivalents(data.subscriptions.data).monthly).toBe(850);
	});

	test('failed categories and contacts do not take the subscriptions with them', async () => {
		const backend = createFixtureBackend('FX-SUB-LIST-01', {
			failures: {
				'GET /api/categories': { kind: 'status', status: 500 },
				'GET /api/contacts': { kind: 'unreachable' },
			},
		});
		const data = await runList(backend);

		expect(data.categories).toEqual({ status: 'unavailable', reason: 'error' });
		expect(data.contacts).toEqual({ status: 'unavailable', reason: 'unreachable' });
		expect(data.subscriptions.status).toBe('ok');
	});

	test('visiting the overview issues no writes and never generates billing', async () => {
		const backend = createFixtureBackend('FX-SUB-LIST-01');
		await runList(backend);

		expectReadOnly(backend);
		expect(backend.requests.some((request) => request.url.includes('generate-billing'))).toBe(false);
	});
});

describe('subscription detail loader', () => {
	test('visiting the detail page issues no writes and does not advance the cursor', async () => {
		const backend = createFixtureBackend('FX-SUB-SHARED-OWNER-PARTIAL-01');
		const data = await runDetail(backend, '9406');

		expect(data.subscription.nextBillingDate).toBe('2026-09-15');
		expectReadOnly(backend);
		expect(backend.requests.some((request) => request.url.includes('generate-billing'))).toBe(false);
	});

	test('an unreadable generation cursor keeps the rest of the subscription', async () => {
		const scenario = loadScenario('FX-SUB-SHARED-OWNER-PARTIAL-01');
		const backend = createFixtureBackend('FX-SUB-SHARED-OWNER-PARTIAL-01', {
			routes: {
				'GET /api/subscriptions/9406': {
					...scenario.routes['GET /api/subscriptions/9406'],
					nextBillingDate: 'not-a-date',
				},
			},
		});
		const data = await runDetail(backend, '9406');

		// The cursor alone is unreadable. Failing the whole read over it took the
		// price, the split and the stored records down with it and left the page
		// unable to load at all.
		expect(data.subscription.nextBillingDate).toBeNull();
		expect(data.subscription.cost).toBe(600);
		expect(data.payments.status).toBe('ok');
		// And the cursor reports unavailable rather than a guessed day.
		expect(
			billingGenerationStatus({
				nextBillingDate: data.subscription.nextBillingDate,
				today: '2026-09-07',
			}).state,
		).toBe('unavailable');
		expectReadOnly(backend);
	});

	test('a member-less shared subscription reports an ok empty list, not a failure', async () => {
		const backend = createFixtureBackend('FX-SUB-EMPTY-01');
		const data = await runDetail(backend, '9402');

		expect(data.members).toEqual({ status: 'ok', data: [] });
		expect(data.payments).toEqual({ status: 'ok', data: [] });
	});
});
