import { error } from '@sveltejs/kit';
import { m } from '$lib/paraglide/messages.js';
import { logPublicSubscriptionLoad } from '$lib/server/public-subscription-log';
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
		logPublicSubscriptionLoad('backend_unreachable', 502);
		error(502, m.error_backend_unreachable());
	}

	if (res.status === 404) {
		logPublicSubscriptionLoad('not_found', 404);
		error(404, m.error_subscription_not_found());
	}

	if (!res.ok) {
		logPublicSubscriptionLoad('backend_error', res.status);
		error(502, m.error_could_not_load_subscription());
	}

	const data: PublicSubscriptionData = await res.json();
	return { subscription: data };
};
