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
import { HARNESS_ROUTES } from './fixtures/harness-routes';
import { load as dashboardLoad } from '../src/routes/+page.server';
import { load as layoutLoad } from '../src/routes/+layout.server';
import { load as subscriptionLoad } from '../src/routes/subscriptions/[id]/+page.server';
import { loadScenario, UNAVAILABLE_SECTION } from './fixtures/scenarios';

function cookies(values = {}) {
	const store = new Map(Object.entries(values));
	return {
		get: (name) => store.get(name),
		set: (name, value) => store.set(name, value),
		store,
	};
}

/**
 * Every loader run below goes through these helpers, and each one asserts that
 * no undeclared route was requested. An undeclared route rejects exactly like an
 * unreachable backend, so without this a mistyped or unexpected call would be
 * credited to the outage a test injected on purpose.
 */
async function runDashboard(backend, year = 2026) {
	const data = await dashboardLoad({
		fetch: backend.fetch,
		url: new URL(`http://app.test/?year=${year}`),
		cookies: cookies({ PARAGLIDE_LOCALE: 'es' }),
	});
	backend.assertNoUndeclaredRoutes();
	return data;
}

async function runLayout(backend) {
	// The shell asks for preferences on every navigation; a scenario about
	// balances need not restate them, but leaving them undeclared would make the
	// loader fall back to defaults for a reason no test intended.
	backend.declareDefaultRoutes(HARNESS_ROUTES);
	const data = await layoutLoad({
		locals: { session: { user: { id: 'fixture-user' } } },
		fetch: backend.fetch,
		cookies: cookies(),
		url: new URL('http://app.test/transactions'),
	});
	backend.assertNoUndeclaredRoutes();
	return data;
}

async function runSubscription(backend, id) {
	const data = await subscriptionLoad({
		params: { id },
		fetch: backend.fetch,
		cookies: cookies(),
	});
	backend.assertNoUndeclaredRoutes();
	return data;
}

/** No page read may create, update, or delete anything. */
function expectReadOnly(backend) {
	expect(backend.requests.every((request) => request.method === 'GET')).toBe(true);
}

describe('dashboard loader sections', () => {
	test('a successful load reports the fixture figures in composed sections', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01');
		const data = await runDashboard(backend);

		expect(data.overview.status).toBe('ok');
		expect(data.overview.data.position).toMatchObject({
			status: 'ok',
			data: { netBalance: 2_500.75, inBoxes: 500, availableToSpend: 2_000.75 },
		});
		expect(data.overview.data.history).toMatchObject({
			status: 'ok',
			data: { totalIngress: 6_000, totalEgress: 3_499.25 },
		});
		expectReadOnly(backend);
	});

	test('the whole dashboard is one request, not a fan-out per account', async () => {
		// The page used to read the account list and then a credit-settings and a
		// confirmed-statement route per Credit Financial Account. Composing
		// server-side is what keeps the page's cost flat as cards are added.
		const backend = createFixtureBackend('FX-DASH-NEG-01');
		await runDashboard(backend);

		expect(backend.requests.map((request) => request.key)).toEqual([
			'GET /api/dashboard/overview',
		]);
	});

	test('a genuine zero stays an ok section, not an unavailable one', async () => {
		const backend = createFixtureBackend('FX-BAL-ZERO-01');
		const data = await runDashboard(backend);

		expect(data.overview.data.position).toMatchObject({
			status: 'ok',
			data: { netBalance: 0, inBoxes: 0, availableToSpend: 0 },
		});
	});

	test('a non-OK overview is unavailable rather than an empty dashboard', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', {
			failures: { 'GET /api/dashboard/overview': { kind: 'status', status: 500 } },
		});
		const data = await runDashboard(backend);

		expect(data.overview).toEqual({ status: 'unavailable', reason: 'error' });
	});

	test('a rejected overview request never becomes a zero balance', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', {
			failures: { 'GET /api/dashboard/overview': { kind: 'unreachable' } },
		});
		const data = await runDashboard(backend);

		expect(data.overview).toEqual({ status: 'unavailable', reason: 'unreachable' });
		expect(JSON.stringify(data.overview)).not.toContain('netBalance');
	});

	test('one unavailable section leaves the others with their figures', async () => {
		// The backend reports per-section availability; a plans section that
		// could not be computed must not take the position figures down with it.
		const scenario = loadScenario('FX-DASH-NEG-01');
		const backend = createFixtureBackend('FX-DASH-NEG-01', {
			routes: {
				'GET /api/dashboard/overview': {
					...scenario.routes['GET /api/dashboard/overview'],
					plans: UNAVAILABLE_SECTION,
				},
			},
		});
		const data = await runDashboard(backend);

		expect(data.overview.data.plans).toEqual({ status: 'unavailable', reason: 'error' });
		expect(data.overview.data.position.data.netBalance).toBe(4_176);
		expect(data.overview.data.expected.status).toBe('ok');
	});

	test('a partial but successful position cannot manufacture a zero balance', async () => {
		const scenario = loadScenario('FX-TRACK-ACTIVE-01');
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', {
			// HTTP 200 with the money fields missing. A loader that spread this
			// over a zero-filled default would present 0.00 as the Net Balance.
			routes: {
				'GET /api/dashboard/overview': {
					...scenario.routes['GET /api/dashboard/overview'],
					position: { status: 'ok', reason: null, data: { trackingActive: true, setupRequired: false } },
				},
			},
		});
		const data = await runDashboard(backend);

		expect(data.overview.data.position).toEqual({ status: 'unavailable', reason: 'invalid' });
		expect(JSON.stringify(data.overview.data.position)).not.toContain('netBalance');
	});

	test('a non-numeric money field is rejected rather than coerced', async () => {
		const scenario = loadScenario('FX-TRACK-ACTIVE-01');
		const position = scenario.routes['GET /api/dashboard/overview'].position;
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', {
			routes: {
				'GET /api/dashboard/overview': {
					...scenario.routes['GET /api/dashboard/overview'],
					position: { ...position, data: { ...position.data, netBalance: '2500.75' } },
				},
			},
		});
		const data = await runDashboard(backend);

		expect(data.overview.data.position).toEqual({ status: 'unavailable', reason: 'invalid' });
	});

	test('an unreadable tracking flag is not silently treated as inactive', async () => {
		// Decision D1: the tracking mode decides how the Net Balance is
		// explained, so a missing boolean fails the section instead of arriving
		// as a defaulted `false`, which is the forbidden inference in disguise.
		const scenario = loadScenario('FX-TRACK-ACTIVE-01');
		const position = scenario.routes['GET /api/dashboard/overview'].position;
		const { trackingActive: _dropped, ...withoutTracking } = position.data;
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', {
			routes: {
				'GET /api/dashboard/overview': {
					...scenario.routes['GET /api/dashboard/overview'],
					position: { ...position, data: withoutTracking },
				},
			},
		});
		const data = await runDashboard(backend);

		expect(data.overview.data.position.status).toBe('unavailable');
	});

	test('a malformed plan status fails its section instead of reading as unplanned', async () => {
		const scenario = loadScenario('FX-DASH-NEG-01');
		const plans = scenario.routes['GET /api/dashboard/overview'].plans;
		const backend = createFixtureBackend('FX-DASH-NEG-01', {
			routes: {
				'GET /api/dashboard/overview': {
					...scenario.routes['GET /api/dashboard/overview'],
					plans: {
						...plans,
						data: {
							...plans.data,
							items: [{ ...plans.data.items[0], status: 'PROBABLY_FINE' }],
						},
					},
				},
			},
		});
		const data = await runDashboard(backend);

		expect(data.overview.data.plans).toEqual({ status: 'unavailable', reason: 'invalid' });
		// "No plan" invites creating one, which is the wrong offer for a Box
		// whose plan we simply failed to understand.
		expect(JSON.stringify(data.overview.data.plans)).not.toContain('boxesWithoutActivePlan');
	});

	test('retrying after the backend recovers returns the real figures', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', {
			failures: { 'GET /api/dashboard/overview': { kind: 'status', status: 500 } },
		});
		const failed = await runDashboard(backend);
		expect(failed.overview.status).toBe('unavailable');

		backend.healRoute('GET /api/dashboard/overview');
		const recovered = await runDashboard(backend);

		expect(recovered.overview.data.position).toMatchObject({
			status: 'ok',
			data: { netBalance: 2_500.75 },
		});
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

	test('a malformed next billing date is an unreadable cursor, not a dead page', async () => {
		const backend = createFixtureBackend('FX-SUB-SHARED-POPULATED-01', {
			routes: {
				'GET /api/subscriptions/9401': {
					id: 9401,
					name: 'Streaming sintético',
					cost: 299,
					billingCycle: 'MONTHLY',
					type: 'SHARED',
					// Shaped like a date but not a real day; `2026-13-45` would roll
					// over into a wrong-but-plausible day if it were ever parsed.
					nextBillingDate: '2026-13-45',
				},
			},
		});
		const data = await runSubscription(backend, '9401');

		// The cursor is withheld — never a rolled-over or substituted day — while
		// the price, the members and the stored records, which are independent
		// facts, still load. Failing the page took all of them down over a date
		// that only says whether Keenti still owes itself records.
		expect(data.subscription.nextBillingDate).toBeNull();
		expect(data.subscription.cost).toBe(299);
		expect(data.members.status).toBe('ok');
		expect(data.payments.data).toHaveLength(3);
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
