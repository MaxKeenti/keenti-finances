import { error, fail } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';

const BACKEND = 'http://localhost:8080';

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
	createdAt: string;
};

type PaymentRecord = {
	id: number;
	subscriptionId: number;
	memberId: number | null;
	billingDate: string;
	amount: number;
	status: string;
	paidDate: string | null;
	createdAt: string;
};

export const load: PageServerLoad = async ({ params, fetch }) => {
	const id = params.id;

	let subscription: Subscription;
	let members: MemberResponse[] = [];
	let payments: PaymentRecord[] = [];

	try {
		const subRes = await fetch(`${BACKEND}/api/subscriptions/${id}`);
		if (subRes.status === 404) error(404, 'Subscription not found');
		if (!subRes.ok) {
			console.error(`[subscriptions/${id}] load: backend returned ${subRes.status}`);
			error(502, 'Could not load subscription');
		}
		subscription = await subRes.json();
	} catch (e) {
		if ((e as { status?: number }).status) throw e;
		console.error(`[subscriptions/${id}] load: backend unreachable`);
		error(502, 'Backend unreachable');
	}

	try {
		const [membersRes, paymentsRes] = await Promise.all([
			fetch(`${BACKEND}/api/subscriptions/${id}/members`),
			fetch(`${BACKEND}/api/subscriptions/${id}/payments`),
		]);

		if (membersRes.ok) members = await membersRes.json();
		else console.error(`[subscriptions/${id}] load: members returned ${membersRes.status}`);

		if (paymentsRes.ok) payments = await paymentsRes.json();
		else console.error(`[subscriptions/${id}] load: payments returned ${paymentsRes.status}`);
	} catch {
		console.error(`[subscriptions/${id}] load: backend unreachable for members/payments`);
	}

	return { subscription, members, payments };
};

export const actions: Actions = {
	recordPayment: async ({ params, request, fetch }) => {
		const id = params.id;
		const data = await request.formData();
		const paymentId = data.get('paymentId');

		if (!paymentId) return fail(400, { message: 'Missing paymentId.' });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/subscriptions/${id}/payments/${paymentId}`, {
				method: 'PUT',
			});
		} catch {
			console.error(`[subscriptions/${id}] recordPayment: backend unreachable`);
			return fail(502, { message: 'Could not reach backend service.' });
		}

		if (res.status === 404) return fail(404, { message: 'Payment record not found.' });
		if (!res.ok) {
			console.error(`[subscriptions/${id}] recordPayment: backend error ${res.status}`);
			return fail(502, { message: 'Unexpected error recording payment.' });
		}

		console.log(
			`[subscriptions/${id}] recordPayment: success — paymentId: ${paymentId}`,
		);
		return {};
	},
};
