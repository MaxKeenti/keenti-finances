import { fail } from '@sveltejs/kit';
import { superValidate } from 'sveltekit-superforms';
import { zod4 } from 'sveltekit-superforms/adapters';
import { z } from 'zod';
import type { Actions, PageServerLoad } from './$types';

const transactionSchema = z.object({
	id: z.coerce.number().optional(),
	amount: z.coerce.number().positive('Amount must be greater than 0'),
	direction: z.enum(['INGRESS', 'EGRESS']),
	description: z.string().max(500).optional(),
	transactionDate: z.string().min(1, 'Date is required'),
	categoryId: z.coerce.number().min(1, 'Category is required'),
	contactId: z.union([z.coerce.number(), z.literal('')]).optional(),
});

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

type Category = { id: number; name: string; type: string };
type Contact = { id: number; name: string; phone: string | null; email: string | null };
type Transaction = {
	id: number;
	amount: number;
	direction: string;
	description: string | null;
	transactionDate: string;
	categoryId: number;
	categoryName: string | null;
	contactId: number | null;
	contactName: string | null;
};

export const load: PageServerLoad = async ({ fetch }) => {
	let transactions: Transaction[] = [];
	let categories: Category[] = [];
	let contacts: Contact[] = [];

	try {
		const [txRes, catRes, conRes] = await Promise.all([
			fetch(`${BACKEND}/api/transactions`),
			fetch(`${BACKEND}/api/categories`),
			fetch(`${BACKEND}/api/contacts`),
		]);

		if (txRes.ok) transactions = await txRes.json();
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

	return { transactions, categories, contacts, form };
};

export const actions: Actions = {
	create: async ({ request, fetch }) => {
		const form = await superValidate(request, zod4(transactionSchema));
		if (!form.valid) return fail(400, { form });

		const contactId = form.data.contactId === '' || form.data.contactId === undefined ? null : form.data.contactId;

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/transactions`, {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
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
			return fail(502, { form: { ...form, message: 'Could not reach backend service.' } });
		}

		if (res.status === 400) {
			console.error('[transactions] create: validation error from backend');
			return fail(400, { form: { ...form, message: 'Invalid transaction data.' } });
		}
		if (!res.ok) {
			console.error(`[transactions] create: backend error ${res.status}`);
			return fail(502, { form: { ...form, message: 'Unexpected error creating transaction.' } });
		}

		console.log(`[transactions] create: success — amount: ${form.data.amount} direction: ${form.data.direction}`);
		return { form };
	},

	update: async ({ request, fetch }) => {
		const form = await superValidate(request, zod4(transactionSchema));
		if (!form.valid) return fail(400, { form });

		const id = form.data.id;
		if (!id) return fail(400, { form: { ...form, message: 'Missing transaction id for update.' } });

		const contactId = form.data.contactId === '' || form.data.contactId === undefined ? null : form.data.contactId;

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/transactions/${id}`, {
				method: 'PUT',
				headers: { 'content-type': 'application/json' },
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
			return fail(502, { form: { ...form, message: 'Could not reach backend service.' } });
		}

		if (res.status === 404) return fail(404, { form: { ...form, message: 'Transaction not found.' } });
		if (res.status === 400) {
			console.error('[transactions] update: validation error from backend');
			return fail(400, { form: { ...form, message: 'Invalid transaction data.' } });
		}
		if (!res.ok) {
			console.error(`[transactions] update: backend error ${res.status}`);
			return fail(502, { form: { ...form, message: 'Unexpected error updating transaction.' } });
		}

		console.log(`[transactions] update: success — id: ${id} amount: ${form.data.amount}`);
		return { form };
	},

	delete: async ({ request, fetch }) => {
		const data = await request.formData();
		const id = data.get('id');

		if (!id) return fail(400, { message: 'Missing transaction id.' });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/transactions/${id}`, { method: 'DELETE' });
		} catch {
			console.error('[transactions] delete: backend unreachable');
			return fail(502, { message: 'Could not reach backend service.' });
		}

		if (res.status === 404) return fail(404, { message: 'Transaction not found.' });
		if (!res.ok) {
			console.error(`[transactions] delete: backend error ${res.status}`);
			return fail(502, { message: 'Unexpected error deleting transaction.' });
		}

		console.log(`[transactions] delete: success — id: ${id}`);
		return {};
	},
};
