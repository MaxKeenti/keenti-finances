/**
 * The navigation model, shared by the dock and by its tests.
 *
 * Slice 2A (decision D4) changes what mobile navigation offers by default:
 * Dashboard, Transactions, Boxes, and More, so planning is reachable without
 * guessing. It does not change what a User chose. The preference migration
 * below is an upgrade of stored navigation settings — not a Flyway database
 * migration — and it never writes: it interprets the stored value on read, so
 * a User whose pins predate this slice keeps them, in their order, and can
 * still change them afterwards.
 */

/** Destinations that may be pinned to the mobile bar. */
export const NAVIGABLE_HREFS = [
	'/',
	'/transactions',
	'/boxes',
	'/accounts',
	'/subscriptions',
	'/debts',
	'/settings',
	'/categories',
	'/contacts',
	'/trash',
] as const;

/** Dashboard is the stable first entry of the mobile bar. */
export const DASHBOARD_HREF = '/';

/**
 * Defaults for a User who never pinned anything: Transactions and Boxes
 * alongside the stable Dashboard entry, with everything else under More.
 */
export const DEFAULT_PINNED_HREFS = ['/transactions', '/boxes'] as const;

/** Explicit pins kept beyond Dashboard, so the bar stays usable at 320px. */
const MAX_EXPLICIT_PINS = 3;

/**
 * Resolves the mobile bar's destinations from the stored preference.
 *
 * Dashboard always leads. Every explicitly stored pin that names a real
 * destination is preserved, in the stored order, and a stored Dashboard pin is
 * de-duplicated rather than shown twice — without rewriting the preference, so
 * the User's recorded choice is unchanged and reversible. Only a User who
 * stored nothing gets the new defaults.
 */
export function resolveMobileNavHrefs(stored: string | undefined | null): string[] {
	const allowed = new Set<string>(NAVIGABLE_HREFS);
	const explicit: string[] = [];
	for (const raw of (stored ?? '').split(',')) {
		const href = raw.trim();
		if (!allowed.has(href) || href === DASHBOARD_HREF) continue;
		if (!explicit.includes(href)) explicit.push(href);
	}

	const pins = hasExplicitPins(stored) ? explicit.slice(0, MAX_EXPLICIT_PINS) : [...DEFAULT_PINNED_HREFS];
	return [DASHBOARD_HREF, ...pins];
}

/** True when the stored preference names at least one usable destination. */
export function hasExplicitPins(stored: string | undefined | null): boolean {
	const allowed = new Set<string>(NAVIGABLE_HREFS);
	return (stored ?? '')
		.split(',')
		.map((href) => href.trim())
		.some((href) => allowed.has(href));
}
