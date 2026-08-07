import type { LayoutServerLoad } from './$types';
import { redirect } from '@sveltejs/kit';
import { EMPTY_BALANCE_SUMMARY, type BalanceSummary } from '$lib/types/boxes';

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

const DEFAULT_PREFERENCES = {
	primaryHue: 91,
	headingFont: 'Fraunces',
	bodyFont: 'Geist',
	locale: 'es',
	transactionPageSize: 25,
	transactionSortBy: 'transactionDate',
	transactionSortDirection: 'desc',
	mobilePinnedNavItems: '/transactions,/subscriptions,/debts',
	dockMagnification: true,
	timeZone: 'America/Mexico_City',
} as const;

type Preferences = {
	primaryHue: number;
	headingFont: string;
	bodyFont: string;
	locale: string;
	transactionPageSize: number;
	transactionSortBy: string;
	transactionSortDirection: string;
	mobilePinnedNavItems: string;
	dockMagnification: boolean;
	timeZone: string;
};

export const load: LayoutServerLoad = async ({ locals, fetch, cookies, url }) => {
	const cookieLocale = cookies.get('PARAGLIDE_LOCALE');
	let preferences: Preferences = {
		...DEFAULT_PREFERENCES,
		locale: cookieLocale === 'en' ? 'en' : DEFAULT_PREFERENCES.locale,
	};
	let balanceSummary: BalanceSummary = EMPTY_BALANCE_SUMMARY;

	if (locals.session) {
		const [preferencesResult, balanceResult, accountStatusResult] = await Promise.allSettled([
			fetch(`${BACKEND}/api/user/preferences`),
			fetch(`${BACKEND}/api/boxes/summary`),
			fetch(`${BACKEND}/api/accounts/status`),
		]);

		if (
			accountStatusResult.status === 'fulfilled' &&
			accountStatusResult.value.ok &&
			(await accountStatusResult.value.json() as { setupRequired?: boolean }).setupRequired &&
			url.pathname !== '/accounts' &&
			url.pathname !== '/logout' &&
			!url.pathname.startsWith('/public/')
		) {
			redirect(303, '/accounts');
		}

		if (preferencesResult.status === 'fulfilled' && preferencesResult.value.ok) {
			preferences = (await preferencesResult.value.json()) as Preferences;
			if (preferences.locale === 'en' || preferences.locale === 'es') {
				cookies.set('PARAGLIDE_LOCALE', preferences.locale, {
					path: '/',
					sameSite: 'lax',
					maxAge: 34_560_000,
					httpOnly: false,
				});
			}
		} else {
			console.error('[layout] failed to load user preferences; using defaults');
		}

		if (balanceResult.status === 'fulfilled' && balanceResult.value.ok) {
			balanceSummary = (await balanceResult.value.json()) as BalanceSummary;
		} else {
			console.error('[layout] failed to load balance summary; using zero fallback');
		}
	}

	return { session: locals.session, preferences, balanceSummary };
};
