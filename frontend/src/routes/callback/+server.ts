import { redirect, error } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getWorkOS } from '$lib/server/workos';
import { setSession } from '$lib/server/workos-session';

export const GET: RequestHandler = async ({ url, cookies }) => {
	const code = url.searchParams.get('code');

	if (!code) {
		console.error('[workos-auth] callback — missing code param');
		error(400, 'Missing authorization code');
	}

	const clientId = process.env.WORKOS_CLIENT_ID;
	if (!clientId) {
		console.error('[workos-auth] callback — WORKOS_CLIENT_ID not set');
		error(500, 'Server configuration error');
	}

	let result;
	try {
		result = await getWorkOS().userManagement.authenticateWithCode({
			clientId,
			code,
		});
	} catch (err) {
		console.error('[workos-auth] callback — code exchange failed', err instanceof Error ? err.message : err);
		redirect(303, '/callback?error=auth_failed');
	}

	const session = {
		accessToken: result.accessToken,
		refreshToken: result.refreshToken,
		user: {
			id: result.user.id,
			email: result.user.email,
			firstName: result.user.firstName,
			lastName: result.user.lastName,
		},
	};

	setSession(cookies, session);
	console.log(`[workos-auth] session-create — user: ${session.user.email}`);

	redirect(303, '/');
};
