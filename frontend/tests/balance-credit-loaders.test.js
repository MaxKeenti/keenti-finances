// @ts-nocheck
/**
 * Slices 1A-balance and 3A at request level.
 *
 * These drive the production loaders and form actions with the synthetic 0A/0B
 * fixture backend. Nothing here reaches a real service: every route is served
 * in process, and the assertions are about what the app claims when a read
 * fails as much as when it succeeds.
 */

import { describe, expect, test } from 'bun:test';
import { createFixtureBackend } from './fixtures/backend';
import { HARNESS_ROUTES } from './fixtures/harness-routes';
import { load as layoutLoad } from '../src/routes/+layout.server';
import { load as dashboardLoad } from '../src/routes/+page.server';
import { load as accountsLoad, actions as accountsActions } from '../src/routes/accounts/+page.server';
import { load as accountLoad } from '../src/routes/accounts/[id]/+page.server';
import { accountStatementPaymentStatus } from '../src/lib/obligation-status';
import { netBalanceSource } from '../src/lib/balance-presentation';
import { loadScenario } from './fixtures/scenarios';

function cookies(values = {}) {
	const store = new Map(Object.entries(values));
	return { get: (name) => store.get(name), set: (name, value) => store.set(name, value), store };
}

async function runLayout(backend, path = '/transactions') {
	backend.declareDefaultRoutes(HARNESS_ROUTES);
	const data = await layoutLoad({
		locals: { session: { user: { id: 'fixture-user' } } },
		fetch: backend.fetch,
		cookies: cookies(),
		url: new URL(`http://app.test${path}`),
	});
	backend.assertNoUndeclaredRoutes();
	return data;
}

async function runDashboard(backend) {
	const data = await dashboardLoad({
		fetch: backend.fetch,
		url: new URL('http://app.test/?year=2026'),
		cookies: cookies({ PARAGLIDE_LOCALE: 'es' }),
	});
	backend.assertNoUndeclaredRoutes();
	return data;
}

async function runAccounts(backend, search = '') {
	const data = await accountsLoad({
		fetch: backend.fetch,
		cookies: cookies(),
		url: new URL(`http://app.test/accounts${search}`),
	});
	backend.assertNoUndeclaredRoutes();
	return data;
}

/** Routes the Accounts overview needs beyond a balance scenario's own. */
const ACCOUNTS_PAGE_ROUTES = {
	'GET /api/accounts?archived=true': [],
	'GET /api/account-transfers': [],
};

/** Routes the account detail page needs beyond the balance scenarios' own. */
function accountDetailRoutes(id, account) {
	return {
		[`GET /api/accounts/${id}`]: account,
		'GET /api/transactions': [],
		'GET /api/account-transfers': [],
		[`GET /api/accounts/${id}/msi-plans`]: [],
		[`GET /api/accounts/${id}/credit-statements/current-estimate`]: null,
	};
}

async function runAccountDetail(backend, id) {
	const data = await accountLoad({
		params: { id: String(id) },
		fetch: backend.fetch,
		cookies: cookies(),
	});
	backend.assertNoUndeclaredRoutes();
	return data;
}

function expectReadOnly(backend) {
	expect(backend.requests.every((request) => request.method === 'GET')).toBe(true);
}

describe('tracking mode is read, never inferred', () => {
	test('an inactive fixture reports the Transaction formula', async () => {
		const backend = createFixtureBackend('FX-TRACK-INACTIVE-01');
		// `setupRequired` is true here, so an ordinary page redirects to setup;
		// /accounts is exempt and is where the mode still has to be readable.
		const data = await runLayout(backend, '/accounts');

		expect(data.accountTracking).toMatchObject({ status: 'ok', data: { active: false } });
		expect(netBalanceSource(data.accountTracking.data)).toBe('transactions');
	});

	test('an active fixture reports the Financial Account ledger', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01');
		const data = await runLayout(backend);

		expect(data.accountTracking).toMatchObject({ status: 'ok', data: { active: true } });
		expect(netBalanceSource(data.accountTracking.data)).toBe('accounts');
	});

	test('a failed status read is unavailable and leaves the balance intact', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', {
			failures: { 'GET /api/accounts/status': { kind: 'status', status: 500 } },
		});
		const data = await runLayout(backend);

		expect(data.accountTracking).toEqual({ status: 'unavailable', reason: 'error' });
		// The balance loaded independently and is still true; only the
		// explanation of which formula produced it is missing.
		expect(data.balanceSummary).toMatchObject({ status: 'ok', data: { netBalance: 2_500.75 } });
		expect(netBalanceSource(null)).toBe('unknown');
	});

	test('an unreachable status read is unavailable, not "not activated"', async () => {
		const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', {
			failures: { 'GET /api/accounts/status': { kind: 'unreachable' } },
		});
		const data = await runLayout(backend);

		expect(data.accountTracking).toEqual({ status: 'unavailable', reason: 'unreachable' });
		expect(JSON.stringify(data.accountTracking)).not.toContain('active');
	});

	test('a 200 with no usable active boolean is invalid, not a mode', async () => {
		for (const body of [
			{ setupRequired: false, accountNetBalance: 2_500.75 },
			{ active: 'true', setupRequired: false },
			{ active: 1, setupRequired: false },
			{ active: true },
		]) {
			const backend = createFixtureBackend('FX-TRACK-ACTIVE-01', {
				routes: { 'GET /api/accounts/status': body },
			});
			const data = await runLayout(backend);
			expect(data.accountTracking).toEqual({ status: 'unavailable', reason: 'invalid' });
		}
	});

	test('a malformed status cannot strand the User on the setup redirect', async () => {
		const backend = createFixtureBackend('FX-TRACK-INACTIVE-01', {
			routes: { 'GET /api/accounts/status': { setupRequired: true } },
		});
		// A redirect would throw out of the loader; reaching the assertion means
		// the unreadable status changed no navigation.
		const data = await runLayout(backend, '/transactions');
		expect(data.accountTracking.status).toBe('unavailable');
	});

	test('a readable setupRequired still redirects to setup', async () => {
		const backend = createFixtureBackend('FX-TRACK-INACTIVE-01');
		backend.declareDefaultRoutes(HARNESS_ROUTES);
		let redirected = null;
		try {
			await layoutLoad({
				locals: { session: { user: { id: 'fixture-user' } } },
				fetch: backend.fetch,
				cookies: cookies(),
				url: new URL('http://app.test/transactions'),
			});
		} catch (thrown) {
			redirected = thrown;
		}
		expect(redirected?.status).toBe(303);
		expect(redirected?.location).toBe('/accounts');
	});

	test('the accounts page reports an unreadable status instead of a zero', async () => {
		const backend = createFixtureBackend('FX-TRACK-INACTIVE-01', {
			routes: ACCOUNTS_PAGE_ROUTES,
			failures: { 'GET /api/accounts/status': { kind: 'status', status: 503 } },
		});
		const data = await runAccounts(backend);

		expect(data.status).toEqual({ status: 'unavailable', reason: 'error' });
		// The old fallback answered with active:false and a 0.00 net balance,
		// which is an activation target nobody computed.
		expect(JSON.stringify(data.status)).not.toContain('transactionNetBalance');
		expectReadOnly(backend);
	});

	test('the accounts page keeps the pre-activation balance when it is readable', async () => {
		const backend = createFixtureBackend('FX-TRACK-INACTIVE-01', { routes: ACCOUNTS_PAGE_ROUTES });
		const data = await runAccounts(backend);

		expect(data.status).toMatchObject({
			status: 'ok',
			data: { active: false, setupRequired: true, transactionNetBalance: 1_500 },
		});
	});

	test('an unreadable pre-activation balance does not become zero', async () => {
		const backend = createFixtureBackend('FX-TRACK-INACTIVE-01', {
			routes: {
				...ACCOUNTS_PAGE_ROUTES,
				'GET /api/accounts/status': {
					active: false,
					setupRequired: true,
					activatedAt: null,
					transactionNetBalance: 'mil quinientos',
					accountNetBalance: 0,
				},
			},
		});
		const data = await runAccounts(backend);

		expect(data.status.status).toBe('ok');
		expect(data.status.data.transactionNetBalance).toBeNull();
	});
});

describe('dashboard credit obligations', () => {
	test('a confirmed unpaid statement travels up with its figures', async () => {
		const backend = createFixtureBackend('FX-CREDIT-INFAVOR-STMT-01');
		const data = await runDashboard(backend);
		const { expected } = backend.scenario;

		expect(data.accountWarnings.status).toBe('ok');
		expect(data.accountWarnings.data.partial).toBe(false);
		const [entry] = data.accountWarnings.data.statementAccounts;
		expect(entry.accountId).toBe(expected.accountId);

		const { status } = accountStatementPaymentStatus({
			statements: entry.statements,
			today: '2026-09-07',
			read: (statement) => statement,
		});
		expect(status.state).toBe('outstanding-upcoming');
		expect(status.outstanding).toBe(expected.outstandingBalance);
		// Credit in the User's favour and an unpaid statement are both true; the
		// statement is never netted against the balance or against Net Balance.
		expect(data.summary.data.netBalance).toBe(expected.netBalance);
		expectReadOnly(backend);
	});

	test('a mismatched statement keeps its payment state and its own amount', async () => {
		const backend = createFixtureBackend('FX-STMT-MISMATCH-01');
		const data = await runDashboard(backend);
		const [entry] = data.accountWarnings.data.statementAccounts;

		const { status } = accountStatementPaymentStatus({
			statements: entry.statements,
			today: '2026-09-07',
			read: (statement) => statement,
		});
		expect(status.state).toBe('outstanding-upcoming');
		expect(status.reconciliationMismatch).toBe(true);
		expect(status.mismatchAmount).toBe(backend.scenario.expected.mismatchAmount);
		expect(status.outstanding).toBe(backend.scenario.expected.officialBalance);
	});

	test('an unreadable statement list contributes nothing and marks the page partial', async () => {
		const backend = createFixtureBackend('FX-CREDIT-INFAVOR-STMT-01', {
			failures: { 'GET /api/accounts/9112/credit-statements': { kind: 'status', status: 500 } },
		});
		const data = await runDashboard(backend);

		expect(data.accountWarnings.data.partial).toBe(true);
		// Silence is not "no payment due": the account is simply absent from the
		// list the page derives statuses from, and the page says so.
		expect(data.accountWarnings.data.statementAccounts).toEqual([]);
	});

	test('a statement missing its due date keeps the amount it still owes', async () => {
		// The real loader, not the standalone helper: a due date that fails
		// validation used to fail the whole list, so a card owing 640 rendered
		// as having nothing confirmed at all. D2 has a state for exactly this.
		const backend = createFixtureBackend('FX-CREDIT-INFAVOR-STMT-01', {
			routes: {
				'GET /api/accounts/9112/credit-statements': [
					{
						id: 9303,
						periodStart: '2026-08-06',
						periodEnd: '2026-09-05',
						dueDate: '2026-13-45',
						officialBalance: 640,
						officialMinimumPayment: 150,
						officialAvoidInterest: 640,
						officialNote: null,
						paidAmount: 0,
						outstandingBalance: 640,
						reconciliationMismatch: false,
						mismatchAmount: 0,
					},
				],
			},
		});
		const data = await runDashboard(backend);

		expect(data.accountWarnings.data.partial).toBe(false);
		const [entry] = data.accountWarnings.data.statementAccounts;
		expect(entry.statements[0].dueDate).toBeNull();

		const { status } = accountStatementPaymentStatus({
			statements: entry.statements,
			today: '2026-09-07',
			read: (statement) => statement,
		});
		expect(status.state).toBe('outstanding-due-unknown');
		expect(status.outstanding).toBe(640);
	});

	test('a mismatch on a covered statement still reaches the page', async () => {
		// Fully paid, so it is not an outstanding obligation — but the recorded
		// activity and the bank's snapshot still disagree, and that notice is
		// independent of the payment state.
		const backend = createFixtureBackend('FX-STMT-MISMATCH-01', {
			routes: {
				'GET /api/accounts/9108/credit-statements': [
					{
						...loadScenario('FX-STMT-MISMATCH-01').routes['GET /api/accounts/9108/credit-statements'][0],
						paidAmount: 1_250,
						outstandingBalance: 0,
					},
				],
			},
		});
		const data = await runDashboard(backend);
		const [entry] = data.accountWarnings.data.statementAccounts;

		const { status, mismatches } = accountStatementPaymentStatus({
			statements: entry.statements,
			today: '2026-09-07',
			read: (statement) => statement,
		});
		expect(status.state).toBe('covered');
		expect(mismatches).toHaveLength(1);
		expect(mismatches[0].status.mismatchAmount).toBe(125.5);
	});

	test('a statement missing its mismatch flag fails the section rather than defaulting', async () => {
		const backend = createFixtureBackend('FX-CREDIT-INFAVOR-STMT-01', {
			routes: {
				'GET /api/accounts/9112/credit-statements': [
					{
						id: 9303,
						periodStart: '2026-08-06',
						periodEnd: '2026-09-05',
						dueDate: '2026-09-20',
						officialBalance: 640,
						officialMinimumPayment: 150,
						officialAvoidInterest: 640,
						officialNote: null,
						paidAmount: 0,
						outstandingBalance: 640,
						mismatchAmount: 0,
					},
				],
			},
		});
		const data = await runDashboard(backend);

		expect(data.accountWarnings.data.partial).toBe(true);
		expect(data.accountWarnings.data.statementAccounts).toEqual([]);
	});
});

describe('credit account page sections', () => {
	const CARD = {
		id: 9112,
		name: 'Tarjeta sintética a favor',
		kind: 'CREDIT',
		hue: 200,
		openingBalance: 0,
		openingDate: '2026-01-01',
		balance: 85,
		archived: false,
	};

	test('settings and statements load as independent sections', async () => {
		const backend = createFixtureBackend('FX-CREDIT-INFAVOR-STMT-01', {
			routes: accountDetailRoutes(9112, CARD),
		});
		const data = await runAccountDetail(backend, 9112);

		expect(data.credit.settings).toMatchObject({ status: 'ok', data: { creditLimit: 12_000 } });
		expect(data.credit.statements.status).toBe('ok');
		expect(data.credit.statements.data[0].outstandingBalance).toBe(640);
		expectReadOnly(backend);
	});

	test('unconfigured credit settings are an absence, not a failure', async () => {
		const backend = createFixtureBackend('FX-CREDIT-INFAVOR-STMT-01', {
			routes: accountDetailRoutes(9112, CARD),
			failures: {
				'GET /api/accounts/9112/credit-settings': { kind: 'status', status: 404 },
			},
		});
		const data = await runAccountDetail(backend, 9112);

		// `ok` with `null` data: there is no limit, so available credit is
		// genuinely unknown and the page offers to set one up.
		expect(data.credit.settings).toEqual({ status: 'ok', data: null });
		expect(data.credit.statements.status).toBe('ok');
	});

	test('an unreadable statement list is unavailable, not an empty one', async () => {
		const backend = createFixtureBackend('FX-CREDIT-INFAVOR-STMT-01', {
			routes: accountDetailRoutes(9112, CARD),
			failures: { 'GET /api/accounts/9112/credit-statements': { kind: 'unreachable' } },
		});
		const data = await runAccountDetail(backend, 9112);

		expect(data.credit.statements).toEqual({ status: 'unavailable', reason: 'unreachable' });
		// An empty list would read as "nothing left to pay".
		expect(data.credit.statements.data).toBeUndefined();
	});

	test('a failed estimate read is reported, so "not confirmed" is not claimed on it', async () => {
		const backend = createFixtureBackend('FX-CREDIT-INFAVOR-STMT-01', {
			routes: { ...accountDetailRoutes(9112, CARD), 'GET /api/accounts/9112/credit-statements': [] },
			failures: {
				'GET /api/accounts/9112/credit-statements/current-estimate': { kind: 'status', status: 500 },
			},
		});
		const data = await runAccountDetail(backend, 9112);

		expect(data.credit.estimateAvailable).toBe(false);
		// With no confirmed statement and a failed estimate, nothing is known
		// about this cycle: "estimated statement, not confirmed" would be a
		// claim about a read that never answered.
		const { status } = accountStatementPaymentStatus({
			statements: data.credit.statements.data,
			today: '2026-09-07',
			read: (statement) => statement,
			estimateAvailable: data.credit.estimateAvailable,
		});
		expect(status.state).toBe('unavailable');
	});

	test('a successful estimate read allows the estimated-only state', async () => {
		const backend = createFixtureBackend('FX-CREDIT-INFAVOR-STMT-01', {
			routes: { ...accountDetailRoutes(9112, CARD), 'GET /api/accounts/9112/credit-statements': [],
				'GET /api/accounts/9112/credit-statements/current-estimate': {periodStart:'2026-09-06',periodEnd:'2026-10-05',dueDate:'2026-10-20',estimatedBalance:0} },
		});
		const data = await runAccountDetail(backend, 9112);

		expect(data.credit.estimateAvailable).toBe(true);
		const { status } = accountStatementPaymentStatus({
			statements: data.credit.statements.data,
			today: '2026-09-07',
			read: (statement) => statement,
			estimateAvailable: data.credit.estimateAvailable,
		});
		expect(status.state).toBe('estimated-only');
	});

	test('malformed or missing estimates cannot claim an estimated statement', async () => {
		for (const body of [null, {}, { estimatedBalance: '0' }, {periodStart:'2026-09-06',periodEnd:'2026-10-05',dueDate:'2026-10-20',estimatedBalance:'bad'}]) {
			const backend = createFixtureBackend('FX-CREDIT-INFAVOR-STMT-01', {routes: {
				...accountDetailRoutes(9112, CARD),
				'GET /api/accounts/9112/credit-statements': [],
				'GET /api/accounts/9112/credit-statements/current-estimate': body,
			}});
			const data = await runAccountDetail(backend, 9112);
			expect(data.credit.currentEstimate).toBeNull();
			expect(data.credit.estimateAvailable).toBe(false);
		}
	});

	test('an unreachable estimate does not discard loaded confirmed statements', async () => {
		const backend = createFixtureBackend('FX-CREDIT-INFAVOR-STMT-01', {routes: accountDetailRoutes(9112, CARD), failures: {
			'GET /api/accounts/9112/credit-statements/current-estimate': {kind:'unreachable'},
		}});
		const data = await runAccountDetail(backend, 9112);
		expect(data.credit.estimateAvailable).toBe(false);
		expect(data.credit.statements.status).toBe('ok');
		expect(data.credit.statements.data[0].outstandingBalance).toBe(640);
	});

	test('an unconfigured statement schedule is a setup state, never an estimate or outage', async () => {
		const backend = createFixtureBackend('FX-CREDIT-INFAVOR-STMT-01', {routes: {
			...accountDetailRoutes(9112, CARD), 'GET /api/accounts/9112/credit-statements': [],
		}, failures: {
			'GET /api/accounts/9112/credit-settings': {kind:'status',status:404},
			'GET /api/accounts/9112/credit-statements/current-estimate': {kind:'status',status:409},
		}});
		const data = await runAccountDetail(backend, 9112);
		expect(data.credit.statementScheduleUnconfigured).toBe(true);
		expect(data.credit.estimateAvailable).toBe(false);
		expect(data.credit.currentEstimate).toBeNull();
		expect(data.credit.statements).toEqual({status:'ok',data:[]});
	});

	test('an estimate conflict with failed settings cannot invent a setup state', async () => {
		const backend = createFixtureBackend('FX-CREDIT-INFAVOR-STMT-01', {routes: accountDetailRoutes(9112,CARD), failures: {
			'GET /api/accounts/9112/credit-settings': {kind:'status',status:500},
			'GET /api/accounts/9112/credit-statements/current-estimate': {kind:'status',status:409},
		}});
		const data = await runAccountDetail(backend, 9112);
		expect(data.credit.statementScheduleUnconfigured).toBe(false);
		expect(data.credit.estimateAvailable).toBe(false);
	});

	test('malformed cycle days fail the settings section rather than prefill blanks', async () => {
		const backend = createFixtureBackend('FX-CREDIT-INFAVOR-STMT-01', {
			routes: {
				...accountDetailRoutes(9112, CARD),
				'GET /api/accounts/9112/credit-settings': { creditLimit: 12_000 },
			},
		});
		const data = await runAccountDetail(backend, 9112);

		expect(data.credit.settings).toEqual({ status: 'unavailable', reason: 'invalid' });
	});
});

describe('contextual card payment through the neutral Transfer workflow', () => {
	test('the prefill is read from the URL and writes nothing', async () => {
		const backend = createFixtureBackend('FX-CREDIT-INFAVOR-STMT-01', {
			routes: ACCOUNTS_PAGE_ROUTES,
		});
		const data = await runAccounts(backend, '?payCard=9112&amount=640');

		expect(data.payCard).toEqual({ accountId: 9112, amount: 640 });
		// Opening a prefilled form is not recording a payment.
		expectReadOnly(backend);
		expect(backend.requests.some((request) => request.url.includes('/api/account-transfers') && request.method !== 'GET')).toBe(false);
	});

	test('abandoning the prefilled form leaves the ledger untouched', async () => {
		const backend = createFixtureBackend('FX-CREDIT-INFAVOR-STMT-01', {
			routes: ACCOUNTS_PAGE_ROUTES,
		});
		await runAccounts(backend, '?payCard=9112&amount=640');
		// The User closes the dialog: the page is simply loaded again without
		// the prefill parameters, and no Transfer action ever runs.
		backend.reset();
		const data = await runAccounts(backend);

		expect(data.payCard).toEqual({ accountId: null, amount: null });
		expectReadOnly(backend);
	});

	test('a garbled prefill offers no destination rather than a guessed one', async () => {
		const backend = createFixtureBackend('FX-CREDIT-INFAVOR-STMT-01', {
			routes: ACCOUNTS_PAGE_ROUTES,
		});
		const data = await runAccounts(backend, '?payCard=not-an-account&amount=muchisimo');

		expect(data.payCard).toEqual({ accountId: null, amount: null });
	});

	test('confirming records one Transfer and no Transaction', async () => {
		const backend = createFixtureBackend('FX-CREDIT-INFAVOR-STMT-01', {
			routes: { 'POST /api/account-transfers': { id: 9001 } },
		});
		const form = new Map([
			['sourceAccountId', '9113'],
			['destinationAccountId', '9112'],
			['amount', '640'],
			['transferDate', '2026-09-07'],
			['notes', ''],
		]);
		const result = await accountsActions.transfer({
			request: { formData: async () => ({ get: (key) => form.get(key) ?? null }) },
			fetch: backend.fetch,
			cookies: cookies(),
		});

		expect(result).toEqual({ transferred: true });
		const writes = backend.requests.filter((request) => request.method !== 'GET');
		expect(writes).toHaveLength(1);
		expect(writes[0].key).toBe('POST /api/account-transfers');
		// A card payment is a Transfer, never an EGRESS Transaction: routing one
		// through /api/transactions would put it in expense reporting and move
		// Net Balance.
		expect(backend.requests.some((request) => request.url.includes('/api/transactions'))).toBe(false);
	});

	test('a rejected Transfer reports the failure instead of claiming success', async () => {
		const backend = createFixtureBackend('FX-CREDIT-INFAVOR-STMT-01', {
			failures: { 'POST /api/account-transfers': { kind: 'status', status: 409 } },
		});
		const form = new Map([
			['sourceAccountId', '9113'],
			['destinationAccountId', '9112'],
			['amount', '640'],
			['transferDate', '2026-09-07'],
		]);
		const result = await accountsActions.transfer({
			request: { formData: async () => ({ get: (key) => form.get(key) ?? null }) },
			fetch: backend.fetch,
			cookies: cookies(),
		});

		expect(result.status).toBe(409);
	});
});
