import { fail } from '@sveltejs/kit';
import { superValidate } from 'sveltekit-superforms';
import { zod4 } from 'sveltekit-superforms/adapters';
import { z } from 'zod';
import { getSession } from '$lib/server/workos-session';
import { parseBoxPlanSummaries } from '$lib/server/payloads';
import { loadSection } from '$lib/server/section-load';
import { m } from '$lib/paraglide/messages.js';
import type { BoxDto } from '$lib/types/boxes';
import type { BoxPlanSummary } from '$lib/types/box-plans';
import type { Section } from '$lib/types/section';
import type { Actions, PageServerLoad } from './$types';

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

const boxSchema = z.object({
	id: z.coerce.number().int().positive().optional(),
	name: z.string().trim().min(1, m.validation_name_required()).max(100, m.validation_box_name_too_long()),
	hue: z.coerce.number().int().min(0).max(359),
	icon: z.string().trim().max(16, m.validation_box_icon_too_long()).optional(),
	description: z.string().trim().max(500, m.validation_box_description_too_long()).optional(),
});

function headers(cookies: Parameters<typeof getSession>[0], json = false): Record<string, string> {
	const accessToken = getSession(cookies)?.accessToken;
	return {
		...(json ? { 'content-type': 'application/json' } : {}),
		...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
	};
}

async function readBoxes(response: Response): Promise<BoxDto[]> {
	return response.ok ? ((await response.json()) as BoxDto[]) : [];
}

export const load: PageServerLoad = async ({ fetch, cookies }) => {
	let boxes: BoxDto[] = [];
	let archivedBoxes: BoxDto[] = [];
	let loadFailed = false;

	try {
		const authHeaders = headers(cookies);
		const [activeResponse, archivedResponse] = await Promise.all([
			fetch(`${BACKEND}/api/boxes`, { headers: authHeaders }),
			fetch(`${BACKEND}/api/boxes?archived=true`, { headers: authHeaders }),
		]);
		loadFailed = !activeResponse.ok || !archivedResponse.ok;
		boxes = await readBoxes(activeResponse);
		archivedBoxes = await readBoxes(archivedResponse);
	} catch {
		loadFailed = true;
		console.error('[boxes] load: backend unreachable');
	}

	// Slice 2A: the overview says what each Box is for, so it needs the Box's
	// Plan summaries. Each Box gets its own section: one Box whose plans cannot
	// be read must not make the others look unplanned, and "no active plan" —
	// which offers to create one — is only ever claimed from a list we read.
	const planSections: Record<number, Section<BoxPlanSummary[]>> = {};
	await Promise.all(
		boxes.map(async (box) => {
			planSections[box.id] = await loadSection<BoxPlanSummary[]>(
				fetch,
				`${BACKEND}/api/boxes/${box.id}/plans`,
				{
					parse: parseBoxPlanSummaries,
					headers: headers(cookies),
					label: `boxes/${box.id}/plans`,
				},
			);
		}),
	);

	const form = await superValidate(
		{ name: '', hue: 220, icon: '', description: '' },
		zod4(boxSchema),
	);

	return {
		boxes: boxes.toSorted((a, b) => a.displayOrder - b.displayOrder),
		archivedBoxes: archivedBoxes.toSorted((a, b) => a.displayOrder - b.displayOrder),
		planSections,
		loadFailed,
		form,
	};
};

export const actions: Actions = {
	create: async ({ request, fetch, cookies }) => {
		const form = await superValidate(request, zod4(boxSchema));
		if (!form.valid) return fail(400, { form });

		let response: Response;
		try {
			response = await fetch(`${BACKEND}/api/boxes`, {
				method: 'POST',
				headers: headers(cookies, true),
				body: JSON.stringify({
					name: form.data.name,
					hue: form.data.hue,
					icon: form.data.icon || null,
					description: form.data.description || null,
				}),
			});
		} catch {
			return fail(502, { form: { ...form, message: m.error_backend_unreachable() } });
		}

		if (response.status === 409) {
			return fail(409, { form: { ...form, message: m.error_box_exists() } });
		}
		if (response.status === 400) {
			return fail(400, { form: { ...form, message: m.error_invalid_box() } });
		}
		if (!response.ok) {
			console.error(`[boxes] create: backend error ${response.status}`);
			return fail(502, { form: { ...form, message: m.error_box_create() } });
		}

		return { form };
	},

	update: async ({ request, fetch, cookies }) => {
		const form = await superValidate(request, zod4(boxSchema));
		if (!form.valid) return fail(400, { form });
		if (!form.data.id) {
			return fail(400, { form: { ...form, message: m.error_missing_box_id() } });
		}

		let response: Response;
		try {
			response = await fetch(`${BACKEND}/api/boxes/${form.data.id}`, {
				method: 'PUT',
				headers: headers(cookies, true),
				body: JSON.stringify({
					name: form.data.name,
					hue: form.data.hue,
					icon: form.data.icon || null,
					description: form.data.description || null,
				}),
			});
		} catch {
			return fail(502, { form: { ...form, message: m.error_backend_unreachable() } });
		}

		if (response.status === 404) {
			return fail(404, { form: { ...form, message: m.error_box_not_found() } });
		}
		if (response.status === 409) {
			return fail(409, { form: { ...form, message: m.error_box_exists() } });
		}
		if (response.status === 400) {
			return fail(400, { form: { ...form, message: m.error_invalid_box() } });
		}
		if (!response.ok) {
			console.error(`[boxes] update: backend error ${response.status}`);
			return fail(502, { form: { ...form, message: m.error_box_update() } });
		}

		return { form };
	},

	reorder: async ({ request, fetch, cookies }) => {
		const data = await request.formData();
		const boxIds = String(data.get('boxIds') ?? '')
			.split(',')
			.map(Number)
			.filter((id) => Number.isInteger(id) && id > 0);
		if (boxIds.length === 0) return fail(400, { message: m.error_invalid_box_order() });

		try {
			const response = await fetch(`${BACKEND}/api/boxes/reorder`, {
				method: 'PUT',
				headers: headers(cookies, true),
				body: JSON.stringify({ boxIds }),
			});
			if (response.status === 400) return fail(400, { message: m.error_invalid_box_order() });
			if (response.status === 404) return fail(404, { message: m.error_box_not_found() });
			if (!response.ok) return fail(502, { message: m.error_box_reorder() });
		} catch {
			return fail(502, { message: m.error_backend_unreachable() });
		}

		return { reordered: true };
	},

	archive: async ({ request, fetch, cookies }) => {
		const data = await request.formData();
		const id = Number(data.get('id'));
		if (!Number.isInteger(id) || id <= 0) return fail(400, { message: m.error_missing_box_id() });

		try {
			const response = await fetch(`${BACKEND}/api/boxes/${id}/archive`, {
				method: 'POST',
				headers: headers(cookies),
			});
			if (response.status === 400) return fail(400, { message: m.error_box_archive_balance() });
			if (response.status === 409) return fail(409, { message: m.error_box_archive_active_plan() });
			if (response.status === 404) return fail(404, { message: m.error_box_not_found() });
			if (!response.ok) return fail(502, { message: m.error_box_archive() });
		} catch {
			return fail(502, { message: m.error_backend_unreachable() });
		}

		return { archived: true };
	},

	restore: async ({ request, fetch, cookies }) => {
		const data = await request.formData();
		const id = Number(data.get('id'));
		if (!Number.isInteger(id) || id <= 0) return fail(400, { message: m.error_missing_box_id() });

		try {
			const response = await fetch(`${BACKEND}/api/boxes/${id}/restore`, {
				method: 'POST',
				headers: headers(cookies),
			});
			if (response.status === 409) return fail(409, { message: m.error_box_exists() });
			if (response.status === 404) return fail(404, { message: m.error_box_not_found() });
			if (!response.ok) return fail(502, { message: m.error_box_restore() });
		} catch {
			return fail(502, { message: m.error_backend_unreachable() });
		}

		return { restored: true };
	},
};
