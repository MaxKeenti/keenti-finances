import { error } from '@sveltejs/kit';
import type { PageServerLoad } from './$types';

const BACKEND = 'http://localhost:8080';

type PaymentSummary = {
	paymentId: number;
	billingDate: string;
	amount: number;
	status: string;
	paidDate: string | null;
};

type MemberPaymentSummary = {
	memberId: number;
	contactId: number | null;
	contactName: string | null;
	shareAmount: number | null;
	payments: PaymentSummary[];
};

export type PublicSubscriptionData = {
	subscriptionName: string;
	cost: number;
	billingCycle: string;
	nextBillingDate: string;
	members: MemberPaymentSummary[];
};

export const load: PageServerLoad = async ({ params, fetch }) => {
	const token = params.token;

	let res: Response;
	try {
		res = await fetch(`${BACKEND}/api/public/subscriptions/${token}`);
	} catch {
		console.error(`[public/subscription/${token}] load: backend unreachable`);
		error(502, 'Backend unreachable');
	}

	if (res.status === 404) {
		console.info(`[public/subscription/${token}] load: token not found`);
		error(404, 'Subscription not found');
	}

	if (!res.ok) {
		console.error(`[public/subscription/${token}] load: backend returned ${res.status}`);
		error(502, 'Could not load subscription');
	}

	const data: PublicSubscriptionData = await res.json();
	return { subscription: data };
};
