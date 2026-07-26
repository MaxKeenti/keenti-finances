import { error } from '@sveltejs/kit';
import { m } from '$lib/paraglide/messages.js';
import type { PageServerLoad } from './$types';

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

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
		console.error('[public/subscription] load: backend unreachable');
		error(502, m.error_backend_unreachable());
	}

	if (res.status === 404) {
		console.info('[public/subscription] load: token not found');
		error(404, m.error_subscription_not_found());
	}

	if (!res.ok) {
		console.error(`[public/subscription] load: backend returned ${res.status}`);
		error(502, m.error_could_not_load_subscription());
	}

	const data: PublicSubscriptionData = await res.json();
	return { subscription: data };
};
