import { error, fail } from '@sveltejs/kit';
import { superValidate } from 'sveltekit-superforms';
import { zod4 } from 'sveltekit-superforms/adapters';
import { z } from 'zod';
import type { Actions, PageServerLoad } from './$types';

const paymentSchema = z.object({
	amount: z.coerce.number().positive('Amount must be greater than 0'),
	paymentDate: z.string().min(1, 'Payment date is required'),
	categoryId: z.coerce.number().positive('Category is required'),
	notes: z.string().optional(),
});

const BACKEND = 'http://localhost:8080';

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

type DebtPayment = {
	id: number;
	debtId: number;
	amount: number;
	paymentDate: string;
	transactionId: number | null;
	notes: string | null;
	createdAt: string;
};

type Category = { id: number; name: string; type: string };

export const load: PageServerLoad = async ({ params, fetch }) => {
	const id = params.id;

	let debt: Debt;
	let payments: DebtPayment[] = [];
	let categories: Category[] = [];

	try {
		const debtRes = await fetch(`${BACKEND}/api/debts/${id}`);
		if (debtRes.status === 404) error(404, 'Debt not found');
		if (!debtRes.ok) {
			console.error(`[debts/${id}] load: backend returned ${debtRes.status}`);
			error(502, 'Could not load debt');
		}
		debt = await debtRes.json();
	} catch (e) {
		if ((e as { status?: number }).status) throw e;
		console.error(`[debts/${id}] load: backend unreachable`);
		error(502, 'Backend unreachable');
	}

	try {
		const [paymentsRes, categoriesRes] = await Promise.all([
			fetch(`${BACKEND}/api/debts/${id}/payments`),
			fetch(`${BACKEND}/api/categories`),
		]);

		if (paymentsRes.ok) payments = await paymentsRes.json();
		else console.error(`[debts/${id}] load: payments returned ${paymentsRes.status}`);

		if (categoriesRes.ok) categories = await categoriesRes.json();
		else console.error(`[debts/${id}] load: categories returned ${categoriesRes.status}`);
	} catch {
		console.error(`[debts/${id}] load: backend unreachable for payments/categories`);
	}

	const today = new Date().toISOString().split('T')[0];
	const form = await superValidate(
		{ amount: debt.remaining, paymentDate: today, categoryId: 0, notes: '' },
		zod4(paymentSchema),
	);

	return { debt, payments, categories, form };
};

export const actions: Actions = {
	recordPayment: async ({ params, request, fetch }) => {
		const id = params.id;
		const form = await superValidate(request, zod4(paymentSchema));
		if (!form.valid) return fail(400, { form });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/debts/${id}/payments`, {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({
					amount: form.data.amount,
					paymentDate: form.data.paymentDate,
					categoryId: form.data.categoryId,
					notes: form.data.notes || null,
				}),
			});
		} catch {
			console.error(`[debts/${id}] recordPayment: backend unreachable`);
			return fail(502, { form: { ...form, message: 'Could not reach backend service.' } });
		}

		if (res.status === 400) {
			const body = await res.json().catch(() => ({}));
			const msg = body?.error ?? 'Invalid payment data.';
			console.error(`[debts/${id}] recordPayment: validation error — ${msg}`);
			return fail(400, { form: { ...form, message: msg } });
		}
		if (res.status === 404) {
			return fail(404, { form: { ...form, message: 'Debt not found.' } });
		}
		if (!res.ok) {
			console.error(`[debts/${id}] recordPayment: backend error ${res.status}`);
			return fail(502, { form: { ...form, message: 'Unexpected error recording payment.' } });
		}

		const payment = await res.json();
		console.log(
			`[debts/${id}] recordPayment: success — paymentId: ${payment.id} amount: ${form.data.amount} transactionId: ${payment.transactionId}`,
		);
		return { form };
	},
};
