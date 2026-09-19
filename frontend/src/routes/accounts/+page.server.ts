import { fail } from '@sveltejs/kit';
import { getSession } from '$lib/server/workos-session';
import type { Actions, PageServerLoad } from './$types';
import { m } from '$lib/paraglide/messages.js';
import { loadSection } from '$lib/server/section-load';
import { parseAccountTrackingStatus } from '$lib/server/payloads';

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

function headers(cookies: Parameters<typeof getSession>[0], json = false): Record<string, string> {
	const token = getSession(cookies)?.accessToken;
	return {
		...(json ? { 'content-type': 'application/json' } : {}),
		...(token ? { Authorization: `Bearer ${token}` } : {}),
	};
}

export const load: PageServerLoad = async ({ fetch, cookies, url }) => {
	const auth = headers(cookies);
	// The tracking status decides whether this page offers activation or the
	// activated view, and the pre-activation Net Balance it offers to match.
	// The previous fallback answered a failed read with `active: false` and a
	// net balance of 0.00, which is an activation screen built on a figure
	// nobody computed. An unreadable status is now unavailable (decision D1).
	const [status, accountsRes, archivedAccountsRes, transfersRes] = await Promise.all([
		loadSection(fetch, `${BACKEND}/api/accounts/status`, {
			parse: parseAccountTrackingStatus,
			headers: auth,
			label: 'accounts/status',
		}),
		fetch(`${BACKEND}/api/accounts`, { headers: auth }),
		fetch(`${BACKEND}/api/accounts?archived=true`, { headers: auth }),
		fetch(`${BACKEND}/api/account-transfers`, { headers: auth }),
	]);
	const accounts = accountsRes.ok ? await accountsRes.json() : [];
	return {
		status,
		accounts,
		archivedAccounts: archivedAccountsRes.ok ? await archivedAccountsRes.json() : [],
		transfers: transfersRes.ok ? await transfersRes.json() : [],
		// A contextual card payment arrives here as a prefill request, not as a
		// recorded movement: it only opens the existing Transfer dialog with the
		// Credit Financial Account as its destination. Nothing is written unless
		// the User submits that form.
		payCard: {
			accountId: Number(url.searchParams.get('payCard')) || null,
			amount: Number(url.searchParams.get('amount')) || null,
		},
	};
};

export const actions: Actions = {
	create: async ({ request, fetch, cookies }) => {
		const data = await request.formData();
		const name = String(data.get('name') ?? '').trim();
		const kind = String(data.get('kind') ?? '').toUpperCase();
		const validKinds = new Set(['CASH', 'DEBIT', 'CHECKING', 'SAVINGS', 'CREDIT']);
		if (!name) return fail(400, { message: m.error_account_name_required() });
		if (!validKinds.has(kind)) return fail(400, { message: m.error_account_kind_invalid() });

		const openingBalance = Number(data.get('openingBalance') ?? 0);
		const hue = Number(data.get('hue'));
		if (!Number.isFinite(openingBalance)) return fail(400, { message: m.error_opening_balance_invalid() });
		if (!Number.isInteger(hue) || hue < 0 || hue > 359) return fail(400, { message: m.error_account_colour_invalid() });
		const response = await fetch(`${BACKEND}/api/accounts`, {
			method: 'POST',
			headers: headers(cookies, true),
			body: JSON.stringify({ name, kind, hue, openingBalance }),
		});
		if (!response.ok) {
			return fail(response.status === 409 ? 409 : 400, {
				message: response.status === 409 ? m.error_account_exists() : m.account_create_error(),
			});
		}
		return { accountCreated: true };
	},
	activate: async ({ request, fetch, cookies }) => {
		const data = await request.formData();
		let accounts: unknown;
		try {
			accounts = JSON.parse(String(data.get('accounts') ?? '[]'));
		} catch {
			return fail(400, { message: m.error_activation_needs_account() });
		}
		const response = await fetch(`${BACKEND}/api/accounts/activate`, {
			method: 'POST',
			headers: headers(cookies, true),
			body: JSON.stringify({
				activationDate: data.get('activationDate'),
				accounts,
			}),
		});
		if (!response.ok)
			return fail(response.status === 409 ? 409 : 400, {
				message: m.error_opening_balances_mismatch(),
			});
		return { activated: true };
	},
	transfer: async ({ request, fetch, cookies }) => {
		const data = await request.formData();
		const response = await fetch(`${BACKEND}/api/account-transfers`, {
			method: 'POST',
			headers: headers(cookies, true),
			body: JSON.stringify({
				sourceAccountId: Number(data.get('sourceAccountId')),
				destinationAccountId: Number(data.get('destinationAccountId')),
				amount: Number(data.get('amount')),
				transferDate: data.get('transferDate'),
				notes: String(data.get('notes') ?? '') || null,
			}),
		});
		if (!response.ok)
			return fail(response.status === 409 ? 409 : 400, {
				message: m.error_transfer_create(),
			});
		return { transferred: true };
	},
	updateTransfer: async ({ request, fetch, cookies }) => {
		const data = await request.formData();
		const id = Number(data.get('id'));
		const response = await fetch(`${BACKEND}/api/account-transfers/${id}`, {
			method: 'PUT',
			headers: headers(cookies, true),
			body: JSON.stringify({
				sourceAccountId: Number(data.get('sourceAccountId')),
				destinationAccountId: Number(data.get('destinationAccountId')),
				amount: Number(data.get('amount')),
				transferDate: data.get('transferDate'),
				notes: String(data.get('notes') ?? '') || null,
			}),
		});
		if (!response.ok)
			return fail(response.status === 409 ? 409 : 400, {
				message: m.error_transfer_update(),
			});
		return { transferUpdated: true };
	},
	deleteTransfer: async ({ request, fetch, cookies }) => {
		const data = await request.formData();
		const response = await fetch(`${BACKEND}/api/account-transfers/${Number(data.get('id'))}`, {
			method: 'DELETE',
			headers: headers(cookies),
		});
		if (!response.ok) return fail(400, { message: m.error_transfer_delete() });
		return { transferDeleted: true };
	},
};
