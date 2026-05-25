import { fail } from '@sveltejs/kit';
import { getSession } from '$lib/server/workos-session';
import type { Actions, PageServerLoad } from './$types';

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

export interface TrashItem {
	id: number;
	entityType: string;
	label: string;
	deletedAt: string;
}

export const load: PageServerLoad = async ({ fetch, cookies }) => {
	const session = getSession(cookies);
	const accessToken = session?.accessToken;
	const authHeaders: Record<string, string> = accessToken
		? { Authorization: `Bearer ${accessToken}` }
		: {};

	let items: TrashItem[] = [];
	try {
		const res = await fetch(`${BACKEND}/api/trash`, { headers: authHeaders });
		if (res.ok) {
			items = await res.json();
		} else {
			console.error(`[trash] load: backend returned ${res.status}`);
		}
	} catch {
		console.error('[trash] load: backend unreachable');
	}

	return { items };
};

export const actions: Actions = {
	restore: async ({ request, fetch, cookies }) => {
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		const data = await request.formData();
		const id = data.get('id');
		const entityType = data.get('entityType');

		if (!id || !entityType) return fail(400, { message: 'Missing id or entityType.' });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/trash/${entityType}/${id}/restore`, {
				method: 'POST',
				headers: authHeaders,
			});
		} catch {
			console.error('[trash] restore: backend unreachable');
			return fail(502, { message: 'Could not reach backend service.' });
		}

		if (res.status === 404) return fail(404, { message: 'Item not found in trash.' });
		if (!res.ok) {
			console.error(`[trash] restore: backend error ${res.status}`);
			return fail(502, { message: 'Unexpected error restoring item.' });
		}

		console.log(`[trash] restore: success — type: ${entityType} id: ${id}`);
		return {};
	},

	permanentDelete: async ({ request, fetch, cookies }) => {
		const session = getSession(cookies);
		const accessToken = session?.accessToken;
		const authHeaders: Record<string, string> = accessToken
			? { Authorization: `Bearer ${accessToken}` }
			: {};

		const data = await request.formData();
		const id = data.get('id');
		const entityType = data.get('entityType');

		if (!id || !entityType) return fail(400, { message: 'Missing id or entityType.' });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/trash/${entityType}/${id}`, {
				method: 'DELETE',
				headers: authHeaders,
			});
		} catch {
			console.error('[trash] permanentDelete: backend unreachable');
			return fail(502, { message: 'Could not reach backend service.' });
		}

		if (res.status === 404) return fail(404, { message: 'Item not found in trash.' });
		if (!res.ok) {
			console.error(`[trash] permanentDelete: backend error ${res.status}`);
			return fail(502, { message: 'Unexpected error deleting item.' });
		}

		console.log(`[trash] permanentDelete: success — type: ${entityType} id: ${id}`);
		return {};
	},
};
