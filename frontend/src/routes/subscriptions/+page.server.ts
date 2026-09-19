import { fail } from '@sveltejs/kit';
import { superValidate } from 'sveltekit-superforms';
import { zod4 } from 'sveltekit-superforms/adapters';
import { z } from 'zod';
import { getSession } from '$lib/server/workos-session';
import { m } from '$lib/paraglide/messages.js';
import type { Actions, PageServerLoad } from './$types';
import { dateInTimeZone } from '$lib/formatting';
import { loadSection } from '$lib/server/section-load';
import {
	parseCategories,
	parseContacts,
	parseMembers,
	parseSubscriptions,
	type CategorySummary,
	type ContactSummary,
	type MemberResponse,
	type Subscription,
} from '$lib/server/payloads';
import { sectionOk, sectionUnavailable, type Section } from '$lib/types/section';

const subscriptionSchema = z.object({
	id: z.coerce.number().optional(),
	name: z.string().min(1, m.validation_name_required()),
	cost: z.coerce.number().positive(m.validation_cost_positive()),
	billingCycle: z.enum(['MONTHLY', 'YEARLY']),
	type: z.enum(['PERSONAL', 'SHARED']),
	categoryId: z.union([z.coerce.number(), z.literal('')]).optional(),
	nextBillingDate: z.string().min(1, m.validation_next_billing_required()),
	ownerParticipates: z.boolean().optional(),
});

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

/**
 * One Subscription on the list, with its member list resolved separately.
 *
 * `members` is a section rather than an array: before Slice 3B a failed member
 * request became `[]`, which the card rendered as "Members: 0" and the member
 * dialog as "no members yet" — a claim about the User's Subscription that the
 * failed request never supported. `PERSONAL` Subscriptions have no member list
 * to read at all, which is why it is optional.
 */
export type SubscriptionListItem = Subscription & {
	members?: Section<MemberResponse[]>;
};

export const load: PageServerLoad = async ({ fetch, cookies, parent }) => {
	const session = getSession(cookies);
	const accessToken = session?.accessToken;
	const authHeaders: Record<string, string> = accessToken
		? { Authorization: `Bearer ${accessToken}` }
		: {};

	// Each list reports its own availability. A failed Subscriptions request
	// used to fall through to `[]`, which the page then totalled into a
	// confident $0.00 monthly commitment.
	const [subscriptionsSection, categories, contacts] = await Promise.all([
		loadSection<Subscription[]>(fetch, `${BACKEND}/api/subscriptions`, {
			parse: parseSubscriptions,
			headers: authHeaders,
			label: 'subscriptions',
		}),
		loadSection<CategorySummary[]>(fetch, `${BACKEND}/api/categories`, {
			parse: parseCategories,
			headers: authHeaders,
			label: 'subscriptions/categories',
		}),
		loadSection<ContactSummary[]>(fetch, `${BACKEND}/api/contacts`, {
			parse: parseContacts,
			headers: authHeaders,
			label: 'subscriptions/contacts',
		}),
	]);

	let subscriptions: Section<SubscriptionListItem[]>;
	if (subscriptionsSection.status !== 'ok') {
		subscriptions = sectionUnavailable(subscriptionsSection.reason);
	} else {
		const items: SubscriptionListItem[] = subscriptionsSection.data.map((subscription) => ({
			...subscription,
		}));
		const shared = items.filter((item) => item.type === 'SHARED');
		const memberSections = await Promise.all(
			shared.map((item) =>
				loadSection<MemberResponse[]>(
					fetch,
					`${BACKEND}/api/subscriptions/${item.id}/members`,
					{
						parse: parseMembers,
						headers: authHeaders,
						label: `subscriptions/${item.id}/members`,
					},
				),
			),
		);
		shared.forEach((item, index) => {
			item.members = memberSections[index];
		});
		subscriptions = sectionOk(items);
	}

	// `toISOString()` is the UTC date, which is already tomorrow for a
	// User at UTC-6 after 18:00 local. Resolve their calendar day instead.
	const { preferences } = await parent();
	const today = dateInTimeZone(preferences.timeZone);
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
	create: async ({ request, fetch, cookies }) => {
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		const form = await superValidate(request, zod4(subscriptionSchema));
		if (!form.valid) return fail(400, { form });

		const categoryId = !form.data.categoryId ? null : form.data.categoryId;

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/subscriptions`, {
				method: 'POST',
				headers: { 'content-type': 'application/json', ...authHeaders },
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
			return fail(502, { form: { ...form, message: m.error_backend_unreachable() } });
		}

		if (res.status === 400) {
			console.error('[subscriptions] create: validation error from backend');
			return fail(400, { form: { ...form, message: m.error_invalid_subscription() } });
		}
		if (!res.ok) {
			console.error(`[subscriptions] create: backend error ${res.status}`);
			return fail(502, { form: { ...form, message: m.error_unexpected_create_subscription() } });
		}

		console.log(
			`[subscriptions] create: success — name: ${form.data.name} type: ${form.data.type}`,
		);
		return { form };
	},

	update: async ({ request, fetch, cookies }) => {
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		const form = await superValidate(request, zod4(subscriptionSchema));
		if (!form.valid) return fail(400, { form });

		const id = form.data.id;
		if (!id) return fail(400, { form: { ...form, message: m.error_missing_subscription_id_update() } });

		const categoryId = !form.data.categoryId ? null : form.data.categoryId;

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/subscriptions/${id}`, {
				method: 'PUT',
				headers: { 'content-type': 'application/json', ...authHeaders },
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
			return fail(502, { form: { ...form, message: m.error_backend_unreachable() } });
		}

		if (res.status === 404)
			return fail(404, { form: { ...form, message: m.error_subscription_not_found() } });
		if (res.status === 400) {
			console.error('[subscriptions] update: validation error from backend');
			return fail(400, { form: { ...form, message: m.error_invalid_subscription() } });
		}
		if (!res.ok) {
			console.error(`[subscriptions] update: backend error ${res.status}`);
			return fail(502, { form: { ...form, message: m.error_unexpected_update_subscription() } });
		}

		console.log(`[subscriptions] update: success — id: ${id} name: ${form.data.name}`);
		return { form };
	},

	delete: async ({ request, fetch, cookies }) => {
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		const data = await request.formData();
		const id = data.get('id');

		if (!id) return fail(400, { message: m.error_missing_subscription_id() });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/subscriptions/${id}`, {
				method: 'DELETE',
				headers: authHeaders,
			});
		} catch {
			console.error('[subscriptions] delete: backend unreachable');
			return fail(502, { message: m.error_backend_unreachable() });
		}

		if (res.status === 404) return fail(404, { message: m.error_subscription_not_found() });
		if (!res.ok) {
			console.error(`[subscriptions] delete: backend error ${res.status}`);
			return fail(502, { message: m.error_unexpected_delete_subscription() });
		}

		console.log(`[subscriptions] delete: success — id: ${id}`);
		return {};
	},

	addMember: async ({ request, fetch, cookies }) => {
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		const data = await request.formData();
		const subscriptionId = data.get('subscriptionId');
		const contactId = data.get('contactId');

		if (!subscriptionId || !contactId)
			return fail(400, { message: m.error_missing_subscription_id() });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/subscriptions/${subscriptionId}/members`, {
				method: 'POST',
				headers: { 'content-type': 'application/json', ...authHeaders },
				body: JSON.stringify({ contactId: Number(contactId) }),
			});
		} catch {
			console.error('[subscriptions] addMember: backend unreachable');
			return fail(502, { message: m.error_backend_unreachable() });
		}

		if (res.status === 404) return fail(404, { message: m.error_subscription_not_found() });
		if (res.status === 409) return fail(409, { message: m.error_contact_already_member() });
		if (!res.ok) {
			console.error(`[subscriptions] addMember: backend error ${res.status}`);
			return fail(502, { message: m.subscriptions_member_add_failed() });
		}

		console.log(
			`[subscriptions] addMember: success — subscriptionId: ${subscriptionId} contactId: ${contactId}`,
		);
		return {};
	},

	removeMember: async ({ request, fetch, cookies }) => {
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		const data = await request.formData();
		const subscriptionId = data.get('subscriptionId');
		const memberId = data.get('memberId');

		if (!subscriptionId || !memberId)
			return fail(400, { message: m.error_missing_subscription_id() });

		let res: Response;
		try {
			res = await fetch(
				`${BACKEND}/api/subscriptions/${subscriptionId}/members/${memberId}`,
				{ method: 'DELETE', headers: authHeaders },
			);
		} catch {
			console.error('[subscriptions] removeMember: backend unreachable');
			return fail(502, { message: m.error_backend_unreachable() });
		}

		if (res.status === 404) return fail(404, { message: m.error_member_not_found() });
		if (!res.ok) {
			console.error(`[subscriptions] removeMember: backend error ${res.status}`);
			return fail(502, { message: m.subscriptions_member_remove_failed() });
		}

		console.log(
			`[subscriptions] removeMember: success — subscriptionId: ${subscriptionId} memberId: ${memberId}`,
		);
		return {};
	},
};
