import { getSession } from '$lib/server/workos-session';
import type { PageServerLoad } from './$types';
import { formatDateOnly, mxnFormatter } from '$lib/formatting';
import { m } from '$lib/paraglide/messages.js';
import { loadOptionalSection, loadSection } from '$lib/server/section-load';
import {
	parseAccounts,
	parseCreditSettings,
	parseCreditStatements,
	parseDashboardSummary,
	type AccountSummary,
	type DashboardSummary,
} from '$lib/server/payloads';
import { sectionOk, sectionUnavailable, type Section } from '$lib/types/section';

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

export type AccountWarning = { title: string; description: string; href: string };

/**
 * Account warnings, plus whether some account could not be fully checked.
 * `partial` is not "no warnings": it means the page cannot promise it has
 * listed every one.
 */
export type AccountWarnings = { items: AccountWarning[]; partial: boolean };

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

	// The two sections are requested concurrently and resolved independently:
	// a failed summary must not blank the warnings, and vice versa.
	const [summary, accountWarnings] = await Promise.all([
		loadSection<DashboardSummary>(fetch, `${BACKEND}/api/dashboard/summary?year=${year}`, {
			parse: parseDashboardSummary,
			headers: authHeaders,
			label: 'dashboard/summary',
		}),
		loadAccountWarnings(fetch, authHeaders, locale, mxn),
	]);

	if (summary.status === 'ok') {
		console.log(
			`[dashboard] load: year=${year} months=${summary.data.monthly.length} netBalance=${summary.data.netBalance}`,
		);
	}

	return { summary, year, accountWarnings };
};

async function loadAccountWarnings(
	fetch: typeof globalThis.fetch,
	authHeaders: Record<string, string>,
	locale: string,
	mxn: Intl.NumberFormat,
): Promise<Section<AccountWarnings>> {
	const accounts = await loadSection<AccountSummary[]>(fetch, `${BACKEND}/api/accounts`, {
		parse: parseAccounts,
		headers: authHeaders,
		label: 'dashboard/accounts',
	});
	if (accounts.status !== 'ok') return sectionUnavailable(accounts.reason);

	const items: AccountWarning[] = accounts.data
		.filter((account) => account.kind !== 'CREDIT' && account.balance < 0)
		.map((account) => ({
			title: m.warning_account_overdrawn_title({ name: account.name }),
			description: m.warning_account_overdrawn_description({ amount: mxn.format(account.balance) }),
			href: `/accounts/${account.id}`,
		}));

	let partial = false;
	const creditWarnings = await Promise.all(
		accounts.data
			.filter((account) => account.kind === 'CREDIT')
			.map(async (account) => {
				const [settings, statements] = await Promise.all([
					// A Credit Financial Account with no Credit settings saved yet
					// answers 404. That is the normal not-configured state, not a
					// service failure: it means there is no credit limit to breach,
					// so the page must not claim its warnings may be incomplete.
					loadOptionalSection(fetch, `${BACKEND}/api/accounts/${account.id}/credit-settings`, {
						parse: parseCreditSettings,
						headers: authHeaders,
						label: `dashboard/credit-settings/${account.id}`,
						absentStatuses: [404],
					}),
					loadSection(fetch, `${BACKEND}/api/accounts/${account.id}/credit-statements`, {
						parse: parseCreditStatements,
						headers: authHeaders,
						label: `dashboard/credit-statements/${account.id}`,
					}),
				]);

				// One credit card that cannot be checked leaves the other warnings
				// intact; the page says the list may be incomplete instead of
				// implying this card is fine.
				if (settings.status !== 'ok' || statements.status !== 'ok') partial = true;

				const warnings: AccountWarning[] = [];
				if (
					settings.status === 'ok' &&
					settings.data !== null &&
					account.balance < -settings.data.creditLimit
				)
					warnings.push({
						title: m.warning_credit_limit_title({ name: account.name }),
						description: m.warning_credit_limit_description(),
						href: `/accounts/${account.id}`,
					});

				const next =
					statements.status === 'ok'
						? statements.data
								.filter((statement) => statement.outstandingBalance > 0)
								.sort((a, b) => a.dueDate.localeCompare(b.dueDate))[0]
						: undefined;
				if (next)
					warnings.push({
						title: m.warning_payment_due_title({
							name: account.name,
							date: formatDateOnly(next.dueDate, locale),
						}),
						description: m.warning_payment_due_description({
							amount: mxn.format(next.outstandingBalance),
						}),
						href: `/accounts/${account.id}`,
					});
				return warnings;
			}),
	);

	items.push(...creditWarnings.flat());
	return sectionOk({ items, partial });
}
