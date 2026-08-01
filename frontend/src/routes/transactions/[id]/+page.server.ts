import { error, fail, redirect } from '@sveltejs/kit';
import { superValidate } from 'sveltekit-superforms';
import { zod4 } from 'sveltekit-superforms/adapters';
import { m } from '$lib/paraglide/messages.js';
import { transactionSchema } from '$lib/schemas/transaction';
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
	boxFunding: BoxFundingDto[];
	boxDistributions: BoxDistributionDto[];
	availableToSpendAmount: number;
};

export const load: PageServerLoad = async ({ params, fetch }) => {
	const id = params.id;

	let transaction: Transaction;
	let categories: Category[] = [];
	let contacts: Contact[] = [];
	let boxes: BoxDto[] = [];

	try {
		const txRes = await fetch(`${BACKEND}/api/transactions/${id}`);
		if (txRes.status === 404) error(404, m.error_transaction_not_found());
		if (!txRes.ok) {
			console.error(`[transactions/${id}] load: backend returned ${txRes.status}`);
			error(502, m.error_could_not_load_transaction());
		}
		transaction = normalizeTransactionBoxFields(await txRes.json());
	} catch (e) {
		if ((e as { status?: number }).status) throw e;
		console.error(`[transactions/${id}] load: backend unreachable`);
		error(502, m.error_backend_unreachable());
	}

	try {
		const [catRes, conRes, boxRes] = await Promise.all([
			fetch(`${BACKEND}/api/categories`),
			fetch(`${BACKEND}/api/contacts`),
			fetch(`${BACKEND}/api/boxes?archived=false`),
		]);

		if (catRes.ok) categories = await catRes.json();
		else console.error(`[transactions/${id}] load: categories returned ${catRes.status}`);

		if (conRes.ok) contacts = await conRes.json();
		else console.error(`[transactions/${id}] load: contacts returned ${conRes.status}`);

		if (boxRes.ok) boxes = await boxRes.json();
		else console.error(`[transactions/${id}] load: boxes returned ${boxRes.status}`);
	} catch {
		console.error(`[transactions/${id}] load: backend unreachable for categories/contacts/boxes`);
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
			boxFunding: transaction.boxFunding.map(({ boxId, amount }) => ({ boxId, amount })),
			boxDistributions: [],
		},
		zod4(transactionSchema),
	);

	return { transaction, categories, contacts, boxes, form };
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
					boxFunding: form.data.direction === 'EGRESS' ? form.data.boxFunding : [],
				}),
			});
		} catch {
			console.error(`[transactions/${id}] update: backend unreachable`);
			return fail(502, { form: { ...form, message: m.error_backend_unreachable() } });
		}

		if (res.status === 404) return fail(404, { form: { ...form, message: m.error_transaction_not_found() } });
		if (res.status === 400) {
			console.error(`[transactions/${id}] update: validation error from backend`);
			return fail(400, { form: { ...form, message: m.error_invalid_transaction() } });
		}
		if (res.status === 409) {
			return fail(409, { form: { ...form, message: m.error_transaction_box_conflict() } });
		}
		if (!res.ok) {
			console.error(`[transactions/${id}] update: backend error ${res.status}`);
			return fail(502, { form: { ...form, message: m.error_unexpected_update_transaction() } });
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
			return fail(502, { message: m.error_backend_unreachable() });
		}

		if (res.status === 404) return fail(404, { message: m.error_transaction_not_found() });
		if (res.status === 409) return fail(409, { message: m.error_transaction_box_conflict() });
		if (!res.ok) {
			console.error(`[transactions/${id}] delete: backend error ${res.status}`);
			return fail(502, { message: m.error_unexpected_delete_transaction() });
		}

		console.log(`[transactions/${id}] delete: success`);
		redirect(303, '/transactions');
	},
};
