import type { LayoutServerLoad } from './$types';

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
};

export const load: LayoutServerLoad = async ({ locals, fetch, cookies }) => {
	const cookieLocale = cookies.get('PARAGLIDE_LOCALE');
	let preferences: Preferences = {
		...DEFAULT_PREFERENCES,
		locale: cookieLocale === 'en' ? 'en' : DEFAULT_PREFERENCES.locale,
	};

	if (locals.session) {
		try {
			const res = await fetch(`${BACKEND}/api/user/preferences`);
			if (res.ok) {
				preferences = (await res.json()) as Preferences;
				if (preferences.locale === 'en' || preferences.locale === 'es') {
					cookies.set('PARAGLIDE_LOCALE', preferences.locale, {
						path: '/',
						sameSite: 'lax',
						maxAge: 34_560_000,
						httpOnly: false,
					});
				}
			}
		} catch {
			console.error('[layout] failed to load user preferences; using defaults');
		}
	}

	return { session: locals.session, preferences };
};
