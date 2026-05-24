import { fail } from '@sveltejs/kit';
import { superValidate } from 'sveltekit-superforms';
import { zod4 } from 'sveltekit-superforms/adapters';
import { z } from 'zod';
import { getSession } from '$lib/server/workos-session';
import type { Actions, PageServerLoad } from './$types';

const contactSchema = z.object({
	id: z.coerce.number().optional(),
	name: z.string().min(1, 'Name is required'),
	phone: z.string().optional(),
	email: z.string().email('Invalid email format').optional().or(z.literal('')),
});

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

export const load: PageServerLoad = async ({ fetch, cookies }) => {
	const session = getSession(cookies);
	const accessToken = session?.accessToken;
	const authHeaders: Record<string, string> = accessToken
		? { Authorization: `Bearer ${accessToken}` }
		: {};

	let contacts: Array<{ id: number; name: string; phone: string | null; email: string | null }> =
		[];
	try {
		const res = await fetch(`${BACKEND}/api/contacts`, { headers: authHeaders });
		if (res.ok) {
			contacts = await res.json();
		} else {
			console.error(`[contacts] load: backend returned ${res.status}`);
		}
	} catch {
		console.error('[contacts] load: backend unreachable');
	}

	const form = await superValidate({ name: '', phone: '', email: '' }, zod4(contactSchema));
	return { contacts, form };
};

export const actions: Actions = {
	create: async ({ request, fetch, cookies }) => {
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		const form = await superValidate(request, zod4(contactSchema));
		if (!form.valid) return fail(400, { form });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/contacts`, {
				method: 'POST',
				headers: { 'content-type': 'application/json', ...authHeaders },
				body: JSON.stringify({
					name: form.data.name,
					phone: form.data.phone || null,
					email: form.data.email || null,
				}),
			});
		} catch {
			console.error('[contacts] create: backend unreachable');
			return fail(502, { form: { ...form, message: 'Could not reach backend service.' } });
		}

		if (!res.ok) {
			console.error(`[contacts] create: backend error ${res.status}`);
			return fail(502, { form: { ...form, message: 'Unexpected error creating contact.' } });
		}

		console.log(`[contacts] create: success — name: ${form.data.name}`);
		return { form };
	},

	update: async ({ request, fetch, cookies }) => {
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		const form = await superValidate(request, zod4(contactSchema));
		if (!form.valid) return fail(400, { form });

		const id = form.data.id;
		if (!id) return fail(400, { form: { ...form, message: 'Missing contact id for update.' } });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/contacts/${id}`, {
				method: 'PUT',
				headers: { 'content-type': 'application/json', ...authHeaders },
				body: JSON.stringify({
					name: form.data.name,
					phone: form.data.phone || null,
					email: form.data.email || null,
				}),
			});
		} catch {
			console.error('[contacts] update: backend unreachable');
			return fail(502, { form: { ...form, message: 'Could not reach backend service.' } });
		}

		if (res.status === 404) {
			return fail(404, { form: { ...form, message: 'Contact not found.' } });
		}
		if (!res.ok) {
			console.error(`[contacts] update: backend error ${res.status}`);
			return fail(502, { form: { ...form, message: 'Unexpected error updating contact.' } });
		}

		console.log(`[contacts] update: success — id: ${id} name: ${form.data.name}`);
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

		if (!id) return fail(400, { message: 'Missing contact id.' });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/contacts/${id}`, {
				method: 'DELETE',
				headers: authHeaders,
			});
		} catch {
			console.error('[contacts] delete: backend unreachable');
			return fail(502, { message: 'Could not reach backend service.' });
		}

		if (res.status === 404) return fail(404, { message: 'Contact not found.' });
		if (!res.ok) {
			console.error(`[contacts] delete: backend error ${res.status}`);
			return fail(502, { message: 'Unexpected error deleting contact.' });
		}

		console.log(`[contacts] delete: success — id: ${id}`);
		return {};
	},
};
