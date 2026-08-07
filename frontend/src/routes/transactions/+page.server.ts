import { fail } from '@sveltejs/kit';
import { superValidate } from 'sveltekit-superforms';
import { zod4 } from 'sveltekit-superforms/adapters';
import { getSession } from '$lib/server/workos-session';
import { m } from '$lib/paraglide/messages.js';
import { transactionSchema } from '$lib/schemas/transaction';
import { dateInTimeZone } from '$lib/formatting';
import type { BoxDto } from '$lib/types/boxes';
import {
	normalizeTransactionBoxFields,
	type BoxDistributionDto,
	type BoxFundingDto,
} from '$lib/types/transactions';
import type { Actions, PageServerLoad } from './$types';

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

type Category = { id: number; name: string; type: string; color?: string };
type Contact = { id: number; name: string; phone: string | null; email: string | null };
type FinancialAccount = { id: number; name: string; kind: string; balance: number; archived: boolean };
type AccountTracking = { active: boolean; setupRequired: boolean; activatedAt: string | null };
type Transaction = {
	id: number;
	amount: number;
	direction: string;
	description: string | null;
	transactionDate: string;
	categoryId: number;
	categoryName: string | null;
	categoryHue: number | null;
	contactId: number | null;
	contactName: string | null;
	accountId: number | null;
	accountName: string | null;
	accountKind: string | null;
	boxFunding: BoxFundingDto[];
	boxDistributions: BoxDistributionDto[];
	availableToSpendAmount: number;
};

type TransactionPage = {
	items: Transaction[];
	pageIndex: number;
	pageSize: number;
	totalItems: number;
	totalPages: number;
	sortBy: TransactionSortBy;
	sortDirection: TransactionSortDirection;
};
type Transfer = { id: number; sourceAccountName: string | null; destinationAccountName: string | null; amount: number; transferDate: string; notes: string | null };

type TransactionSortBy =
	| 'transactionDate'
	| 'amount'
	| 'direction'
	| 'description'
	| 'categoryName'
	| 'contactName';
type TransactionSortDirection = 'asc' | 'desc';

const SORT_FIELDS = new Set<TransactionSortBy>([
	'transactionDate',
	'amount',
	'direction',
	'description',
	'categoryName',
	'contactName',
]);
const PAGE_SIZES = new Set([10, 25, 50, 100]);

function positiveInt(value: string | null, fallback: number) {
	const parsed = Number(value);
	return Number.isInteger(parsed) && parsed >= 0 ? parsed : fallback;
}

function pageSizeParam(value: string | null, fallback: number) {
	const parsed = Number(value);
	return PAGE_SIZES.has(parsed) ? parsed : PAGE_SIZES.has(fallback) ? fallback : 25;
}

function sortByParam(value: string | null, fallback: string): TransactionSortBy {
	return value && SORT_FIELDS.has(value as TransactionSortBy)
		? (value as TransactionSortBy)
		: SORT_FIELDS.has(fallback as TransactionSortBy)
			? (fallback as TransactionSortBy)
			: 'transactionDate';
}

function sortDirectionParam(value: string | null, fallback: string): TransactionSortDirection {
	if (value === 'asc' || value === 'desc') return value;
	return fallback === 'asc' ? 'asc' : 'desc';
}

export const load: PageServerLoad = async ({ fetch, cookies, url, parent }) => {
	const { preferences } = await parent();
	const session = getSession(cookies);
	const accessToken = session?.accessToken;
	const authHeaders: Record<string, string> = accessToken
		? { Authorization: `Bearer ${accessToken}` }
		: {};

	const pageIndex = positiveInt(url.searchParams.get('page'), 0);
	const pageSize = pageSizeParam(url.searchParams.get('pageSize'), preferences.transactionPageSize);
	const sortBy = sortByParam(url.searchParams.get('sortBy'), preferences.transactionSortBy);
	const sortDirection = sortDirectionParam(
		url.searchParams.get('sortDirection'),
		preferences.transactionSortDirection,
	);
	const txParams = new URLSearchParams({
		page: String(pageIndex),
		pageSize: String(pageSize),
		sortBy,
		sortDirection,
	});

	let transactionPage: TransactionPage = {
		items: [],
		pageIndex,
		pageSize,
		totalItems: 0,
		totalPages: 0,
		sortBy,
		sortDirection,
	};
	let categories: Category[] = [];
	let contacts: Contact[] = [];
	let boxes: BoxDto[] = [];
	let accounts: FinancialAccount[] = [];
	let accountTracking: AccountTracking = { active: false, setupRequired: false, activatedAt: null };
	let transfers: Transfer[] = [];
	let activityTransactions: Transaction[] = [];

	try {
		const [txRes, activityTxRes, catRes, conRes, boxRes, accountRes, accountStatusRes, transferRes] = await Promise.all([
			fetch(`${BACKEND}/api/transactions?${txParams}`, { headers: authHeaders }),
			fetch(`${BACKEND}/api/transactions`, { headers: authHeaders }),
			fetch(`${BACKEND}/api/categories`, { headers: authHeaders }),
			fetch(`${BACKEND}/api/contacts`, { headers: authHeaders }),
			fetch(`${BACKEND}/api/boxes?archived=false`, { headers: authHeaders }),
			fetch(`${BACKEND}/api/accounts`, { headers: authHeaders }),
			fetch(`${BACKEND}/api/accounts/status`, { headers: authHeaders }),
			fetch(`${BACKEND}/api/account-transfers`, { headers: authHeaders }),
		]);

		if (txRes.ok) {
			const body = await txRes.json();
			if (body && Array.isArray(body.items)) {
				transactionPage = {
					...body,
					items: body.items.map((transaction: Transaction) =>
						normalizeTransactionBoxFields(transaction),
					),
				};
			} else {
				// Defensive: an older/mismatched backend returns a bare array instead of a
				// paginated TransactionPageResponse. Keep the empty default so the page renders
				// the empty state rather than crashing on `transactions.length`.
				console.error('[transactions] load: unexpected transactions response shape (no items array)');
			}
		} else {
			console.error(`[transactions] load: backend returned ${txRes.status} for transactions`);
		}
		if (activityTxRes.ok) {
			const body = await activityTxRes.json();
			if (Array.isArray(body)) activityTransactions = body.map(normalizeTransactionBoxFields);
		}

		if (catRes.ok) categories = await catRes.json();
		else console.error(`[transactions] load: backend returned ${catRes.status} for categories`);

		if (conRes.ok) contacts = await conRes.json();
		else console.error(`[transactions] load: backend returned ${conRes.status} for contacts`);

		if (boxRes.ok) boxes = await boxRes.json();
		else console.error(`[transactions] load: backend returned ${boxRes.status} for boxes`);

		if (accountRes.ok) accounts = await accountRes.json();
		else console.error(`[transactions] load: backend returned ${accountRes.status} for accounts`);

		if (accountStatusRes.ok) accountTracking = await accountStatusRes.json();
		else console.error(`[transactions] load: backend returned ${accountStatusRes.status} for account status`);

		if (transferRes.ok) transfers = await transferRes.json();
		else console.error(`[transactions] load: backend returned ${transferRes.status} for transfers`);
	} catch {
		console.error('[transactions] load: backend unreachable');
	}

	const today = dateInTimeZone(preferences.timeZone);
	const form = await superValidate(
		{
			amount: 0,
			direction: 'INGRESS' as const,
			description: '',
			transactionDate: today,
			categoryId: 0,
			contactId: '' as '',
			accountId: '' as '',
			boxFunding: [],
			boxDistributions: [],
		},
		zod4(transactionSchema),
		);

	return {
		transactions: transactionPage.items ?? [], transactionPage, categories, contacts, boxes,
		accounts, accountTracking, transfers, activityTransactions, form,
	};
};

export const actions: Actions = {
	create: async ({ request, fetch, cookies }) => {
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		const form = await superValidate(request, zod4(transactionSchema));
		if (!form.valid) return fail(400, { form });

		const contactId = !form.data.contactId ? null : form.data.contactId;
		const accountId = !form.data.accountId ? null : form.data.accountId;

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/transactions`, {
				method: 'POST',
				headers: { 'content-type': 'application/json', ...authHeaders },
				body: JSON.stringify({
					amount: form.data.amount,
					direction: form.data.direction,
					description: form.data.description || null,
					transactionDate: form.data.transactionDate,
					categoryId: form.data.categoryId,
					contactId,
					accountId,
					boxFunding: form.data.direction === 'EGRESS' ? form.data.boxFunding : [],
					boxDistributions:
						form.data.direction === 'INGRESS' ? form.data.boxDistributions : [],
				}),
			});
		} catch {
			console.error('[transactions] create: backend unreachable');
			return fail(502, { form: { ...form, message: m.error_backend_unreachable() } });
		}

		if (res.status === 400) {
			console.error('[transactions] create: validation error from backend');
			return fail(400, { form: { ...form, message: m.error_invalid_transaction() } });
		}
		if (res.status === 404) {
			return fail(404, { form: { ...form, message: m.error_transaction_box_not_found() } });
		}
		if (res.status === 409) {
			return fail(409, {
				form: {
					...form,
					message:
						form.data.direction === 'INGRESS'
							? m.error_transaction_distribution_unavailable()
							: m.error_transaction_box_conflict(),
				},
			});
		}
		if (!res.ok) {
			console.error(`[transactions] create: backend error ${res.status}`);
			return fail(502, { form: { ...form, message: m.error_unexpected_create_transaction() } });
		}

		console.log(`[transactions] create: success — amount: ${form.data.amount} direction: ${form.data.direction}`);
		return { form };
	},

	update: async ({ request, fetch, cookies }) => {
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		const form = await superValidate(request, zod4(transactionSchema));
		if (!form.valid) return fail(400, { form });

		const id = form.data.id;
		if (!id) return fail(400, { form: { ...form, message: m.error_missing_transaction_id_update() } });

		const contactId = !form.data.contactId ? null : form.data.contactId;
		const accountId = !form.data.accountId ? null : form.data.accountId;

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/transactions/${id}`, {
				method: 'PUT',
				headers: { 'content-type': 'application/json', ...authHeaders },
				body: JSON.stringify({
					amount: form.data.amount,
					direction: form.data.direction,
					description: form.data.description || null,
					transactionDate: form.data.transactionDate,
					categoryId: form.data.categoryId,
					contactId,
					accountId,
					boxFunding: form.data.direction === 'EGRESS' ? form.data.boxFunding : [],
				}),
			});
		} catch {
			console.error('[transactions] update: backend unreachable');
			return fail(502, { form: { ...form, message: m.error_backend_unreachable() } });
		}

		if (res.status === 404) return fail(404, { form: { ...form, message: m.error_transaction_not_found() } });
		if (res.status === 400) {
			console.error('[transactions] update: validation error from backend');
			return fail(400, { form: { ...form, message: m.error_invalid_transaction() } });
		}
		if (res.status === 409) {
			return fail(409, { form: { ...form, message: m.error_transaction_box_conflict() } });
		}
		if (!res.ok) {
			console.error(`[transactions] update: backend error ${res.status}`);
			return fail(502, { form: { ...form, message: m.error_unexpected_update_transaction() } });
		}

		console.log(`[transactions] update: success — id: ${id} amount: ${form.data.amount}`);
		return { form };
	},

	delete: async ({ request, fetch, cookies }) => {
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		const data = await request.formData();
		const id = data.get('id');

		if (!id) return fail(400, { message: m.error_missing_transaction_id() });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/transactions/${id}`, {
				method: 'DELETE',
				headers: authHeaders,
			});
		} catch {
			console.error('[transactions] delete: backend unreachable');
			return fail(502, { message: m.error_backend_unreachable() });
		}

		if (res.status === 404) return fail(404, { message: m.error_transaction_not_found() });
		if (res.status === 409) return fail(409, { message: m.error_transaction_box_conflict() });
		if (!res.ok) {
			console.error(`[transactions] delete: backend error ${res.status}`);
			return fail(502, { message: m.error_unexpected_delete_transaction() });
		}

		console.log(`[transactions] delete: success — id: ${id}`);
		return {};
	},

	bulkDelete: async ({ request, fetch, cookies }) => {
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		const data = await request.formData();
		const ids = Array.from(
			new Set(
				data
					.getAll('id')
					.map((value) => String(value))
					.filter(Boolean),
			),
		);

		if (ids.length === 0) return fail(400, { message: m.error_missing_transaction_id() });

		try {
			for (const id of ids) {
				const res = await fetch(`${BACKEND}/api/transactions/${id}`, {
					method: 'DELETE',
					headers: authHeaders,
				});

				if (res.status === 404) return fail(404, { message: m.error_transaction_not_found() });
				if (res.status === 409) return fail(409, { message: m.error_transaction_box_conflict() });
				if (!res.ok) {
					console.error(`[transactions] bulkDelete: backend error ${res.status} for id ${id}`);
					return fail(502, { message: m.error_unexpected_bulk_delete_transactions() });
				}
			}
		} catch {
			console.error('[transactions] bulkDelete: backend unreachable');
			return fail(502, { message: m.error_backend_unreachable() });
		}

		console.log(`[transactions] bulkDelete: success — count: ${ids.length}`);
		return { deleted: ids.length };
	},
};
