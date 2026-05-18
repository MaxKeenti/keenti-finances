import { onMount } from 'svelte';

let isDark = $state(false);

export function getIsDark() {
	return isDark;
}

export function initTheme() {
	onMount(() => {
		const mq = window.matchMedia('(prefers-color-scheme: dark)');
		isDark = mq.matches;

		const onChange = (e: MediaQueryListEvent) => {
			isDark = e.matches;
			document.documentElement.classList.toggle('dark', e.matches);
		};

		mq.addEventListener('change', onChange);
		return () => mq.removeEventListener('change', onChange);
	});
}
