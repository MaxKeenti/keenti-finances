/**
 * The dashboard loader (Phase 4).
 *
 * One request: `GET /api/dashboard/overview`, the composed read model that
 * answers with five independently available sections. The page used to build
 * its attention list itself — a Financial Account list, then a credit-settings
 * and a confirmed-statement request per Credit Financial Account — which grew
 * with the User's cards and still could not see Debts, Payment Records or Box
 * Plans without growing further. Composing server-side keeps the page's cost
 * flat and keeps every figure attributable to the service that owns it.
 *
 * What this loader does not do: fetch any per-item history. No Transaction
 * list, no Box movement log, no per-Subscription payment history is requested
 * here. It also issues no writes — the overview endpoint is read-only by
 * contract, so opening the dashboard never generates billing, never advances a
 * generation cursor, and never moves money.
 */

import { getSession } from '$lib/server/workos-session';
import type { PageServerLoad } from './$types';
import { loadSection } from '$lib/server/section-load';
import { parseDashboardOverview, type DashboardOverview } from '$lib/server/payloads';
import type { Section } from '$lib/types/section';

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

/**
 * The years the overview endpoint accepts. A request outside them is answered
 * with a 400, which would take all five sections down over a query string.
 */
const MIN_YEAR = 1900;
const MAX_YEAR = 9999;

export const load: PageServerLoad = async ({ fetch, url, cookies }) => {
	const yearParam = url.searchParams.get('year');
	const parsedYear = yearParam !== null && /^\d{4}$/.test(yearParam) ? Number(yearParam) : NaN;
	// An unusable `?year=` is not a reason to refuse the page: the history
	// section is the only thing the year scopes, and the current position is
	// all-time either way. `current` asks the backend to resolve the year in the
	// User's own time zone — the server's calendar year is the wrong one for
	// several hours around every New Year, and this loader has no zone of its
	// own to resolve it with.
	const requestedYear =
		Number.isInteger(parsedYear) && parsedYear >= MIN_YEAR && parsedYear <= MAX_YEAR
			? parsedYear
			: null;

	const session = getSession(cookies);
	const accessToken = session?.accessToken;
	const authHeaders: Record<string, string> = accessToken
		? { Authorization: `Bearer ${accessToken}` }
		: {};

	const overview: Section<DashboardOverview> = await loadSection<DashboardOverview>(
		fetch,
		`${BACKEND}/api/dashboard/overview?year=${requestedYear ?? 'current'}`,
		{
			parse: parseDashboardOverview,
			headers: authHeaders,
			label: 'dashboard/overview',
		},
	);

	// Which year the page is showing comes from the response that scoped it: the
	// backend resolved `current` against the User's zone, and its history
	// section reports the year it answered for. Only if nothing usable came back
	// does this fall through to the server's own calendar year, which is a label
	// for the year control and never a figure.
	const year =
		requestedYear ??
		(overview.status === 'ok' && overview.data.history.status === 'ok'
			? overview.data.history.data.year
			: null) ??
		(overview.status === 'ok' && overview.data.today !== null
			? Number.parseInt(overview.data.today.slice(0, 4), 10)
			: null) ??
		new Date().getFullYear();

	if (overview.status === 'ok' && overview.data.position.status === 'ok') {
		console.log(
			`[dashboard] overview: year=${year} netBalance=${overview.data.position.data.netBalance}`,
		);
	}

	return { overview, year };
};
