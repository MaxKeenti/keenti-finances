import { fail } from '@sveltejs/kit';
import { getSession } from '$lib/server/workos-session';
import type { Actions, PageServerLoad } from './$types';

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

function headers(cookies: Parameters<typeof getSession>[0], json = false): Record<string, string> {
	const token = getSession(cookies)?.accessToken;
	return { ...(json ? { 'content-type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) };
}

export const load: PageServerLoad = async ({ fetch, cookies }) => {
	const auth = headers(cookies);
	const [statusRes, accountsRes, archivedAccountsRes, transfersRes] = await Promise.all([
		fetch(`${BACKEND}/api/accounts/status`, { headers: auth }),
		fetch(`${BACKEND}/api/accounts`, { headers: auth }),
		fetch(`${BACKEND}/api/accounts?archived=true`, { headers: auth }),
		fetch(`${BACKEND}/api/account-transfers`, { headers: auth }),
	]);
	const status = statusRes.ok ? await statusRes.json() : { active: false, transactionNetBalance: 0, accountNetBalance: 0 };
	const accounts = accountsRes.ok ? await accountsRes.json() : [];
	const creditDetails = await Promise.all(
		accounts.filter((account: { kind: string }) => account.kind === 'CREDIT').map(async (account: { id: number }) => {
			const [settingsRes, statementsRes] = await Promise.all([
				fetch(`${BACKEND}/api/accounts/${account.id}/credit-settings`, { headers: auth }),
				fetch(`${BACKEND}/api/accounts/${account.id}/credit-statements`, { headers: auth }),
			]);
			return [account.id, {
				settings: settingsRes.ok ? await settingsRes.json() : null,
				statements: statementsRes.ok ? await statementsRes.json() : [],
			}] as const;
		})
	);
	return {
		status,
		accounts,
		archivedAccounts: archivedAccountsRes.ok ? await archivedAccountsRes.json() : [],
		transfers: transfersRes.ok ? await transfersRes.json() : [],
		creditDetails: Object.fromEntries(creditDetails),
	};
};

export const actions: Actions = {
	create: async ({ request, fetch, cookies }) => {
		const data = await request.formData();
		const name = String(data.get('name') ?? '').trim();
		const kind = String(data.get('kind') ?? '').toUpperCase();
		const validKinds = new Set(['CASH', 'DEBIT', 'CHECKING', 'SAVINGS', 'CREDIT']);
		if (!name) return fail(400, { message: 'Account name is required.' });
		if (!validKinds.has(kind)) return fail(400, { message: 'Choose a valid account kind.' });

		const response = await fetch(`${BACKEND}/api/accounts`, {
			method: 'POST', headers: headers(cookies, true),
			body: JSON.stringify({ name, kind, openingBalance: 0 }),
		});
		if (!response.ok) {
			return fail(response.status === 409 ? 409 : 400, {
				message: response.status === 409
					? 'An active account with that name already exists.'
					: 'The account could not be created. New accounts must start at a zero opening balance.',
			});
		}
		return { accountCreated: true };
	},
	activate: async ({ request, fetch, cookies }) => {
		const data = await request.formData();
		let accounts: unknown;
		try { accounts = JSON.parse(String(data.get('accounts') ?? '[]')); } catch { return fail(400, { message: 'Add at least one valid account.' }); }
		const response = await fetch(`${BACKEND}/api/accounts/activate`, {
			method: 'POST', headers: headers(cookies, true),
			body: JSON.stringify({ activationDate: data.get('activationDate'), accounts }),
		});
		if (!response.ok) return fail(response.status === 409 ? 409 : 400, { message: 'The opening balances must match your current Net Balance exactly.' });
		return { activated: true };
	},
	transfer: async ({ request, fetch, cookies }) => {
		const data = await request.formData();
		const response = await fetch(`${BACKEND}/api/account-transfers`, {
			method: 'POST', headers: headers(cookies, true),
			body: JSON.stringify({
				sourceAccountId: Number(data.get('sourceAccountId')),
				destinationAccountId: Number(data.get('destinationAccountId')),
				amount: Number(data.get('amount')),
				transferDate: data.get('transferDate'), notes: String(data.get('notes') ?? '') || null,
			}),
		});
		if (!response.ok) return fail(response.status === 409 ? 409 : 400, { message: 'The transfer could not be recorded.' });
		return { transferred: true };
	},
	saveCreditSettings: async ({ request, fetch, cookies }) => {
		const data = await request.formData();
		const accountId = Number(data.get('accountId'));
		const response = await fetch(`${BACKEND}/api/accounts/${accountId}/credit-settings`, {
			method: 'PUT', headers: headers(cookies, true), body: JSON.stringify({
				creditLimit: Number(data.get('creditLimit')),
				statementClosingDay: Number(data.get('statementClosingDay')),
				paymentDueDay: Number(data.get('paymentDueDay')),
			}),
		});
		if (!response.ok) return fail(400, { message: 'Credit settings could not be saved.' });
		return { creditSettingsSaved: true };
	},
	confirmCreditStatement: async ({ request, fetch, cookies }) => {
		const data = await request.formData();
		const accountId = Number(data.get('accountId'));
		const response = await fetch(`${BACKEND}/api/accounts/${accountId}/credit-statements`, {
			method: 'POST', headers: headers(cookies, true), body: JSON.stringify({
				periodStart: data.get('periodStart'), periodEnd: data.get('periodEnd'), dueDate: data.get('dueDate'),
				officialBalance: Number(data.get('officialBalance')),
				officialMinimumPayment: Number(data.get('officialMinimumPayment')),
				officialAvoidInterest: Number(data.get('officialAvoidInterest')),
				officialNote: String(data.get('officialNote') ?? '') || null,
			}),
		});
		if (!response.ok) return fail(response.status === 409 ? 409 : 400, { message: 'The statement could not be confirmed.' });
		return { statementConfirmed: true };
	},
};
