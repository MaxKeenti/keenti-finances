import { fail } from '@sveltejs/kit';
import { superValidate } from 'sveltekit-superforms';
import { zod4 } from 'sveltekit-superforms/adapters';
import { z } from 'zod';
import type { Actions, PageServerLoad } from './$types';

const subscriptionSchema = z.object({
	id: z.coerce.number().optional(),
	name: z.string().min(1, 'Name is required'),
	cost: z.coerce.number().positive('Cost must be greater than 0'),
	billingCycle: z.enum(['MONTHLY', 'YEARLY']),
	type: z.enum(['PERSONAL', 'SHARED']),
	categoryId: z.union([z.coerce.number(), z.literal('')]).optional(),
	nextBillingDate: z.string().min(1, 'Next billing date is required'),
	ownerParticipates: z.boolean().optional(),
});

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

type Category = { id: number; name: string; type: string };
type Contact = { id: number; name: string; phone: string | null; email: string | null };
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
	members?: MemberResponse[];
};

export const load: PageServerLoad = async ({ fetch }) => {
	let subscriptions: Subscription[] = [];
	let categories: Category[] = [];
	let contacts: Contact[] = [];

	try {
		const [subRes, catRes, conRes] = await Promise.all([
			fetch(`${BACKEND}/api/subscriptions`),
			fetch(`${BACKEND}/api/categories`),
			fetch(`${BACKEND}/api/contacts`),
		]);

		if (subRes.ok) subscriptions = await subRes.json();
		else console.error(`[subscriptions] load: backend returned ${subRes.status} for subscriptions`);

		if (catRes.ok) categories = await catRes.json();
		else console.error(`[subscriptions] load: backend returned ${catRes.status} for categories`);

		if (conRes.ok) contacts = await conRes.json();
		else console.error(`[subscriptions] load: backend returned ${conRes.status} for contacts`);

		// Fetch members for shared subscriptions
		const sharedSubs = subscriptions.filter((s) => s.type === 'SHARED');
		if (sharedSubs.length > 0) {
			const memberResults = await Promise.all(
				sharedSubs.map((s) =>
					fetch(`${BACKEND}/api/subscriptions/${s.id}/members`).then((r) =>
						r.ok ? r.json() : [],
					),
				),
			);
			sharedSubs.forEach((s, i) => {
				const sub = subscriptions.find((x) => x.id === s.id);
				if (sub) sub.members = memberResults[i];
			});
		}
	} catch {
		console.error('[subscriptions] load: backend unreachable');
	}

	const today = new Date().toISOString().split('T')[0];
	const form = await superValidate(
		{
			name: '',
			cost: 0,
			billingCycle: 'MONTHLY' as const,
			type: 'PERSONAL' as const,
			categoryId: '' as '',
			nextBillingDate: today,
			ownerParticipates: true,
		},
		zod4(subscriptionSchema),
	);

	return { subscriptions, categories, contacts, form };
};

export const actions: Actions = {
	create: async ({ request, fetch }) => {
		const form = await superValidate(request, zod4(subscriptionSchema));
		if (!form.valid) return fail(400, { form });

		const categoryId = !form.data.categoryId ? null : form.data.categoryId;

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/subscriptions`, {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({
					name: form.data.name,
					cost: form.data.cost,
					billingCycle: form.data.billingCycle,
					type: form.data.type,
					categoryId,
					nextBillingDate: form.data.nextBillingDate,
					ownerParticipates: form.data.ownerParticipates ?? true,
				}),
			});
		} catch {
			console.error('[subscriptions] create: backend unreachable');
			return fail(502, { form: { ...form, message: 'Could not reach backend service.' } });
		}

		if (res.status === 400) {
			console.error('[subscriptions] create: validation error from backend');
			return fail(400, { form: { ...form, message: 'Invalid subscription data.' } });
		}
		if (!res.ok) {
			console.error(`[subscriptions] create: backend error ${res.status}`);
			return fail(502, { form: { ...form, message: 'Unexpected error creating subscription.' } });
		}

		console.log(
			`[subscriptions] create: success — name: ${form.data.name} type: ${form.data.type}`,
		);
		return { form };
	},

	update: async ({ request, fetch }) => {
		const form = await superValidate(request, zod4(subscriptionSchema));
		if (!form.valid) return fail(400, { form });

		const id = form.data.id;
		if (!id) return fail(400, { form: { ...form, message: 'Missing subscription id for update.' } });

		const categoryId = !form.data.categoryId ? null : form.data.categoryId;

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/subscriptions/${id}`, {
				method: 'PUT',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({
					name: form.data.name,
					cost: form.data.cost,
					billingCycle: form.data.billingCycle,
					type: form.data.type,
					categoryId,
					nextBillingDate: form.data.nextBillingDate,
					ownerParticipates: form.data.ownerParticipates ?? true,
				}),
			});
		} catch {
			console.error('[subscriptions] update: backend unreachable');
			return fail(502, { form: { ...form, message: 'Could not reach backend service.' } });
		}

		if (res.status === 404)
			return fail(404, { form: { ...form, message: 'Subscription not found.' } });
		if (res.status === 400) {
			console.error('[subscriptions] update: validation error from backend');
			return fail(400, { form: { ...form, message: 'Invalid subscription data.' } });
		}
		if (!res.ok) {
			console.error(`[subscriptions] update: backend error ${res.status}`);
			return fail(502, { form: { ...form, message: 'Unexpected error updating subscription.' } });
		}

		console.log(`[subscriptions] update: success — id: ${id} name: ${form.data.name}`);
		return { form };
	},

	delete: async ({ request, fetch }) => {
		const data = await request.formData();
		const id = data.get('id');

		if (!id) return fail(400, { message: 'Missing subscription id.' });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/subscriptions/${id}`, { method: 'DELETE' });
		} catch {
			console.error('[subscriptions] delete: backend unreachable');
			return fail(502, { message: 'Could not reach backend service.' });
		}

		if (res.status === 404) return fail(404, { message: 'Subscription not found.' });
		if (!res.ok) {
			console.error(`[subscriptions] delete: backend error ${res.status}`);
			return fail(502, { message: 'Unexpected error deleting subscription.' });
		}

		console.log(`[subscriptions] delete: success — id: ${id}`);
		return {};
	},

	addMember: async ({ request, fetch }) => {
		const data = await request.formData();
		const subscriptionId = data.get('subscriptionId');
		const contactId = data.get('contactId');

		if (!subscriptionId || !contactId)
			return fail(400, { message: 'Missing subscriptionId or contactId.' });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/subscriptions/${subscriptionId}/members`, {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ contactId: Number(contactId) }),
			});
		} catch {
			console.error('[subscriptions] addMember: backend unreachable');
			return fail(502, { message: 'Could not reach backend service.' });
		}

		if (res.status === 404) return fail(404, { message: 'Subscription or contact not found.' });
		if (res.status === 409) return fail(409, { message: 'Contact is already a member.' });
		if (!res.ok) {
			console.error(`[subscriptions] addMember: backend error ${res.status}`);
			return fail(502, { message: 'Unexpected error adding member.' });
		}

		console.log(
			`[subscriptions] addMember: success — subscriptionId: ${subscriptionId} contactId: ${contactId}`,
		);
		return {};
	},

	removeMember: async ({ request, fetch }) => {
		const data = await request.formData();
		const subscriptionId = data.get('subscriptionId');
		const memberId = data.get('memberId');

		if (!subscriptionId || !memberId)
			return fail(400, { message: 'Missing subscriptionId or memberId.' });

		let res: Response;
		try {
			res = await fetch(
				`${BACKEND}/api/subscriptions/${subscriptionId}/members/${memberId}`,
				{ method: 'DELETE' },
			);
		} catch {
			console.error('[subscriptions] removeMember: backend unreachable');
			return fail(502, { message: 'Could not reach backend service.' });
		}

		if (res.status === 404) return fail(404, { message: 'Member not found.' });
		if (!res.ok) {
			console.error(`[subscriptions] removeMember: backend error ${res.status}`);
			return fail(502, { message: 'Unexpected error removing member.' });
		}

		console.log(
			`[subscriptions] removeMember: success — subscriptionId: ${subscriptionId} memberId: ${memberId}`,
		);
		return {};
	},

	generateBilling: async ({ fetch }) => {
		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/subscriptions/generate-billing`, {
				method: 'POST',
			});
		} catch {
			console.error('[subscriptions] generateBilling: backend unreachable');
			return fail(502, { message: 'Could not reach backend service.' });
		}

		if (!res.ok) {
			console.error(`[subscriptions] generateBilling: backend error ${res.status}`);
			return fail(502, { message: 'Unexpected error generating billing.' });
		}

		const result = await res.json();
		const count: number = result.generated ?? 0;
		console.log(`[billing.generate] generated=${count}`);
		return { generated: count };
	},
};
