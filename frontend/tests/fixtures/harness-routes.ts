/**
 * Routes every authenticated page needs, regardless of scenario.
 *
 * `+layout.server.ts` requests these on each navigation. A scenario that
 * declares one wins; these only keep the shell from degrading to defaults in
 * scenarios whose subject is something else. `timeZone` matches the fixture
 * clocks' Mexico City zone, so a date rendered against them is interpreted in
 * the same zone the scenarios were written against.
 *
 * The loader tests and the browser fixture server share this so the two cannot
 * disagree about what the shell was given.
 */
export const HARNESS_ROUTES: Record<string, unknown> = {
	'GET /api/user/preferences': {
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
	},
};
