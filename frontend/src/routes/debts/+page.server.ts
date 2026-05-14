import { fail } from '@sveltejs/kit';
import { superValidate } from 'sveltekit-superforms';
import { zod4 } from 'sveltekit-superforms/adapters';
import { z } from 'zod';
import type { Actions, PageServerLoad } from './$types';

const debtSchema = z.object({
	id: z.coerce.number().optional(),
	contactId: z.coerce.number().positive('Contact is required'),
	description: z.string().min(1, 'Description is required'),
	totalAmount: z.coerce.number().positive('Total amount must be greater than 0'),
});

const BACKEND = 'http://localhost:8080';

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

export const load: PageServerLoad = async ({ fetch }) => {
	let debts: Debt[] = [];
	let contacts: Contact[] = [];

	try {
		const [debtRes, conRes] = await Promise.all([
			fetch(`${BACKEND}/api/debts`),
			fetch(`${BACKEND}/api/contacts`),
		]);

		if (debtRes.ok) debts = await debtRes.json();
		else console.error(`[debts] load: backend returned ${debtRes.status} for debts`);

		if (conRes.ok) contacts = await conRes.json();
		else console.error(`[debts] load: backend returned ${conRes.status} for contacts`);
	} catch {
		console.error('[debts] load: backend unreachable');
	}

	const form = await superValidate(
		{ contactId: 0, description: '', totalAmount: 0 },
		zod4(debtSchema),
	);

	return { debts, contacts, form };
};

export const actions: Actions = {
	create: async ({ request, fetch }) => {
		const form = await superValidate(request, zod4(debtSchema));
		if (!form.valid) return fail(400, { form });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/debts`, {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({
					contactId: form.data.contactId,
					description: form.data.description,
					totalAmount: form.data.totalAmount,
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

	update: async ({ request, fetch }) => {
		const form = await superValidate(request, zod4(debtSchema));
		if (!form.valid) return fail(400, { form });

		const id = form.data.id;
		if (!id) return fail(400, { form: { ...form, message: 'Missing debt id for update.' } });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/debts/${id}`, {
				method: 'PUT',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({
					contactId: form.data.contactId,
					description: form.data.description,
					totalAmount: form.data.totalAmount,
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

	delete: async ({ request, fetch }) => {
		const data = await request.formData();
		const id = data.get('id');

		if (!id) return fail(400, { message: 'Missing debt id.' });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/debts/${id}`, { method: 'DELETE' });
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
