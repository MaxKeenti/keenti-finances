import { fail } from '@sveltejs/kit';
import { superValidate } from 'sveltekit-superforms';
import { zod4 } from 'sveltekit-superforms/adapters';
import { z } from 'zod';
import { getSession } from '$lib/server/workos-session';
import type { Actions, PageServerLoad } from './$types';

const DIRECTION_DEFAULT_HUE = { INGRESS: 100, EGRESS: 10, BOTH: 220 } as const;

const categorySchema = z.object({
	id: z.coerce.number().optional(),
	name: z.string().min(1, 'Name is required'),
	type: z.enum(['INGRESS', 'EGRESS', 'BOTH']),
	hue: z.coerce.number().int().min(0).max(359),
});

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

export const load: PageServerLoad = async ({ fetch, cookies }) => {
	const session = getSession(cookies);
	const accessToken = session?.accessToken;
	const authHeaders: Record<string, string> = accessToken
		? { Authorization: `Bearer ${accessToken}` }
		: {};

	let categories: Array<{ id: number; name: string; type: string; hue: number }> = [];
	try {
		const res = await fetch(`${BACKEND}/api/categories`, { headers: authHeaders });
		if (res.ok) {
			categories = await res.json();
		} else {
			console.error(`[categories] load: backend returned ${res.status}`);
		}
	} catch {
		console.error('[categories] load: backend unreachable');
	}

	const form = await superValidate(
		{ name: '', type: 'INGRESS' as const, hue: DIRECTION_DEFAULT_HUE.INGRESS },
		zod4(categorySchema),
	);
	return { categories, form };
};

export const actions: Actions = {
	create: async ({ request, fetch, cookies }) => {
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		const form = await superValidate(request, zod4(categorySchema));
		if (!form.valid) return fail(400, { form });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/categories`, {
				method: 'POST',
				headers: { 'content-type': 'application/json', ...authHeaders },
				body: JSON.stringify({ name: form.data.name, type: form.data.type, hue: form.data.hue }),
			});
		} catch {
			console.error('[categories] create: backend unreachable');
			return fail(502, { form: { ...form, message: 'Could not reach backend service.' } });
		}

		if (res.status === 409) {
			console.log(`[categories] create: conflict — name: ${form.data.name}`);
			return fail(409, { form: { ...form, message: 'A category with that name already exists.' } });
		}
		if (!res.ok) {
			console.error(`[categories] create: backend error ${res.status}`);
			return fail(502, { form: { ...form, message: 'Unexpected error creating category.' } });
		}

		console.log(`[categories] create: success — name: ${form.data.name} type: ${form.data.type}`);
		return { form };
	},

	update: async ({ request, fetch, cookies }) => {
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		const form = await superValidate(request, zod4(categorySchema));
		if (!form.valid) return fail(400, { form });

		const id = form.data.id;
		if (!id) return fail(400, { form: { ...form, message: 'Missing category id for update.' } });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/categories/${id}`, {
				method: 'PUT',
				headers: { 'content-type': 'application/json', ...authHeaders },
				body: JSON.stringify({ name: form.data.name, type: form.data.type, hue: form.data.hue }),
			});
		} catch {
			console.error('[categories] update: backend unreachable');
			return fail(502, { form: { ...form, message: 'Could not reach backend service.' } });
		}

		if (res.status === 404) {
			return fail(404, { form: { ...form, message: 'Category not found.' } });
		}
		if (res.status === 409) {
			console.log(`[categories] update: conflict — id: ${id} name: ${form.data.name}`);
			return fail(409, { form: { ...form, message: 'A category with that name already exists.' } });
		}
		if (!res.ok) {
			console.error(`[categories] update: backend error ${res.status}`);
			return fail(502, { form: { ...form, message: 'Unexpected error updating category.' } });
		}

		console.log(`[categories] update: success — id: ${id} name: ${form.data.name}`);
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

		if (!id) return fail(400, { message: 'Missing category id.' });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/categories/${id}`, {
				method: 'DELETE',
				headers: authHeaders,
			});
		} catch {
			console.error('[categories] delete: backend unreachable');
			return fail(502, { message: 'Could not reach backend service.' });
		}

		if (res.status === 404) return fail(404, { message: 'Category not found.' });
		if (!res.ok) {
			console.error(`[categories] delete: backend error ${res.status}`);
			return fail(502, { message: 'Unexpected error deleting category.' });
		}

		console.log(`[categories] delete: success — id: ${id}`);
		return {};
	},
};
