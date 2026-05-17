import { error, fail, redirect } from '@sveltejs/kit';
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
	categoryColor: string | null;
	contactId: number | null;
	contactName: string | null;
};

export const load: PageServerLoad = async ({ params, fetch }) => {
	const id = params.id;

	let transaction: Transaction;
	let categories: Category[] = [];
	let contacts: Contact[] = [];

	try {
		const txRes = await fetch(`${BACKEND}/api/transactions/${id}`);
		if (txRes.status === 404) error(404, 'Transaction not found');
		if (!txRes.ok) {
			console.error(`[transactions/${id}] load: backend returned ${txRes.status}`);
			error(502, 'Could not load transaction');
		}
		transaction = await txRes.json();
	} catch (e) {
		if ((e as { status?: number }).status) throw e;
		console.error(`[transactions/${id}] load: backend unreachable`);
		error(502, 'Backend unreachable');
	}

	try {
		const [catRes, conRes] = await Promise.all([
			fetch(`${BACKEND}/api/categories`),
			fetch(`${BACKEND}/api/contacts`),
		]);

		if (catRes.ok) categories = await catRes.json();
		else console.error(`[transactions/${id}] load: categories returned ${catRes.status}`);

		if (conRes.ok) contacts = await conRes.json();
		else console.error(`[transactions/${id}] load: contacts returned ${conRes.status}`);
	} catch {
		console.error(`[transactions/${id}] load: backend unreachable for categories/contacts`);
	}

	const form = await superValidate(
		{
			id: transaction.id,
			amount: transaction.amount,
			direction: transaction.direction as 'INGRESS' | 'EGRESS',
			description: transaction.description ?? '',
			transactionDate: transaction.transactionDate,
			categoryId: transaction.categoryId,
			contactId: transaction.contactId ?? ('' as ''),
		},
		zod4(transactionSchema),
	);

	return { transaction, categories, contacts, form };
};

export const actions: Actions = {
	update: async ({ params, request, fetch }) => {
		const id = params.id;
		const form = await superValidate(request, zod4(transactionSchema));
		if (!form.valid) return fail(400, { form });

		const contactId = !form.data.contactId ? null : form.data.contactId;

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
			console.error(`[transactions/${id}] update: backend unreachable`);
			return fail(502, { form: { ...form, message: 'Could not reach backend service.' } });
		}

		if (res.status === 404) return fail(404, { form: { ...form, message: 'Transaction not found.' } });
		if (res.status === 400) {
			console.error(`[transactions/${id}] update: validation error from backend`);
			return fail(400, { form: { ...form, message: 'Invalid transaction data.' } });
		}
		if (!res.ok) {
			console.error(`[transactions/${id}] update: backend error ${res.status}`);
			return fail(502, { form: { ...form, message: 'Unexpected error updating transaction.' } });
		}

		console.log(`[transactions/${id}] update: success — amount: ${form.data.amount}`);
		return { form };
	},

	delete: async ({ params, fetch }) => {
		const id = params.id;

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/transactions/${id}`, { method: 'DELETE' });
		} catch {
			console.error(`[transactions/${id}] delete: backend unreachable`);
			return fail(502, { message: 'Could not reach backend service.' });
		}

		if (res.status === 404) return fail(404, { message: 'Transaction not found.' });
		if (!res.ok) {
			console.error(`[transactions/${id}] delete: backend error ${res.status}`);
			return fail(502, { message: 'Unexpected error deleting transaction.' });
		}

		console.log(`[transactions/${id}] delete: success`);
		redirect(303, '/transactions');
	},
};
