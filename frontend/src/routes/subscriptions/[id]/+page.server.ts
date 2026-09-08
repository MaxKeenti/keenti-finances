import { error, fail } from '@sveltejs/kit';
import { getSession } from '$lib/server/workos-session';
import { m } from '$lib/paraglide/messages.js';
import type { Actions, PageServerLoad } from './$types';
import { loadSection } from '$lib/server/section-load';
import {
	parseMembers,
	parsePayments,
	parseSubscription,
	parseTransactions,
	type MemberResponse,
	type PaymentRecord,
	type TransactionResponse,
} from '$lib/server/payloads';
import { sectionOk, sectionUnavailable, type Section } from '$lib/types/section';

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

export const load: PageServerLoad = async ({ params, fetch, cookies }) => {
	const id = params.id;

	const session = getSession(cookies);
	const accessToken = session?.accessToken;
	const authHeaders: Record<string, string> = accessToken
		? { Authorization: `Bearer ${accessToken}` }
		: {};

	// The subscription itself is the page: without it there is nothing to show,
	// so it stays a hard failure. Everything else is a secondary section that
	// reports its own availability.
	let subRes: Response;
	try {
		subRes = await fetch(`${BACKEND}/api/subscriptions/${id}`, { headers: authHeaders });
	} catch {
		console.error(`[subscriptions/${id}] load: backend unreachable`);
		error(502, m.error_backend_unreachable());
	}
	if (subRes.status === 404) error(404, m.error_subscription_not_found());
	if (!subRes.ok) {
		console.error(`[subscriptions/${id}] load: backend returned ${subRes.status}`);
		error(502, m.error_could_not_load_subscription());
	}
	const subscription = parseSubscription(await subRes.json().catch(() => null));
	if (!subscription) {
		console.error(`[subscriptions/${id}] load: subscription payload failed validation`);
		error(502, m.error_could_not_load_subscription());
	}

	// Requested together but resolved independently: a failed payments request
	// must not turn a populated member list into an empty one.
	const [members, payments, linkedTransactions, allTransactions] = await Promise.all([
		loadSection<MemberResponse[]>(fetch, `${BACKEND}/api/subscriptions/${id}/members`, {
			parse: parseMembers,
			headers: authHeaders,
			label: `subscriptions/${id}/members`,
		}),
		loadSection<PaymentRecord[]>(fetch, `${BACKEND}/api/subscriptions/${id}/payments`, {
			parse: parsePayments,
			headers: authHeaders,
			label: `subscriptions/${id}/payments`,
		}),
		loadSection<TransactionResponse[]>(
			fetch,
			`${BACKEND}/api/subscriptions/${id}/linked-transactions`,
			{
				parse: parseTransactions,
				headers: authHeaders,
				label: `subscriptions/${id}/linked-transactions`,
			},
		),
		loadSection<TransactionResponse[]>(fetch, `${BACKEND}/api/transactions`, {
			parse: parseTransactions,
			headers: authHeaders,
			label: `subscriptions/${id}/transactions`,
		}),
	]);

	// Candidates for linking need both lists: without the linked set we cannot
	// tell an unlinked income from one that is already attached here, so the
	// linking actions are reported unavailable rather than offered a guess.
	let unlinkedTransactions: Section<TransactionResponse[]>;
	if (allTransactions.status !== 'ok') {
		unlinkedTransactions = sectionUnavailable(allTransactions.reason);
	} else if (linkedTransactions.status !== 'ok') {
		unlinkedTransactions = sectionUnavailable(linkedTransactions.reason);
	} else {
		const linkedIds = new Set(linkedTransactions.data.map((t) => t.id));
		unlinkedTransactions = sectionOk(
			allTransactions.data.filter(
				(t) => t.direction === 'INGRESS' && !t.subscriptionId && !linkedIds.has(t.id),
			),
		);
	}

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

	deleteBillingPeriod: async ({ params, request, fetch, cookies }) => {
		const id = params.id;
		const billingDate = (await request.formData()).get('billingDate');
		if (typeof billingDate !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(billingDate)) {
			return fail(400, { message: m.subscriptions_billing_delete_failed() });
		}
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		let res: Response;
		try {
			res = await fetch(
				`${BACKEND}/api/subscriptions/${id}/payments/period/${encodeURIComponent(billingDate)}`,
				{ method: 'DELETE', headers: authHeaders },
			);
		} catch {
			console.error(`[subscriptions/${id}] deleteBillingPeriod: backend unreachable`);
			return fail(502, { message: m.error_backend_unreachable() });
		}

		if (res.status === 404) return fail(404, { message: m.error_payment_record_not_found() });
		if (res.status === 409) return fail(409, { message: m.subscriptions_billing_delete_blocked() });
		if (!res.ok) {
			console.error(`[subscriptions/${id}] deleteBillingPeriod: backend error ${res.status}`);
			return fail(502, { message: m.subscriptions_billing_delete_failed() });
		}

		const result = await res.json();
		return { deleted: result.deleted as number };
	},
};
