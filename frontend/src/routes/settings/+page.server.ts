import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ parent }) => {
	const { preferences } = await parent();
	return { preferences };
};
