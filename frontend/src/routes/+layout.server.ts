import type { LayoutServerLoad } from './$types';

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

const DEFAULT_PREFERENCES = {
	primaryHue: 91,
	headingFont: 'Fraunces',
	bodyFont: 'Geist',
} as const;

type Preferences = {
	primaryHue: number;
	headingFont: string;
	bodyFont: string;
};

export const load: LayoutServerLoad = async ({ locals, fetch }) => {
	let preferences: Preferences = { ...DEFAULT_PREFERENCES };

	if (locals.session) {
		try {
			const res = await fetch(`${BACKEND}/api/user/preferences`);
			if (res.ok) {
				preferences = (await res.json()) as Preferences;
			}
		} catch {
			console.error('[layout] failed to load user preferences; using defaults');
		}
	}

	return { session: locals.session, preferences };
};
