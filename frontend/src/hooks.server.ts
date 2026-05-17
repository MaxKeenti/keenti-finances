import type { Handle } from '@sveltejs/kit';
import { redirect } from '@sveltejs/kit';
import { getSession } from '$lib/server/workos-session';

const PUBLIC_PATHS = ['/login', '/api/auth/login', '/api/auth/callback', '/logout', '/public', '/health'];

export const handle: Handle = async ({ event, resolve }) => {
	const session = getSession(event.cookies);
	event.locals.session = session ? { user: session.user } : null;

	const path = event.url.pathname;
	const isPublic =
		PUBLIC_PATHS.some((p) => path === p || path.startsWith(p + '/')) ||
		path.startsWith('/_app/') ||
		path.startsWith('/static/');

	if (!event.locals.session && !isPublic) {
		console.log(`[auth] redirect to /login — unauthenticated request to ${path}`);
		redirect(303, '/login');
	}

	return resolve(event);
};
