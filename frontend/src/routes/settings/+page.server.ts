import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ parent }) => {
	const { preferences } = await parent();
	const supportedValuesOf = (
		Intl as typeof Intl & { supportedValuesOf?: (key: 'timeZone') => string[] }
	).supportedValuesOf;
	const timeZones = supportedValuesOf
		? supportedValuesOf('timeZone')
		: ['America/Mexico_City', 'America/Cancun', 'America/Tijuana', 'UTC'];
	if (!timeZones.includes(preferences.timeZone)) timeZones.unshift(preferences.timeZone);
	return { preferences, timeZones };
};
