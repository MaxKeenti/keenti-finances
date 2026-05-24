import { getSession } from '$lib/server/workos-session';
import type { PageServerLoad } from './$types';

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

type MonthSummary = {
	month: number;
	ingress: number;
	egress: number;
};

type DashboardSummary = {
	year: number;
	netBalance: number;
	totalIngress: number;
	totalEgress: number;
	monthly: MonthSummary[];
};

const EMPTY_SUMMARY: DashboardSummary = {
	year: new Date().getFullYear(),
	netBalance: 0,
	totalIngress: 0,
	totalEgress: 0,
	monthly: Array.from({ length: 12 }, (_, i) => ({ month: i + 1, ingress: 0, egress: 0 })),
};

export const load: PageServerLoad = async ({ fetch, url, cookies }) => {
	const yearParam = url.searchParams.get('year');
	const year = yearParam ? parseInt(yearParam, 10) : new Date().getFullYear();

	const session = getSession(cookies);
	const accessToken = session?.accessToken;
	const authHeaders: Record<string, string> = accessToken
		? { Authorization: `Bearer ${accessToken}` }
		: {};

	let summary: DashboardSummary = { ...EMPTY_SUMMARY, year };

	try {
		const res = await fetch(`${BACKEND}/api/dashboard/summary?year=${year}`, {
			headers: authHeaders,
		});
		if (res.ok) {
			summary = await res.json();
			console.log(
				`[dashboard] load: year=${year} months=${summary.monthly.length} netBalance=${summary.netBalance}`,
			);
		} else {
			console.error(`[dashboard] load: backend returned ${res.status} for year=${year}`);
		}
	} catch {
		console.error('[dashboard] load: backend unreachable');
	}

	return { summary, year };
};
