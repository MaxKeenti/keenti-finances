import { getSession } from '$lib/server/workos-session';
import type { PageServerLoad } from './$types';
import { formatDateOnly, mxnFormatter } from '$lib/formatting';
import { m } from '$lib/paraglide/messages.js';

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

type MonthSummary = {
	month: number;
	ingress: number;
	egress: number;
};

type DashboardSummary = {
	year: number;
	netBalance: number;
	inBoxes: number;
	availableToSpend: number;
	totalIngress: number;
	totalEgress: number;
	monthly: MonthSummary[];
};

const EMPTY_SUMMARY: DashboardSummary = {
	year: new Date().getFullYear(),
	netBalance: 0,
	inBoxes: 0,
	availableToSpend: 0,
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

	// The warnings below are built here rather than in the page, so they need
	// the User's locale to format their money and dates. The layout keeps this
	// cookie in step with the stored preference.
	const locale = cookies.get('PARAGLIDE_LOCALE') === 'en' ? 'en' : 'es';
	const mxn = mxnFormatter(locale);

	let summary: DashboardSummary = { ...EMPTY_SUMMARY, year };
	let accountWarnings: Array<{ title: string; description: string; href: string }> = [];

	try {
		const res = await fetch(`${BACKEND}/api/dashboard/summary?year=${year}`, {
			headers: authHeaders,
		});
		if (res.ok) {
			summary = { ...EMPTY_SUMMARY, ...((await res.json()) as Partial<DashboardSummary>), year };
			console.log(
				`[dashboard] load: year=${year} months=${summary.monthly.length} netBalance=${summary.netBalance}`,
			);
		} else {
			console.error(`[dashboard] load: backend returned ${res.status} for year=${year}`);
		}
	} catch {
		console.error('[dashboard] load: backend unreachable');
	}

	try {
		const accountsRes = await fetch(`${BACKEND}/api/accounts`, { headers: authHeaders });
		const accounts = accountsRes.ok ? await accountsRes.json() as Array<{ id: number; name: string; kind: string; balance: number }> : [];
		accountWarnings = accounts
			.filter((account) => account.kind !== 'CREDIT' && account.balance < 0)
			.map((account) => ({
				title: m.warning_account_overdrawn_title({ name: account.name }),
				description: m.warning_account_overdrawn_description({ amount: mxn.format(account.balance) }),
				href: `/accounts/${account.id}`,
			}));
		const creditWarnings = await Promise.all(accounts.filter((account) => account.kind === 'CREDIT').map(async (account) => {
			const [settingsRes, statementsRes] = await Promise.all([
				fetch(`${BACKEND}/api/accounts/${account.id}/credit-settings`, { headers: authHeaders }),
				fetch(`${BACKEND}/api/accounts/${account.id}/credit-statements`, { headers: authHeaders }),
			]);
			const warnings: Array<{ title: string; description: string; href: string }> = [];
			const settings = settingsRes.ok ? await settingsRes.json() as { creditLimit: number } : null;
			if (settings && account.balance < -settings.creditLimit)
				warnings.push({
					title: m.warning_credit_limit_title({ name: account.name }),
					description: m.warning_credit_limit_description(),
					href: `/accounts/${account.id}`,
				});
			const statements = statementsRes.ok ? await statementsRes.json() as Array<{ dueDate: string; outstandingBalance: number }> : [];
			const next = statements.filter((statement) => statement.outstandingBalance > 0).sort((a, b) => a.dueDate.localeCompare(b.dueDate))[0];
			if (next)
				warnings.push({
					title: m.warning_payment_due_title({ name: account.name, date: formatDateOnly(next.dueDate, locale) }),
					description: m.warning_payment_due_description({ amount: mxn.format(next.outstandingBalance) }),
					href: `/accounts/${account.id}`,
				});
			return warnings;
		}));
		accountWarnings.push(...creditWarnings.flat());
	} catch {
		// The dashboard remains useful when account-specific data is temporarily unavailable.
	}

	return { summary, year, accountWarnings };
};
