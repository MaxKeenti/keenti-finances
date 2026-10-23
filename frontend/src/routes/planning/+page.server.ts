import { getSession } from '$lib/server/workos-session';
import { loadPlanningOptions } from '$lib/server/planning-options';
import { loadSection } from '$lib/server/section-load';
import { parseBalanceSummaryWithCurrencies } from '$lib/server/payloads';
import { isSupportedCurrency } from '$lib/formatting';
import { planningWindow } from '$lib/planning-preview';
import type { PageServerLoad } from './$types';

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

/**
 * The planning preview's selectable options and its date window.
 *
 * Read-only: every request is a GET, and the preview itself is requested by
 * the page, never here. The window comes from the layout's one captured day in
 * the User's own zone; an unusable zone yields no window rather than UTC.
 *
 * The preview runs in one Viewing Currency (ADR-0024): `?currency=` or the
 * Primary Currency. The Boxes summary names it and lists the others the User
 * holds, for the switcher.
 */
export const load: PageServerLoad = async ({ fetch, cookies, parent, url }) => {
	const accessToken = getSession(cookies)?.accessToken;
	const headers: Record<string, string> = accessToken ? { Authorization: `Bearer ${accessToken}` } : {};

	const { obligationToday, preferences } = await parent();
	const requested = url.searchParams.get('currency');
	const currency = requested !== null && isSupportedCurrency(requested) ? requested : preferences.primaryCurrency;

	const [options, summary] = await Promise.all([
		loadPlanningOptions(fetch, BACKEND, headers, currency),
		loadSection(fetch, `${BACKEND}/api/boxes/summary?currency=${encodeURIComponent(currency)}`, {
			parse: parseBalanceSummaryWithCurrencies,
			headers,
			label: 'planning/summary',
		}),
	]);

	return {
		window: planningWindow(obligationToday),
		options,
		currency,
		currencies: summary.status === 'ok' ? summary.data.currencies : [currency],
	};
};
