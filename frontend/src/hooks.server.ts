import type { Handle, HandleFetch } from '@sveltejs/kit';
import { redirect } from '@sveltejs/kit';
import { paraglideMiddleware } from '$lib/paraglide/server';
import { getTextDirection } from '$lib/paraglide/runtime';
import { getWorkOS, getAuthorizationUrl } from '$lib/server/workos';
import {
	getSession,
	setSession,
	clearSession,
	type WorkOSSession,
} from '$lib/server/workos-session';

const PUBLIC_PATHS = ['/callback', '/logout', '/public', '/health'];

function isTokenExpired(accessToken: string): boolean {
	try {
		const [, payload] = accessToken.split('.');
		const decoded = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
		// Refresh 60 seconds before actual expiry to avoid clock skew
		return typeof decoded.exp === 'number' && decoded.exp < Math.floor(Date.now() / 1000) + 60;
	} catch {
		return true;
	}
}

async function refreshSession(session: WorkOSSession): Promise<WorkOSSession | null> {
	try {
		const clientId = process.env.WORKOS_CLIENT_ID;
		if (!clientId) return null;
		const result = await getWorkOS().userManagement.authenticateWithRefreshToken({
			clientId,
			refreshToken: session.refreshToken,
		});
		return {
			accessToken: result.accessToken,
			refreshToken: result.refreshToken,
			user: {
				id: result.user.id,
				email: result.user.email,
				firstName: result.user.firstName,
				lastName: result.user.lastName,
			},
		};
	} catch (err) {
		console.error('[workos-auth] session-refresh failed', err instanceof Error ? err.message : err);
		return null;
	}
}

const authHandle: Handle = async ({ event, resolve }) => {
	const path = event.url.pathname;
	const isPublic =
		PUBLIC_PATHS.some((p) => path === p || path.startsWith(p + '/')) ||
		path.startsWith('/_app/') ||
		path.startsWith('/static/');

	if (isPublic) {
		event.locals.session = null;
		return resolve(event);
	}

	let session = getSession(event.cookies);

	if (session && isTokenExpired(session.accessToken)) {
		const refreshed = await refreshSession(session);
		if (refreshed) {
			setSession(event.cookies, refreshed);
			console.log(`[workos-auth] session-refresh — user: ${refreshed.user.email}`);
			session = refreshed;
		} else {
			clearSession(event.cookies);
			session = null;
		}
	}

	if (session) {
		event.locals.session = { user: session.user };
		return resolve(event);
	}

	const redirectUri = `${event.url.origin}/callback`;
	const authUrl = getAuthorizationUrl(redirectUri);
	console.log(`[workos-auth] redirect — unauthenticated request to ${path}`);
	redirect(303, authUrl);
};

export const handle: Handle = ({ event, resolve }) =>
	paraglideMiddleware(event.request, ({ request: localizedRequest, locale }) => {
		event.request = localizedRequest;
		return authHandle({
			event,
			resolve: (event, options) =>
				resolve(event, {
					...options,
					transformPageChunk: ({ html, done }) => {
						const transformed = html
							.replace('%lang%', locale)
							.replace('%dir%', getTextDirection(locale));
						return options?.transformPageChunk?.({ html: transformed, done }) ?? transformed;
					},
				}),
		});
	});

const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080';

export const handleFetch: HandleFetch = async ({ event, request, fetch }) => {
	if (request.url.startsWith(BACKEND_URL) && event.locals.session) {
		const headers = new Headers(request.headers);
		headers.set('X-WorkOS-User-Id', event.locals.session.user.id);
		return fetch(new Request(request, { headers }));
	}
	return fetch(request);
};
