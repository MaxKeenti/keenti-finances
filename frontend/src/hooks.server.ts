import type { Handle } from '@sveltejs/kit';
import { redirect } from '@sveltejs/kit';
import { COOKIE_NAME, validateSessionCookieValue } from '$lib/server/session';

const PUBLIC_PATHS = ['/login', '/api/auth/login', '/logout', '/public', '/health'];

export const handle: Handle = async ({ event, resolve }) => {
	const cookieValue = event.cookies.get(COOKIE_NAME);
	const username = cookieValue ? validateSessionCookieValue(cookieValue) : null;
	event.locals.session = username ? { username } : null;

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
