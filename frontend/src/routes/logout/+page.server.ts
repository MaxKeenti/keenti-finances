import type { PageServerLoad } from './$types';
import { redirect } from '@sveltejs/kit';
import { clearSession } from '$lib/server/workos-session';

export const load: PageServerLoad = async ({ cookies }) => {
	clearSession(cookies);
	console.log('[workos-auth] logout');
	redirect(303, '/login');
};
