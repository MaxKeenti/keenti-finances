import { error, fail } from '@sveltejs/kit';
import { superValidate } from 'sveltekit-superforms';
import { zod4 } from 'sveltekit-superforms/adapters';
import { z } from 'zod';
import { getSession } from '$lib/server/workos-session';
import { m } from '$lib/paraglide/messages.js';
import type { Actions, PageServerLoad } from './$types';
import { dateInTimeZone } from '$lib/formatting';
import { boxAllocationSchema } from '$lib/schemas/transaction';
import { allocationTotal } from '$lib/types/transactions';
import type { BoxDto } from '$lib/types/boxes';

const paymentSchema = z
	.object({
		amount: z.coerce.number().positive(m.validation_amount_positive()),
		paymentDate: z.string().min(1, m.validation_payment_date_required()),
		categoryId: z.coerce.number().positive(m.validation_category_required()),
		accountId: z.union([z.literal(''), z.coerce.number().positive()]).optional(),
		notes: z.string().optional(),
		// Only a payment on a Debt the User owes can carry these; the loader
		// refuses to offer the editor otherwise, and the backend rejects it too.
		boxFunding: z.array(boxAllocationSchema).default([]),
	})
	.superRefine((value, context) => {
		const seen = new Set<number>();
		for (let index = 0; index < value.boxFunding.length; index += 1) {
			const boxId = value.boxFunding[index]?.boxId;
			if (seen.has(boxId)) {
				context.addIssue({
					code: 'custom',
					message: m.transactions_box_duplicate(),
					path: ['boxFunding', index, 'boxId'],
				});
			}
			seen.add(boxId);
		}
		if (allocationTotal(value.boxFunding) > value.amount) {
			context.addIssue({
				code: 'custom',
				message: m.transactions_box_funding_over_total(),
				path: ['boxFunding'],
			});
		}
	});

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

type Debt = {
	id: number;
	contactId: number | null;
	contactName: string | null;
	direction: string;
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
type FinancialAccount = { id: number; name: string; kind: string; balance: number };

export const load: PageServerLoad = async ({ params, fetch, cookies, parent }) => {
	const id = params.id;

	const session = getSession(cookies);
	const accessToken = session?.accessToken;
	const authHeaders: Record<string, string> = accessToken
		? { Authorization: `Bearer ${accessToken}` }
		: {};

	let debt: Debt;
	let payments: DebtPayment[] = [];
	let categories: Category[] = [];
	let accounts: FinancialAccount[] = [];
	let boxes: BoxDto[] = [];
	let accountTracking = { active: false, setupRequired: false };

	try {
		const debtRes = await fetch(`${BACKEND}/api/debts/${id}`, { headers: authHeaders });
		if (debtRes.status === 404) error(404, m.error_debt_not_found());
		if (!debtRes.ok) {
			console.error(`[debts/${id}] load: backend returned ${debtRes.status}`);
			error(502, m.error_could_not_load_debt());
		}
		debt = await debtRes.json();
	} catch (e) {
		if ((e as { status?: number }).status) throw e;
		console.error(`[debts/${id}] load: backend unreachable`);
		error(502, m.error_backend_unreachable());
	}

	try {
		const [paymentsRes, categoriesRes, accountsRes, trackingRes, boxesRes] = await Promise.all([
			fetch(`${BACKEND}/api/debts/${id}/payments`, { headers: authHeaders }),
			fetch(`${BACKEND}/api/categories`, { headers: authHeaders }),
			fetch(`${BACKEND}/api/accounts`, { headers: authHeaders }),
			fetch(`${BACKEND}/api/accounts/status`, { headers: authHeaders }),
			fetch(`${BACKEND}/api/boxes?archived=false`, { headers: authHeaders }),
		]);

		if (paymentsRes.ok) payments = await paymentsRes.json();
		else console.error(`[debts/${id}] load: payments returned ${paymentsRes.status}`);

		if (categoriesRes.ok) categories = await categoriesRes.json();
		else console.error(`[debts/${id}] load: categories returned ${categoriesRes.status}`);

		if (accountsRes.ok) accounts = await accountsRes.json();
		if (trackingRes.ok) accountTracking = await trackingRes.json();

		// Boxes only feed the funding editor. An empty list reads as "no Boxes to
		// draw from", which is the same thing the editor says when there are none.
		if (boxesRes.ok) boxes = await boxesRes.json();
		else console.error(`[debts/${id}] load: boxes returned ${boxesRes.status}`);
	} catch {
		console.error(`[debts/${id}] load: backend unreachable for payments/categories`);
	}

	// `toISOString()` is the UTC date, which is already tomorrow for a
	// User at UTC-6 after 18:00 local. Resolve their calendar day instead.
	const { preferences } = await parent();
	const today = dateInTimeZone(preferences.timeZone);
	// `categoryId: 0` is a placeholder for "not chosen yet", not a mistake the
	// User made. Suppress validation on load so an untouched form does not open
	// showing a required-Category error; submitting still validates.
	const form = await superValidate(
		{
			amount: debt.remaining,
			paymentDate: today,
			categoryId: 0,
			accountId: '' as '',
			notes: '',
			boxFunding: [],
		},
		zod4(paymentSchema),
		{ errors: false },
	);

	return { debt, payments, categories, accounts, boxes, accountTracking, form };
};

export const actions: Actions = {
	recordPayment: async ({ params, request, fetch, cookies }) => {
		const id = params.id;
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		const form = await superValidate(request, zod4(paymentSchema));
		if (!form.valid) return fail(400, { form });
		const accountId = !form.data.accountId ? null : form.data.accountId;

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/debts/${id}/payments`, {
				method: 'POST',
				headers: { 'content-type': 'application/json', ...authHeaders },
				body: JSON.stringify({
					amount: form.data.amount,
					paymentDate: form.data.paymentDate,
					categoryId: form.data.categoryId,
					accountId,
					notes: form.data.notes || null,
					boxFunding: form.data.boxFunding,
				}),
			});
		} catch {
			console.error(`[debts/${id}] recordPayment: backend unreachable`);
			return fail(502, { form: { ...form, message: m.error_backend_unreachable() } });
		}

		if (res.status === 400) {
			const body = await res.json().catch(() => ({}));
			const backendMessage = body?.error ?? m.error_invalid_payment();
			console.error(`[debts/${id}] recordPayment: validation error — ${backendMessage}`);
			return fail(400, { form: { ...form, message: m.error_invalid_payment() } });
		}
		if (res.status === 404) {
			return fail(404, { form: { ...form, message: m.error_debt_not_found() } });
		}
		// A Box whose balance cannot cover its funding line is a conflict, not a
		// malformed request: the same one ordinary spending reports.
		if (res.status === 409) {
			return fail(409, { form: { ...form, message: m.error_transaction_box_conflict() } });
		}
		if (!res.ok) {
			console.error(`[debts/${id}] recordPayment: backend error ${res.status}`);
			return fail(502, { form: { ...form, message: m.error_unexpected_record_payment() } });
		}

		const payment = await res.json();
		console.log(
			`[debts/${id}] recordPayment: success — paymentId: ${payment.id} amount: ${form.data.amount} transactionId: ${payment.transactionId} boxFunding: ${form.data.boxFunding.length}`,
		);
		// ADR-0005 with ADR-0023: one Debt Payment creates exactly one Transaction,
		// in the Debt's own Direction. The backend already reports which one, so
		// the page can link to it instead of guessing; `null` means it was not
		// reported, which the page says plainly rather than inventing an id.
		return {
			form,
			recordedPayment: {
				debtId: Number(id),
				paymentId: payment.id ?? null,
				amount: form.data.amount,
				transactionId: payment.transactionId ?? null,
			},
		};
	},
};
