import { onMount } from 'svelte';

export const MOBILE_POINTER_QUERY = '(hover: none) and (pointer: coarse)';

export function matchesMediaQuery(query: string) {
	return typeof window !== 'undefined' && window.matchMedia(query).matches;
}

export function useMediaQuery(query: string, initialValue = false) {
	let current = $state(initialValue);

	onMount(() => {
		const mq = window.matchMedia(query);
		current = mq.matches;

		const onChange = (event: MediaQueryListEvent) => {
			current = event.matches;
		};

		mq.addEventListener('change', onChange);
		return () => mq.removeEventListener('change', onChange);
	});

	return {
		get current() {
			return current;
		},
	};
}

export function useIsMobile() {
	return useMediaQuery(MOBILE_POINTER_QUERY);
}

export function isMobileViewport() {
	return matchesMediaQuery(MOBILE_POINTER_QUERY);
}
