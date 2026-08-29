import { matchesMediaQuery, useMediaQuery } from '$lib/use-mobile.svelte';

const DARK_QUERY = '(prefers-color-scheme: dark)';

export type ThemeMode = 'light' | 'dark' | 'system';

export const THEME_MODES = ['system', 'light', 'dark'] as const;

export const DEFAULT_THEME_MODE: ThemeMode = 'system';

export function isThemeMode(value: unknown): value is ThemeMode {
	return value === 'system' || value === 'light' || value === 'dark';
}

let isDark = $state(false);

export function getIsDark() {
	return isDark;
}

/** Single writer for the `dark` class on <html>; keeps getIsDark() in sync with it. */
function applyIsDark(dark: boolean) {
	isDark = dark;
	document.documentElement.classList.toggle('dark', dark);
}

/**
 * Applies a mode right away, without waiting for the preference to round-trip
 * through the server. Settings uses this so the switch feels instant; the
 * effect in `initTheme` re-asserts the same result once the new value lands in
 * `data.preferences`, so the two never disagree.
 */
export function applyThemeMode(mode: ThemeMode) {
	applyIsDark(mode === 'system' ? matchesMediaQuery(DARK_QUERY) : mode === 'dark');
}

/**
 * `mode` is a getter, not a value, so callers can hand over reactive state:
 * picking a new mode in settings re-runs the effect and repaints <html> live.
 *
 * On 'system' the effect reads the media query and therefore subscribes to it.
 * On 'light'/'dark' it never reads it, so the effect is not tracking OS changes
 * at all and an explicit choice cannot be overridden by the OS flipping scheme.
 */
export function initTheme(mode: () => ThemeMode = () => DEFAULT_THEME_MODE) {
	const prefersDark = useMediaQuery(DARK_QUERY, matchesMediaQuery(DARK_QUERY));

	$effect(() => {
		const current = mode();
		applyIsDark(current === 'system' ? prefersDark.current : current === 'dark');
	});
}
