import type { LayoutServerLoad } from './$types';
import { redirect } from '@sveltejs/kit';
import { EMPTY_BALANCE_SUMMARY, type BalanceSummary } from '$lib/types/boxes';
// `import type`, not an inline type specifier: under verbatimModuleSyntax the
// latter would still emit a runtime import and drag the rune module into the
// server bundle. This keeps the ThemeMode union defined in exactly one place.
import type { ThemeMode } from '$lib/theme.svelte';

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
	themeMode: 'system',
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
	themeMode: ThemeMode;
};

// `preferences` is a cast over untyped JSON, so narrow before trusting it.
function asThemeMode(value: unknown): ThemeMode | null {
	return value === 'light' || value === 'dark' || value === 'system' ? value : null;
}

export const load: LayoutServerLoad = async ({ locals, fetch, cookies, url }) => {
	const cookieLocale = cookies.get('PARAGLIDE_LOCALE');
	const cookieThemeMode = asThemeMode(cookies.get('KEENTI_THEME'));
	let preferences: Preferences = {
		...DEFAULT_PREFERENCES,
		locale: cookieLocale === 'en' ? 'en' : DEFAULT_PREFERENCES.locale,
		themeMode: cookieThemeMode ?? DEFAULT_PREFERENCES.themeMode,
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
			// Mirrored to a cookie so hooks.server.ts and the inline script in
			// app.html can resolve the scheme before any JS bundle loads.
			preferences.themeMode = asThemeMode(preferences.themeMode) ?? DEFAULT_PREFERENCES.themeMode;
			cookies.set('KEENTI_THEME', preferences.themeMode, {
				path: '/',
				sameSite: 'lax',
				maxAge: 34_560_000,
				httpOnly: false,
			});
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
