import { useMediaQuery } from '$lib/use-mobile.svelte';

let isDark = $state(false);

export function getIsDark() {
	return isDark;
}

export function initTheme() {
	const prefersDark = useMediaQuery('(prefers-color-scheme: dark)');

	$effect(() => {
		isDark = prefersDark.current;
		document.documentElement.classList.toggle('dark', prefersDark.current);
	});
}
