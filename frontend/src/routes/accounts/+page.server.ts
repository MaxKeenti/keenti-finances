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
	const [statusRes, accountsRes, transfersRes] = await Promise.all([
		fetch(`${BACKEND}/api/accounts/status`, { headers: auth }),
		fetch(`${BACKEND}/api/accounts`, { headers: auth }),
		fetch(`${BACKEND}/api/account-transfers`, { headers: auth }),
	]);
	return {
		status: statusRes.ok ? await statusRes.json() : { active: false, transactionNetBalance: 0, accountNetBalance: 0 },
		accounts: accountsRes.ok ? await accountsRes.json() : [],
		transfers: transfersRes.ok ? await transfersRes.json() : [],
	};
};

export const actions: Actions = {
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
};
