import { redirect } from '@sveltejs/kit';
import { getAuthorizationUrl } from '$lib/server/workos';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals, url }) => {
	if (locals.session) {
		redirect(303, '/');
	}
	const redirectUri = `${url.origin}/callback`;
	const authUrl = getAuthorizationUrl(redirectUri);
	console.log('[workos-auth] redirect — to WorkOS authorization');
	redirect(303, authUrl);
};
