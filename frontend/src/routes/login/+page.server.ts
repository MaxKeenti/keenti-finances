import { fail, redirect } from '@sveltejs/kit';
import { superValidate } from 'sveltekit-superforms';
import { zod4 } from 'sveltekit-superforms/adapters';
import { z } from 'zod';
import { COOKIE_NAME, createSessionCookieValue } from '$lib/server/session';
import type { Actions, PageServerLoad } from './$types';

const loginSchema = z.object({
	username: z.string().min(1, 'Username is required'),
	password: z.string().min(1, 'Password is required'),
});

export const load: PageServerLoad = async ({ locals }) => {
	if (locals.session) {
		redirect(303, '/');
	}
	const form = await superValidate(zod4(loginSchema));
	return { form };
};

export const actions: Actions = {
	default: async ({ request, fetch, cookies }) => {
		const form = await superValidate(request, zod4(loginSchema));
		if (!form.valid) return fail(400, { form });

		let response: Response;
		try {
			const backendUrl = process.env.BACKEND_URL ?? 'http://localhost:8080';
			response = await fetch(`${backendUrl}/api/auth/login`, {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ username: form.data.username, password: form.data.password }),
			});
		} catch {
			return fail(502, {
				form: { ...form, message: 'Could not reach authentication service.' },
			});
		}

		if (response.status === 401) {
			console.log(`[auth] login failed — username: ${form.data.username}`);
			return fail(401, {
				form: { ...form, message: 'Invalid username or password.' },
			});
		}

		if (!response.ok) {
			return fail(502, {
				form: { ...form, message: 'Unexpected error from authentication service.' },
			});
		}

		console.log(`[auth] login success — username: ${form.data.username} timestamp: ${new Date().toISOString()}`);

		const isProd = process.env.NODE_ENV === 'production';
		cookies.set(COOKIE_NAME, createSessionCookieValue(form.data.username), {
			path: '/',
			httpOnly: true,
			sameSite: 'lax',
			secure: isProd,
			maxAge: 60 * 60 * 24 * 7, // 7 days
		});

		redirect(303, '/');
	},
};
