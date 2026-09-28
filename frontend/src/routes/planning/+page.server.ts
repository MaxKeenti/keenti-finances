import { getSession } from '$lib/server/workos-session';
import { loadPlanningOptions } from '$lib/server/planning-options';
import { planningWindow } from '$lib/planning-preview';
import type { PageServerLoad } from './$types';

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

/**
 * The planning preview's selectable options and its date window.
 *
 * Read-only: every request is a GET, and the preview itself is requested by
 * the page, never here. The window comes from the layout's one captured day in
 * the User's own zone; an unusable zone yields no window rather than UTC.
 */
export const load: PageServerLoad = async ({ fetch, cookies, parent }) => {
	const accessToken = getSession(cookies)?.accessToken;
	const headers: Record<string, string> = accessToken ? { Authorization: `Bearer ${accessToken}` } : {};

	const [{ obligationToday }, options] = await Promise.all([
		parent(),
		loadPlanningOptions(fetch, BACKEND, headers),
	]);

	return { window: planningWindow(obligationToday), options };
};
