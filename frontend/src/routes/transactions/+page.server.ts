import { fail } from '@sveltejs/kit';
import { superValidate } from 'sveltekit-superforms';
import { zod4 } from 'sveltekit-superforms/adapters';
import { z } from 'zod';
import { getSession } from '$lib/server/workos-session';
import { m } from '$lib/paraglide/messages.js';
import type { Actions, PageServerLoad } from './$types';

const transactionSchema = z.object({
	id: z.coerce.number().optional(),
	amount: z.coerce.number().positive(m.validation_amount_positive()),
	direction: z.enum(['INGRESS', 'EGRESS']),
	description: z.string().max(500).optional(),
	transactionDate: z.string().min(1, m.validation_date_required()),
	categoryId: z.coerce.number().min(1, m.validation_category_required()),
	contactId: z.union([z.coerce.number(), z.literal('')]).optional(),
});

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

type Category = { id: number; name: string; type: string; color?: string };
type Contact = { id: number; name: string; phone: string | null; email: string | null };
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

function pageSizeParam(value: string | null) {
	const parsed = Number(value);
	return PAGE_SIZES.has(parsed) ? parsed : 25;
}

function sortByParam(value: string | null): TransactionSortBy {
	return value && SORT_FIELDS.has(value as TransactionSortBy)
		? (value as TransactionSortBy)
		: 'transactionDate';
}

function sortDirectionParam(value: string | null): TransactionSortDirection {
	return value === 'asc' ? 'asc' : 'desc';
}

export const load: PageServerLoad = async ({ fetch, cookies, url }) => {
	const session = getSession(cookies);
	const accessToken = session?.accessToken;
	const authHeaders: Record<string, string> = accessToken
		? { Authorization: `Bearer ${accessToken}` }
		: {};

	const pageIndex = positiveInt(url.searchParams.get('page'), 0);
	const pageSize = pageSizeParam(url.searchParams.get('pageSize'));
	const sortBy = sortByParam(url.searchParams.get('sortBy'));
	const sortDirection = sortDirectionParam(url.searchParams.get('sortDirection'));
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

	try {
		const [txRes, catRes, conRes] = await Promise.all([
			fetch(`${BACKEND}/api/transactions?${txParams}`, { headers: authHeaders }),
			fetch(`${BACKEND}/api/categories`, { headers: authHeaders }),
			fetch(`${BACKEND}/api/contacts`, { headers: authHeaders }),
		]);

		if (txRes.ok) transactionPage = await txRes.json();
		else console.error(`[transactions] load: backend returned ${txRes.status} for transactions`);

		if (catRes.ok) categories = await catRes.json();
		else console.error(`[transactions] load: backend returned ${catRes.status} for categories`);

		if (conRes.ok) contacts = await conRes.json();
		else console.error(`[transactions] load: backend returned ${conRes.status} for contacts`);
	} catch {
		console.error('[transactions] load: backend unreachable');
	}

	const today = new Date().toISOString().split('T')[0];
	const form = await superValidate(
		{ amount: 0, direction: 'INGRESS' as const, description: '', transactionDate: today, categoryId: 0, contactId: '' as '' },
		zod4(transactionSchema),
		);

	return { transactions: transactionPage.items, transactionPage, categories, contacts, form };
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
		if (!res.ok) {
			console.error(`[transactions] delete: backend error ${res.status}`);
			return fail(502, { message: m.error_unexpected_delete_transaction() });
		}

		console.log(`[transactions] delete: success — id: ${id}`);
		return {};
	},
};
