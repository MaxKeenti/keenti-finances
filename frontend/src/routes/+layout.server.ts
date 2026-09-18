import type { LayoutServerLoad } from './$types';
import { userToday, type DayResolution } from '$lib/obligation-status';
import { redirect } from '@sveltejs/kit';
import type { BalanceSummary } from '$lib/types/boxes';
import { sectionUnavailable, type Section } from '$lib/types/section';
import {
	parseAccountTrackingStatus,
	parseBalanceSummary,
	type AccountTrackingStatus,
} from '$lib/server/payloads';
import { loadSection } from '$lib/server/section-load';
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
	mobilePinnedNavItems: '/,/transactions,/boxes',
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
	const requestInstant = new Date();
	let obligationToday: DayResolution = { status: 'unavailable', reason: 'zone' };
	const cookieLocale = cookies.get('PARAGLIDE_LOCALE');
	const cookieThemeMode = asThemeMode(cookies.get('KEENTI_THEME'));
	let preferences: Preferences = {
		...DEFAULT_PREFERENCES,
		locale: cookieLocale === 'en' ? 'en' : DEFAULT_PREFERENCES.locale,
		themeMode: cookieThemeMode ?? DEFAULT_PREFERENCES.themeMode,
	};
	// Unavailable until proven otherwise: the app shell used to render a failed
	// summary as 0.00 Available to Spend, which is a claim about the User's
	// money rather than an absence of one.
	let balanceSummary: Section<BalanceSummary> = sectionUnavailable('unreachable');
	// Which formula produced the Net Balance on screen is a fact about the
	// User's setup, and the only authority for it is this response's `active`
	// boolean (decision D1). When the read fails the app says the tracking
	// information is unavailable rather than describing the total with a
	// formula nobody confirmed — and, in particular, rather than assuming the
	// pre-activation one.
	let accountTracking: Section<AccountTrackingStatus> = sectionUnavailable('unreachable');

	if (locals.session) {
		const [preferencesResult, balanceResult, accountStatusResult] = await Promise.allSettled([
			fetch(`${BACKEND}/api/user/preferences`),
			loadSection(fetch, `${BACKEND}/api/boxes/summary`, {
				parse: parseBalanceSummary,
				label: 'layout/boxes-summary',
			}),
			loadSection(fetch, `${BACKEND}/api/accounts/status`, {
				parse: parseAccountTrackingStatus,
				label: 'layout/account-status',
			}),
		]);

		accountTracking =
			accountStatusResult.status === 'fulfilled'
				? accountStatusResult.value
				: sectionUnavailable('unreachable');

		// Only a successfully parsed `setupRequired: true` sends the User to
		// setup. An unreadable status must not strand them on a setup screen
		// they may not need, nor let them past one they do — so it changes
		// nothing here and surfaces as an unavailable section instead.
		if (
			accountTracking.status === 'ok' &&
			accountTracking.data.setupRequired &&
			url.pathname !== '/accounts' &&
			url.pathname !== '/logout' &&
			!url.pathname.startsWith('/public/')
		) {
			redirect(303, '/accounts');
		}

		if (preferencesResult.status === 'fulfilled' && preferencesResult.value.ok) {
			try {
				const loadedPreferences = await preferencesResult.value.json();
				// Appearance defaults are safe, but cannot stand in for the User's
				// calendar when deciding whether a payment is past due. Capture once
				// on the server so hydration uses the very same calendar day.
				obligationToday = userToday(loadedPreferences?.timeZone, requestInstant);
				preferences = { ...preferences, ...loadedPreferences };
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
			} catch {
				console.error('[layout] invalid user preferences; calendar unavailable');
			}
		} else {
			console.error('[layout] failed to load user preferences; using defaults');
		}

		// `loadSection` resolves with its own failure reason, so a rejection here
		// would be a bug rather than a backend problem; treat it as unavailable
		// too instead of falling back to zeros.
		balanceSummary =
			balanceResult.status === 'fulfilled' ? balanceResult.value : sectionUnavailable('unreachable');
	}

	return { session: locals.session, preferences, balanceSummary, accountTracking, obligationToday };
};
