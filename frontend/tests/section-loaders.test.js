// @ts-nocheck
/**
 * Slice 1A-loader: per-section availability in the real route loaders.
 *
 * These tests import the production loaders (`src/routes/...`) and drive them
 * with the Slice 0A fixture backend, so they check the orchestration that
 * actually runs on the server rather than a re-implementation of it.
 */

import { describe, expect, test } from 'bun:test';
import { createFixtureBackend } from './fixtures/backend';
import { load as dashboardLoad } from '../src/routes/+page.server';
import { load as layoutLoad } from '../src/routes/+layout.server';
import { load as subscriptionLoad } from '../src/routes/subscriptions/[id]/+page.server';

function cookies(values = {}) {
	const store = new Map(Object.entries(values));
	return {
		get: (name) => store.get(name),
		set: (name, value) => store.set(name, value),
		store,
	};
}

function runDashboard(backend, year = 2026) {
	return dashboardLoad({
		fetch: backend.fetch,
		url: new URL(`http://app.test/?year=${year}`),
		cookies: cookies({ PARAGLIDE_LOCALE: 'es' }),
	});
}

function runLayout(backend) {
	return layoutLoad({
		locals: { session: { user: { id: 'fixture-user' } } },
		fetch: backend.fetch,
		cookies: cookies(),
		url: new URL('http://app.test/transactions'),
	});
}

function runSubscription(backend, id) {
	return subscriptionLoad({
		params: { id },
		fetch: backend.fetch,
		cookies: cookies(),
	});
}

/** No page read may create, update, or delete anything. */
function expectReadOnly(backend) {
	expect(backend.requests.every((request) => request.method === 'GET')).toBe(true);
}

describe('dashboard loader sections', () => {
	test('a successful load reports the fixture figures', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01');
		const data = await runDashboard(backend);

		expect(data.summary).toMatchObject({
			status: 'ok',
			data: { netBalance: 2_500.75, inBoxes: 500, availableToSpend: 2_000.75 },
		});
		expect(data.accountWarnings).toMatchObject({ status: 'ok', data: { partial: false } });
		expectReadOnly(backend);
	});

	test('a genuine zero stays an ok section, not an unavailable one', async () => {
		const backend = createFixtureBackend('FX-BAL-ZERO-01');
		const data = await runDashboard(backend);

		expect(data.summary).toMatchObject({
			status: 'ok',
			data: { netBalance: 0, inBoxes: 0, availableToSpend: 0 },
		});
	});

	test('a non-OK summary is unavailable and keeps the warnings section', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', {
			failures: { 'GET /api/dashboard/summary': { kind: 'status', status: 500 } },
		});
		const data = await runDashboard(backend);

		expect(data.summary).toEqual({ status: 'unavailable', reason: 'error' });
		expect(data.accountWarnings.status).toBe('ok');
	});

	test('a rejected summary request does not discard the successful section', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', {
			failures: { 'GET /api/dashboard/summary': { kind: 'unreachable' } },
		});
		const data = await runDashboard(backend);

		expect(data.summary).toEqual({ status: 'unavailable', reason: 'unreachable' });
		expect(data.accountWarnings.status).toBe('ok');
		expect(backend.requests.some((request) => request.key === 'GET /api/accounts')).toBe(true);
	});

	test('a partial but successful payload cannot manufacture a zero balance', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', {
			// HTTP 200 with the money fields missing: the pre-1A loader spread this
			// over a zero-filled default and presented 0.00 as the Net Balance.
			routes: { 'GET /api/dashboard/summary': { year: 2026, monthly: [] } },
		});
		const data = await runDashboard(backend);

		expect(data.summary).toEqual({ status: 'unavailable', reason: 'invalid' });
		expect(JSON.stringify(data.summary)).not.toContain('netBalance');
	});

	test('a non-numeric money field is rejected rather than coerced', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', {
			routes: {
				'GET /api/dashboard/summary': {
					year: 2026,
					netBalance: '2500.75',
					inBoxes: 500,
					availableToSpend: 2_000.75,
					totalIngress: 0,
					totalEgress: 0,
					monthly: [],
				},
			},
		});
		const data = await runDashboard(backend);

		expect(data.summary).toEqual({ status: 'unavailable', reason: 'invalid' });
	});

	test('one unreadable credit card marks the warnings partial and keeps the rest', async () => {
		const backend = createFixtureBackend('FX-BAL-OVERRESERVED-01', {
			failures: { 'GET /api/accounts/9104/credit-statements': { kind: 'status', status: 503 } },
		});
		const data = await runDashboard(backend);

		expect(data.accountWarnings).toMatchObject({ status: 'ok', data: { partial: true } });
		expect(data.summary.status).toBe('ok');
	});

	test('a credit account with no credit settings configured is not a service failure', async () => {
		// The backend answers 404 for "no Credit settings saved yet", which is the
		// normal state of a Credit Financial Account the User has not configured.
		// Reporting it as partial would leave a permanent "may be incomplete"
		// notice on a dashboard where nothing is actually wrong.
		const backend = createFixtureBackend('FX-BAL-OVERRESERVED-01', {
			failures: {
				'GET /api/accounts/9104/credit-settings': {
					kind: 'status',
					status: 404,
					body: { error: 'Credit settings not configured' },
				},
			},
		});
		const data = await runDashboard(backend);

		expect(data.accountWarnings).toEqual({ status: 'ok', data: { items: [], partial: false } });
	});

	test('a genuine credit-settings failure is still reported as partial', async () => {
		const backend = createFixtureBackend('FX-BAL-OVERRESERVED-01', {
			failures: { 'GET /api/accounts/9104/credit-settings': { kind: 'status', status: 500 } },
		});
		const data = await runDashboard(backend);

		expect(data.accountWarnings).toMatchObject({ status: 'ok', data: { partial: true } });
	});

	test('an unreachable credit-settings request is still reported as partial', async () => {
		const backend = createFixtureBackend('FX-BAL-OVERRESERVED-01', {
			failures: { 'GET /api/accounts/9104/credit-settings': { kind: 'unreachable' } },
		});
		const data = await runDashboard(backend);

		expect(data.accountWarnings).toMatchObject({ status: 'ok', data: { partial: true } });
	});

	test('a malformed credit-settings body is partial, not a missing limit', async () => {
		const backend = createFixtureBackend('FX-BAL-OVERRESERVED-01', {
			routes: { 'GET /api/accounts/9104/credit-settings': { creditLimit: 'ocho mil' } },
		});
		const data = await runDashboard(backend);

		expect(data.accountWarnings).toMatchObject({ status: 'ok', data: { partial: true } });
	});

	test('an unparseable statement due date fails that section instead of the render', async () => {
		const backend = createFixtureBackend('FX-BAL-OVERRESERVED-01', {
			routes: {
				'GET /api/accounts/9104/credit-statements': [
					{ dueDate: 'next Tuesday', outstandingBalance: 1_200 },
				],
			},
		});
		const data = await runDashboard(backend);

		expect(data.accountWarnings).toMatchObject({ status: 'ok', data: { partial: true } });
		expect(data.accountWarnings.data.items).toEqual([]);
	});

	test('an unreadable account list makes the warnings unavailable, not empty', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', {
			failures: { 'GET /api/accounts': { kind: 'unreachable' } },
		});
		const data = await runDashboard(backend);

		expect(data.accountWarnings).toEqual({ status: 'unavailable', reason: 'unreachable' });
	});

	test('retrying after the backend recovers returns the real figures', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', {
			failures: { 'GET /api/dashboard/summary': { kind: 'status', status: 500 } },
		});
		const failed = await runDashboard(backend);
		expect(failed.summary.status).toBe('unavailable');

		backend.healRoute('GET /api/dashboard/summary');
		const recovered = await runDashboard(backend);

		expect(recovered.summary).toMatchObject({ status: 'ok', data: { netBalance: 2_500.75 } });
		expectReadOnly(backend);
	});
});

describe('layout balance summary section', () => {
	test('a successful summary is passed through to the app shell', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01');
		const data = await runLayout(backend);

		expect(data.balanceSummary).toMatchObject({
			status: 'ok',
			data: { netBalance: 2_500.75, inBoxes: 500, availableToSpend: 2_000.75 },
		});
	});

	test('a failed summary is unavailable instead of a zero fallback', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', {
			failures: { 'GET /api/boxes/summary': { kind: 'status', status: 500 } },
		});
		const data = await runLayout(backend);

		expect(data.balanceSummary).toEqual({ status: 'unavailable', reason: 'error' });
		expect(JSON.stringify(data.balanceSummary)).not.toContain('availableToSpend');
	});

	test('an unreachable summary does not stop preferences from loading', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', {
			failures: { 'GET /api/boxes/summary': { kind: 'unreachable' } },
			routes: {
				'GET /api/user/preferences': {
					primaryHue: 91,
					headingFont: 'Fraunces',
					bodyFont: 'Geist',
					locale: 'en',
					transactionPageSize: 25,
					transactionSortBy: 'transactionDate',
					transactionSortDirection: 'desc',
					mobilePinnedNavItems: '/transactions',
					dockMagnification: true,
					timeZone: 'America/Mexico_City',
					themeMode: 'dark',
				},
			},
		});
		const data = await runLayout(backend);

		expect(data.balanceSummary).toEqual({ status: 'unavailable', reason: 'unreachable' });
		expect(data.preferences.locale).toBe('en');
	});

	test('a malformed summary body is unavailable, not a zero balance', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', {
			routes: { 'GET /api/boxes/summary': { netBalance: 2_500.75, inBoxes: null } },
		});
		const data = await runLayout(backend);

		expect(data.balanceSummary).toEqual({ status: 'unavailable', reason: 'invalid' });
	});

	test('a genuine zero balance still reaches the header', async () => {
		const backend = createFixtureBackend('FX-BAL-ZERO-01');
		const data = await runLayout(backend);

		expect(data.balanceSummary).toMatchObject({ status: 'ok', data: { availableToSpend: 0 } });
		expectReadOnly(backend);
	});
});

describe('subscription detail loader sections', () => {
	test('a populated subscription loads every section', async () => {
		const backend = createFixtureBackend('FX-SUB-SHARED-POPULATED-01');
		const data = await runSubscription(backend, '9401');

		expect(data.subscription.cost).toBe(299);
		expect(data.members.data).toHaveLength(2);
		expect(data.payments.data).toHaveLength(3);
		expect(data.linkedTransactions.data).toHaveLength(1);
		expect(data.unlinkedTransactions.data).toHaveLength(1);
		expectReadOnly(backend);
	});

	test('a genuinely empty subscription reports empty ok sections', async () => {
		const backend = createFixtureBackend('FX-SUB-EMPTY-01');
		const data = await runSubscription(backend, '9402');

		expect(data.members).toEqual({ status: 'ok', data: [] });
		expect(data.payments).toEqual({ status: 'ok', data: [] });
		expect(data.unlinkedTransactions).toEqual({ status: 'ok', data: [] });
	});

	test('failed payments leave the member list intact and are not an empty list', async () => {
		const backend = createFixtureBackend('FX-SUB-SHARED-POPULATED-01', {
			failures: { 'GET /api/subscriptions/9401/payments': { kind: 'status', status: 500 } },
		});
		const data = await runSubscription(backend, '9401');

		expect(data.payments).toEqual({ status: 'unavailable', reason: 'error' });
		expect(data.members.data).toHaveLength(2);
		expect(data.linkedTransactions.data).toHaveLength(1);
	});

	test('an unreachable members request does not discard the other sections', async () => {
		const backend = createFixtureBackend('FX-SUB-SHARED-POPULATED-01', {
			failures: { 'GET /api/subscriptions/9401/members': { kind: 'unreachable' } },
		});
		const data = await runSubscription(backend, '9401');

		expect(data.members).toEqual({ status: 'unavailable', reason: 'unreachable' });
		expect(data.payments.data).toHaveLength(3);
	});

	test('link candidates are unavailable when the transaction list fails', async () => {
		const backend = createFixtureBackend('FX-SUB-SHARED-POPULATED-01', {
			failures: { 'GET /api/transactions': { kind: 'status', status: 502 } },
		});
		const data = await runSubscription(backend, '9401');

		// Not "no candidates": the loader cannot tell, so linking is disabled.
		expect(data.unlinkedTransactions).toEqual({ status: 'unavailable', reason: 'error' });
		expect(data.linkedTransactions.data).toHaveLength(1);
	});

	test('a malformed payment amount makes that section unavailable', async () => {
		const backend = createFixtureBackend('FX-SUB-SHARED-POPULATED-01', {
			routes: {
				'GET /api/subscriptions/9401/payments': [
					{
						id: 9421,
						subscriptionId: 9401,
						memberId: 9411,
						billingDate: '2026-09-01',
						amount: null,
						status: 'PAID',
					},
				],
			},
		});
		const data = await runSubscription(backend, '9401');

		expect(data.payments).toEqual({ status: 'unavailable', reason: 'invalid' });
		expect(data.members.data).toHaveLength(2);
	});

	test('an unreadable transactionId does not present a payment as unlinked', async () => {
		// `transactionId: null` means the Payment Record was never settled by a
		// Transaction, and the page turns that into an offered link action. A
		// value we cannot read must not be flattened into that claim.
		const backend = createFixtureBackend('FX-SUB-SHARED-POPULATED-01', {
			routes: {
				'GET /api/subscriptions/9401/payments': [
					{
						id: 9421,
						subscriptionId: 9401,
						memberId: 9411,
						billingDate: '2026-09-01',
						amount: 99.67,
						status: 'PAID',
						paidDate: '2026-09-03',
						transactionId: 'nueve mil seiscientos uno',
					},
				],
			},
		});
		const data = await runSubscription(backend, '9401');

		expect(data.payments).toEqual({ status: 'unavailable', reason: 'invalid' });
		expect(JSON.stringify(data.payments)).not.toContain('transactionId');
	});

	test('an unreadable memberId is not silently attributed to the Owner', async () => {
		// `memberId === null` is rendered as the Subscription Owner's own share.
		const backend = createFixtureBackend('FX-SUB-SHARED-POPULATED-01', {
			routes: {
				'GET /api/subscriptions/9401/payments': [
					{
						id: 9421,
						subscriptionId: 9401,
						memberId: {},
						billingDate: '2026-09-01',
						amount: 99.67,
						status: 'PENDING',
						paidDate: null,
						transactionId: null,
					},
				],
			},
		});
		const data = await runSubscription(backend, '9401');

		expect(data.payments).toEqual({ status: 'unavailable', reason: 'invalid' });
	});

	test('an unreadable subscriptionId keeps a transaction out of the link candidates', async () => {
		// Offering an already-linked Transaction as a candidate would let the User
		// re-link it against another Subscription.
		const backend = createFixtureBackend('FX-SUB-SHARED-POPULATED-01', {
			routes: {
				'GET /api/transactions': [
					{
						id: 9603,
						amount: 99.67,
						direction: 'INGRESS',
						description: 'Ingreso sintético sin vincular',
						transactionDate: '2026-09-05',
						subscriptionId: '9401',
					},
				],
			},
		});
		const data = await runSubscription(backend, '9401');

		expect(data.unlinkedTransactions).toEqual({ status: 'unavailable', reason: 'invalid' });
		expect(data.linkedTransactions.data).toHaveLength(1);
	});

	test('an absent nullable link field still reads as not linked', async () => {
		const backend = createFixtureBackend('FX-SUB-SHARED-POPULATED-01', {
			routes: {
				'GET /api/subscriptions/9401/payments': [
					{
						id: 9421,
						subscriptionId: 9401,
						billingDate: '2026-09-01',
						amount: 99.67,
						status: 'PENDING',
					},
				],
			},
		});
		const data = await runSubscription(backend, '9401');

		expect(data.payments).toMatchObject({
			status: 'ok',
			data: [{ memberId: null, transactionId: null, paidDate: null }],
		});
	});

	test('a malformed billing date fails its section rather than the page render', async () => {
		// `periodLabel` formats this date; an invalid one used to reach `Intl` and
		// throw during render, replacing the whole page with an error.
		const backend = createFixtureBackend('FX-SUB-SHARED-POPULATED-01', {
			routes: {
				'GET /api/subscriptions/9401/payments': [
					{
						id: 9421,
						subscriptionId: 9401,
						memberId: 9411,
						billingDate: 'septiembre',
						amount: 99.67,
						status: 'PENDING',
						paidDate: null,
						transactionId: null,
					},
				],
			},
		});
		const data = await runSubscription(backend, '9401');

		expect(data.payments).toEqual({ status: 'unavailable', reason: 'invalid' });
		expect(data.members.data).toHaveLength(2);
	});

	test('a malformed transaction date fails only the section that received it', async () => {
		const backend = createFixtureBackend('FX-SUB-SHARED-POPULATED-01', {
			routes: {
				'GET /api/subscriptions/9401/linked-transactions': [
					{
						id: 9601,
						amount: 99.67,
						direction: 'INGRESS',
						description: 'Aportación sintética',
						transactionDate: '03/09/2026',
						subscriptionId: 9401,
					},
				],
			},
		});
		const data = await runSubscription(backend, '9401');

		expect(data.linkedTransactions).toEqual({ status: 'unavailable', reason: 'invalid' });
		// Without the linked set the loader cannot classify candidates either.
		expect(data.unlinkedTransactions).toEqual({ status: 'unavailable', reason: 'invalid' });
		expect(data.payments.data).toHaveLength(3);
	});

	test('a malformed next billing date is a page-level failure, not a bad render', async () => {
		const backend = createFixtureBackend('FX-SUB-SHARED-POPULATED-01', {
			routes: {
				'GET /api/subscriptions/9401': {
					id: 9401,
					name: 'Streaming sintético',
					cost: 299,
					billingCycle: 'MONTHLY',
					type: 'SHARED',
					nextBillingDate: '2026-13-45',
				},
			},
		});

		await expect(runSubscription(backend, '9401')).rejects.toMatchObject({ status: 502 });
	});

	test('sections recover on a retry without any write request', async () => {
		const backend = createFixtureBackend('FX-SUB-SHARED-POPULATED-01', {
			failures: { 'GET /api/subscriptions/9401/members': { kind: 'status', status: 503 } },
		});
		expect((await runSubscription(backend, '9401')).members.status).toBe(
			'unavailable',
		);

		backend.healRoute('GET /api/subscriptions/9401/members');
		const data = await runSubscription(backend, '9401');

		expect(data.members.data).toHaveLength(2);
		expectReadOnly(backend);
	});

	test('a failed subscription request is still a page-level failure', async () => {
		const backend = createFixtureBackend('FX-SUB-SHARED-POPULATED-01', {
			failures: { 'GET /api/subscriptions/9401': { kind: 'status', status: 500 } },
		});

		await expect(runSubscription(backend, '9401')).rejects.toMatchObject({ status: 502 });
	});
});
