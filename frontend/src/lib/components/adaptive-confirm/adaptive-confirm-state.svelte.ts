import { tick } from 'svelte';
import { isMobileViewport } from '$lib/use-mobile.svelte';

export type AdaptiveConfirmOptions = {
	title: string;
	description?: string;
	confirmLabel: string;
	cancelLabel: string;
	destructive?: boolean;
	nativeMessage?: string;
};

type PendingConfirm = AdaptiveConfirmOptions & {
	resolve: (value: boolean) => void;
};

let pending = $state<PendingConfirm | null>(null);

function nativeConfirmMessage(options: AdaptiveConfirmOptions) {
	return options.nativeMessage ?? [options.title, options.description].filter(Boolean).join('\n\n');
}

export const adaptiveConfirmState = {
	get pending() {
		return pending;
	},
	settle(value: boolean) {
		const current = pending;
		pending = null;
		current?.resolve(value);
	},
};

export function adaptiveConfirm(options: AdaptiveConfirmOptions): Promise<boolean> {
	if (typeof window === 'undefined') return Promise.resolve(false);
	if (isMobileViewport()) return Promise.resolve(window.confirm(nativeConfirmMessage(options)));

	if (pending) adaptiveConfirmState.settle(false);

	return new Promise((resolve) => {
		pending = { ...options, resolve };
	});
}

export async function submitWithAdaptiveConfirm(
	form: HTMLFormElement | null | undefined,
	options: AdaptiveConfirmOptions,
) {
	if (!form) return;
	if (!(await adaptiveConfirm(options))) return;

	await tick();
	form.requestSubmit();
}
