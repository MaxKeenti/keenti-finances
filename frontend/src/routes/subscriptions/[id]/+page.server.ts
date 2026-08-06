import { error, fail } from '@sveltejs/kit';
import { getSession } from '$lib/server/workos-session';
import { m } from '$lib/paraglide/messages.js';
import type { Actions, PageServerLoad } from './$types';

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

type MemberResponse = {
	id: number;
	subscriptionId: number;
	contactId: number | null;
	contactName: string | null;
	shareAmount: number | null;
	createdAt: string;
};

type Subscription = {
	id: number;
	name: string;
	cost: number;
	billingCycle: string;
	type: string;
	categoryId: number | null;
	nextBillingDate: string;
	tokenUuid: string | null;
	ownerParticipates: boolean | null;
	createdAt: string;
};

type TransactionResponse = {
	id: number;
	amount: number;
	direction: string;
	description: string;
	transactionDate: string;
	categoryId: number | null;
	categoryName: string | null;
	categoryHue: number | null;
	contactId: number | null;
	contactName: string | null;
	subscriptionId: number | null;
};

type PaymentRecord = {
	id: number;
	subscriptionId: number;
	memberId: number | null;
	billingDate: string;
	amount: number;
	status: string;
	paidDate: string | null;
	transactionId: number | null;
	createdAt: string;
};

export const load: PageServerLoad = async ({ params, fetch, cookies }) => {
	const id = params.id;

	const session = getSession(cookies);
	const accessToken = session?.accessToken;
	const authHeaders: Record<string, string> = accessToken
		? { Authorization: `Bearer ${accessToken}` }
		: {};

	let subscription: Subscription;
	let members: MemberResponse[] = [];
	let payments: PaymentRecord[] = [];
	let linkedTransactions: TransactionResponse[] = [];
	let allTransactions: TransactionResponse[] = [];

	try {
		const subRes = await fetch(`${BACKEND}/api/subscriptions/${id}`, { headers: authHeaders });
		if (subRes.status === 404) error(404, m.error_subscription_not_found());
		if (!subRes.ok) {
			console.error(`[subscriptions/${id}] load: backend returned ${subRes.status}`);
			error(502, m.error_could_not_load_subscription());
		}
		subscription = await subRes.json();
	} catch (e) {
		if ((e as { status?: number }).status) throw e;
		console.error(`[subscriptions/${id}] load: backend unreachable`);
		error(502, m.error_backend_unreachable());
	}

	try {
		const [membersRes, paymentsRes, linkedRes, allTxRes] = await Promise.all([
			fetch(`${BACKEND}/api/subscriptions/${id}/members`, { headers: authHeaders }),
			fetch(`${BACKEND}/api/subscriptions/${id}/payments`, { headers: authHeaders }),
			fetch(`${BACKEND}/api/subscriptions/${id}/linked-transactions`, { headers: authHeaders }),
			fetch(`${BACKEND}/api/transactions`, { headers: authHeaders }),
		]);

		if (membersRes.ok) members = await membersRes.json();
		else console.error(`[subscriptions/${id}] load: members returned ${membersRes.status}`);

		if (paymentsRes.ok) payments = await paymentsRes.json();
		else console.error(`[subscriptions/${id}] load: payments returned ${paymentsRes.status}`);

		if (linkedRes.ok) linkedTransactions = await linkedRes.json();
		else console.error(`[subscriptions/${id}] load: linked-transactions returned ${linkedRes.status}`);

		if (allTxRes.ok) allTransactions = await allTxRes.json();
		else console.error(`[subscriptions/${id}] load: transactions returned ${allTxRes.status}`);
	} catch {
		console.error(`[subscriptions/${id}] load: backend unreachable for members/payments/transactions`);
	}

	const linkedIds = new Set(linkedTransactions.map((t) => t.id));
	const unlinkedTransactions = allTransactions.filter(
		(t) => t.direction === 'EGRESS' && !t.subscriptionId && !linkedIds.has(t.id),
	);

	return { subscription, members, payments, linkedTransactions, unlinkedTransactions };
};

export const actions: Actions = {
	recordPayment: async ({ params, request, fetch, cookies }) => {
		const id = params.id;
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		const data = await request.formData();
		const paymentId = data.get('paymentId');

		if (!paymentId) return fail(400, { message: m.error_missing_payment_id() });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/subscriptions/${id}/payments/${paymentId}`, {
				method: 'PUT',
				headers: authHeaders,
			});
		} catch {
			console.error(`[subscriptions/${id}] recordPayment: backend unreachable`);
			return fail(502, { message: m.error_backend_unreachable() });
		}

		if (res.status === 404) return fail(404, { message: m.error_payment_record_not_found() });
		if (!res.ok) {
			console.error(`[subscriptions/${id}] recordPayment: backend error ${res.status}`);
			return fail(502, { message: m.error_unexpected_record_payment() });
		}

		console.log(
			`[subscriptions/${id}] recordPayment: success — paymentId: ${paymentId}`,
		);
		return {};
	},

	linkTransactionToPayment: async ({ params, request, fetch, cookies }) => {
		const id = params.id;
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		const data = await request.formData();
		const paymentId = data.get('paymentId');
		const transactionId = data.get('transactionId');

		if (!paymentId) return fail(400, { message: m.error_missing_payment_id() });
		if (!transactionId) return fail(400, { message: m.error_no_transaction_selected() });

		let res: Response;
		try {
			res = await fetch(
				`${BACKEND}/api/subscriptions/${id}/payments/${paymentId}/link-transaction`,
				{
					method: 'PUT',
					headers: { 'content-type': 'application/json', ...authHeaders },
					body: JSON.stringify({ transactionId: Number(transactionId) }),
				},
			);
		} catch {
			console.error(`[subscriptions/${id}] linkTransactionToPayment: backend unreachable`);
			return fail(502, { message: m.error_backend_unreachable() });
		}

		if (res.status === 404) return fail(404, { message: m.error_payment_or_transaction_not_found() });
		if (res.status === 409) return fail(409, { message: m.error_payment_already_paid() });
		if (!res.ok) {
			console.error(`[subscriptions/${id}] linkTransactionToPayment: backend error ${res.status}`);
			return fail(502, { message: m.subscriptions_transaction_link_failed() });
		}

		console.log(
			`[subscriptions/${id}] linkTransactionToPayment: success — paymentId=${paymentId} transactionId=${transactionId}`,
		);
		return {};
	},

	linkTransactions: async ({ params, request, fetch, cookies }) => {
		const id = params.id;
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		const data = await request.formData();
		const transactionIds = data.getAll('transactionId').map(Number).filter(Boolean);

		if (transactionIds.length === 0)
			return fail(400, { message: m.error_no_transactions_selected() });

		try {
			const results = await Promise.all(
				transactionIds.map((txId) =>
					fetch(`${BACKEND}/api/transactions/${txId}/link-subscription`, {
						method: 'PUT',
						headers: { 'content-type': 'application/json', ...authHeaders },
						body: JSON.stringify({ subscriptionId: Number(id) }),
					}),
				),
			);
			const failed = results.filter((r) => !r.ok);
			if (failed.length > 0) {
				console.error(`[subscriptions/${id}] linkTransactions: ${failed.length} requests failed`);
				return fail(502, { message: m.subscriptions_transactions_link_failed() });
			}
		} catch {
			console.error(`[subscriptions/${id}] linkTransactions: backend unreachable`);
			return fail(502, { message: m.error_backend_unreachable() });
		}

		console.log(`[transaction.link] subscriptionId=${id} count=${transactionIds.length} ids=${transactionIds.join(',')}`);
		return {};
	},

	generateBilling: async ({ params, fetch, cookies }) => {
		const id = params.id;
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/subscriptions/${id}/generate-billing`, {
				method: 'POST',
				headers: authHeaders,
			});
		} catch {
			console.error(`[subscriptions/${id}] generateBilling: backend unreachable`);
			return fail(502, { message: m.error_backend_unreachable() });
		}

		if (res.status === 404) return fail(404, { message: m.error_subscription_not_found() });
		if (!res.ok) {
			console.error(`[subscriptions/${id}] generateBilling: backend error ${res.status}`);
			return fail(502, { message: m.subscriptions_billing_failed() });
		}

		const result = await res.json();
		const count: number = result.generated ?? 0;
		console.log(`[billing.generate] subscriptionId=${id} generated=${count}`);
		return { generated: count };
	},
};
