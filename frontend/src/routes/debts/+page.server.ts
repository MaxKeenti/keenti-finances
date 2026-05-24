import { fail } from '@sveltejs/kit';
import { superValidate } from 'sveltekit-superforms';
import { zod4 } from 'sveltekit-superforms/adapters';
import { z } from 'zod';
import { getSession } from '$lib/server/workos-session';
import type { Actions, PageServerLoad } from './$types';

const debtSchema = z.object({
	id: z.coerce.number().optional(),
	contactId: z.coerce.number().positive('Contact is required'),
	description: z.string().min(1, 'Description is required'),
	totalAmount: z.coerce.number().positive('Total amount must be greater than 0'),
	createdAt: z.string().min(1, 'Date is required'),
});

const bulkPaymentSchema = z.object({
	contactId: z.coerce.number().positive('Contact is required'),
	totalAmount: z.coerce.number().positive('Amount must be greater than 0'),
	paymentDate: z.string().min(1, 'Payment date is required'),
	categoryId: z.coerce.number().positive('Category is required'),
	notes: z.string().optional(),
});

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

type Contact = { id: number; name: string; phone: string | null; email: string | null };
type Debt = {
	id: number;
	contactId: number | null;
	contactName: string | null;
	description: string;
	totalAmount: number;
	totalPaid: number;
	remaining: number;
	status: string;
	createdAt: string;
};
type Category = { id: number; name: string; type: string };

export const load: PageServerLoad = async ({ fetch, cookies }) => {
	const session = getSession(cookies);
	const accessToken = session?.accessToken;
	const authHeaders: Record<string, string> = accessToken
		? { Authorization: `Bearer ${accessToken}` }
		: {};

	let debts: Debt[] = [];
	let contacts: Contact[] = [];
	let categories: Category[] = [];

	try {
		const [debtRes, conRes, catRes] = await Promise.all([
			fetch(`${BACKEND}/api/debts`, { headers: authHeaders }),
			fetch(`${BACKEND}/api/contacts`, { headers: authHeaders }),
			fetch(`${BACKEND}/api/categories`, { headers: authHeaders }),
		]);

		if (debtRes.ok) debts = await debtRes.json();
		else console.error(`[debts] load: backend returned ${debtRes.status} for debts`);

		if (conRes.ok) contacts = await conRes.json();
		else console.error(`[debts] load: backend returned ${conRes.status} for contacts`);

		if (catRes.ok) categories = await catRes.json();
		else console.error(`[debts] load: backend returned ${catRes.status} for categories`);
	} catch {
		console.error('[debts] load: backend unreachable');
	}

	const today = new Date().toISOString().split('T')[0];
	const [form, bulkForm] = await Promise.all([
		superValidate({ contactId: 0, description: '', totalAmount: 0, createdAt: today }, zod4(debtSchema)),
		superValidate(
			{ contactId: 0, totalAmount: 0, paymentDate: today, categoryId: 0, notes: '' },
			zod4(bulkPaymentSchema),
		),
	]);

	return { debts, contacts, categories, form, bulkForm };
};

export const actions: Actions = {
	create: async ({ request, fetch, cookies }) => {
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		const form = await superValidate(request, zod4(debtSchema));
		if (!form.valid) return fail(400, { form });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/debts`, {
				method: 'POST',
				headers: { 'content-type': 'application/json', ...authHeaders },
				body: JSON.stringify({
					contactId: form.data.contactId,
					description: form.data.description,
					totalAmount: form.data.totalAmount,
					createdAt: form.data.createdAt || null,
				}),
			});
		} catch {
			console.error('[debts] create: backend unreachable');
			return fail(502, { form: { ...form, message: 'Could not reach backend service.' } });
		}

		if (res.status === 400) {
			console.error('[debts] create: validation error from backend');
			return fail(400, { form: { ...form, message: 'Invalid debt data.' } });
		}
		if (res.status === 404) {
			return fail(404, { form: { ...form, message: 'Contact not found.' } });
		}
		if (!res.ok) {
			console.error(`[debts] create: backend error ${res.status}`);
			return fail(502, { form: { ...form, message: 'Unexpected error creating debt.' } });
		}

		console.log(
			`[debts] create: success — contactId: ${form.data.contactId} amount: ${form.data.totalAmount}`,
		);
		return { form };
	},

	update: async ({ request, fetch, cookies }) => {
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		const form = await superValidate(request, zod4(debtSchema));
		if (!form.valid) return fail(400, { form });

		const id = form.data.id;
		if (!id) return fail(400, { form: { ...form, message: 'Missing debt id for update.' } });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/debts/${id}`, {
				method: 'PUT',
				headers: { 'content-type': 'application/json', ...authHeaders },
				body: JSON.stringify({
					contactId: form.data.contactId,
					description: form.data.description,
					totalAmount: form.data.totalAmount,
					createdAt: form.data.createdAt || null,
				}),
			});
		} catch {
			console.error('[debts] update: backend unreachable');
			return fail(502, { form: { ...form, message: 'Could not reach backend service.' } });
		}

		if (res.status === 404) return fail(404, { form: { ...form, message: 'Debt or contact not found.' } });
		if (res.status === 400) {
			console.error('[debts] update: validation error from backend');
			return fail(400, { form: { ...form, message: 'Invalid debt data.' } });
		}
		if (!res.ok) {
			console.error(`[debts] update: backend error ${res.status}`);
			return fail(502, { form: { ...form, message: 'Unexpected error updating debt.' } });
		}

		console.log(`[debts] update: success — id: ${id} contactId: ${form.data.contactId}`);
		return { form };
	},

	bulkPayment: async ({ request, fetch, cookies }) => {
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		const form = await superValidate(request, zod4(bulkPaymentSchema));
		if (!form.valid) return fail(400, { bulkForm: form });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/debts/bulk-payment`, {
				method: 'POST',
				headers: { 'content-type': 'application/json', ...authHeaders },
				body: JSON.stringify({
					contactId: form.data.contactId,
					totalAmount: form.data.totalAmount,
					paymentDate: form.data.paymentDate,
					categoryId: form.data.categoryId,
					notes: form.data.notes || null,
				}),
			});
		} catch {
			console.error('[debts] bulkPayment: backend unreachable');
			return fail(502, { bulkForm: { ...form, message: 'Could not reach backend service.' } });
		}

		if (res.status === 400) {
			const body = await res.json().catch(() => ({}));
			const msg = body?.error ?? 'Invalid bulk payment data.';
			console.error(`[debts] bulkPayment: validation error — ${msg}`);
			return fail(400, { bulkForm: { ...form, message: msg } });
		}
		if (res.status === 404) {
			return fail(404, { bulkForm: { ...form, message: 'Contact not found.' } });
		}
		if (!res.ok) {
			console.error(`[debts] bulkPayment: backend error ${res.status}`);
			return fail(502, { bulkForm: { ...form, message: 'Unexpected error processing bulk payment.' } });
		}

		const result = await res.json();
		console.log(
			`[debts] bulkPayment: success — contactId: ${form.data.contactId} applied: ${result.totalApplied} unused: ${result.totalUnused} debts: ${result.payments?.length}`,
		);
		return { bulkForm: form, bulkResult: result };
	},

	delete: async ({ request, fetch, cookies }) => {
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		const data = await request.formData();
		const id = data.get('id');

		if (!id) return fail(400, { message: 'Missing debt id.' });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/debts/${id}`, {
				method: 'DELETE',
				headers: authHeaders,
			});
		} catch {
			console.error('[debts] delete: backend unreachable');
			return fail(502, { message: 'Could not reach backend service.' });
		}

		if (res.status === 404) return fail(404, { message: 'Debt not found.' });
		if (!res.ok) {
			console.error(`[debts] delete: backend error ${res.status}`);
			return fail(502, { message: 'Unexpected error deleting debt.' });
		}

		console.log(`[debts] delete: success — id: ${id}`);
		return {};
	},
};
