import { fail } from '@sveltejs/kit';
import { getSession } from '$lib/server/workos-session';
import { m } from '$lib/paraglide/messages.js';
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

		if (!id || !entityType) return fail(400, { message: m.error_missing_id_or_entity_type() });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/trash/${entityType}/${id}/restore`, {
				method: 'POST',
				headers: authHeaders,
			});
		} catch {
			console.error('[trash] restore: backend unreachable');
			return fail(502, { message: m.error_backend_unreachable() });
		}

		if (res.status === 404) return fail(404, { message: m.error_item_not_found() });
		if (!res.ok) {
			console.error(`[trash] restore: backend error ${res.status}`);
			return fail(502, { message: m.error_unexpected_restore_item() });
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

		if (!id || !entityType) return fail(400, { message: m.error_missing_id_or_entity_type() });

		let res: Response;
		try {
			res = await fetch(`${BACKEND}/api/trash/${entityType}/${id}`, {
				method: 'DELETE',
				headers: authHeaders,
			});
		} catch {
			console.error('[trash] permanentDelete: backend unreachable');
			return fail(502, { message: m.error_backend_unreachable() });
		}

		if (res.status === 404) return fail(404, { message: m.error_item_not_found() });
		if (!res.ok) {
			console.error(`[trash] permanentDelete: backend error ${res.status}`);
			return fail(502, { message: m.error_unexpected_delete_item() });
		}

		console.log(`[trash] permanentDelete: success — type: ${entityType} id: ${id}`);
		return {};
	},

	bulkRestore: async ({ request, fetch, cookies }) =>
		bulkOperate(request, fetch, cookies, 'restore'),

	bulkPermanentDelete: async ({ request, fetch, cookies }) =>
		bulkOperate(request, fetch, cookies, 'delete'),
};

type SelectedItem = { id: number; entityType: string };

// Runs the same single-item backend call for each selected item, tolerating
// partial failure so one bad item doesn't sink the whole batch. Returns the
// done/failed/total counts for the client to surface.
async function bulkOperate(
	request: Request,
	fetch: typeof globalThis.fetch,
	cookies: Parameters<PageServerLoad>[0]['cookies'],
	op: 'restore' | 'delete',
) {
	const session = getSession(cookies);
	const accessToken = session?.accessToken;
	const authHeaders: Record<string, string> = accessToken
		? { Authorization: `Bearer ${accessToken}` }
		: {};

	const data = await request.formData();
	let items: SelectedItem[];
	try {
		items = JSON.parse(String(data.get('items') ?? '[]'));
	} catch {
		return fail(400, { message: m.error_missing_id_or_entity_type() });
	}

	if (!Array.isArray(items) || items.length === 0) {
		return fail(400, { message: m.error_missing_id_or_entity_type() });
	}

	let done = 0;
	let failed = 0;
	for (const item of items) {
		if (!item?.id || !item?.entityType) {
			failed++;
			continue;
		}
		const url =
			op === 'restore'
				? `${BACKEND}/api/trash/${item.entityType}/${item.id}/restore`
				: `${BACKEND}/api/trash/${item.entityType}/${item.id}`;
		try {
			const res = await fetch(url, {
				method: op === 'restore' ? 'POST' : 'DELETE',
				headers: authHeaders,
			});
			if (res.ok) done++;
			else failed++;
		} catch {
			failed++;
		}
	}

	console.log(`[trash] bulk ${op}: done ${done}, failed ${failed}, total ${items.length}`);
	return { done, failed, total: items.length };
}
