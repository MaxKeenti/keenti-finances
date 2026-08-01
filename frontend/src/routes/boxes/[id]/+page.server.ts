import { error, fail } from '@sveltejs/kit';
import { superValidate } from 'sveltekit-superforms';
import { zod4 } from 'sveltekit-superforms/adapters';
import { z } from 'zod';
import { dateInTimeZone } from '$lib/formatting';
import { getSession } from '$lib/server/workos-session';
import { m } from '$lib/paraglide/messages.js';
import type { BoxDto, BoxMovementDto } from '$lib/types/boxes';
import {
	isActivePlan,
	type BoxPlan,
	type BoxPlanSummary,
} from '$lib/types/box-plans';
import type { Actions, PageServerLoad } from './$types';

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';
const MAX_AMOUNT = 9_999_999_999.99;

function movementSchema(today: string) {
	return z
	.object({
		kind: z.enum(['DEPOSIT', 'WITHDRAWAL', 'TRANSFER']),
		amount: z.coerce
			.number()
			.positive(m.validation_amount_positive())
			.max(MAX_AMOUNT, m.validation_amount_too_large())
			.refine(
				(value) => Math.abs(value * 100 - Math.round(value * 100)) < 1e-7,
				m.validation_amount_two_decimals(),
			),
		effectiveDate: z
			.string()
			.regex(/^\d{4}-\d{2}-\d{2}$/, m.validation_date_required())
			.refine((date) => date <= today, m.validation_box_date_future()),
		targetBoxId: z.coerce.number().int().min(0).default(0),
	})
	.superRefine((value, context) => {
		if (value.kind === 'TRANSFER' && value.targetBoxId <= 0) {
			context.addIssue({
				code: 'custom',
				path: ['targetBoxId'],
				message: m.validation_box_target_required(),
			});
		}
	});
}

function movementCorrectionSchema(today: string) {
	return z.object({
		movementId: z.coerce.number().int().positive(),
		amount: z.coerce
			.number()
			.positive(m.validation_amount_positive())
			.max(MAX_AMOUNT, m.validation_amount_too_large())
			.refine(
				(value) => Math.abs(value * 100 - Math.round(value * 100)) < 1e-7,
				m.validation_amount_two_decimals(),
			),
		effectiveDate: z
			.string()
			.regex(/^\d{4}-\d{2}-\d{2}$/, m.validation_date_required())
			.refine((date) => date <= today, m.validation_box_date_future()),
	});
}

function headers(cookies: Parameters<typeof getSession>[0], json = false): Record<string, string> {
	const accessToken = getSession(cookies)?.accessToken;
	return {
		...(json ? { 'content-type': 'application/json' } : {}),
		...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
	};
}

async function actionToday(
	fetch: Parameters<PageServerLoad>[0]['fetch'],
	cookies: Parameters<typeof getSession>[0],
): Promise<string> {
	try {
		const response = await fetch(`${BACKEND}/api/user/preferences`, { headers: headers(cookies) });
		if (response.ok) {
			const preferences = (await response.json()) as { timeZone?: string };
			return dateInTimeZone(preferences.timeZone);
		}
	} catch {
		// The backend remains the authority for the effective-date guard.
	}
	return dateInTimeZone('America/Mexico_City');
}

export const load: PageServerLoad = async ({ params, fetch, cookies, url, parent }) => {
	const id = Number(params.id);
	if (!Number.isInteger(id) || id <= 0) error(404, m.error_box_not_found());
	const { preferences } = await parent();
	const today = dateInTimeZone(preferences.timeZone);

	let box: BoxDto;
	let history: BoxMovementDto[] = [];
	let activeBoxes: BoxDto[] = [];
	let planSummaries: BoxPlanSummary[] = [];
	let planDetail: BoxPlan | null = null;
	let planLoadFailed = false;

	try {
		const authHeaders = headers(cookies);
		const [boxResponse, historyResponse, boxesResponse, plansResponse] = await Promise.all([
			fetch(`${BACKEND}/api/boxes/${id}`, { headers: authHeaders }),
			fetch(`${BACKEND}/api/boxes/${id}/history`, { headers: authHeaders }),
			fetch(`${BACKEND}/api/boxes`, { headers: authHeaders }),
			fetch(`${BACKEND}/api/boxes/${id}/plans`, { headers: authHeaders }),
		]);

		if (boxResponse.status === 404) error(404, m.error_box_not_found());
		if (!boxResponse.ok) {
			console.error(`[boxes/${id}] load: box returned ${boxResponse.status}`);
			error(502, m.error_box_load());
		}
		box = (await boxResponse.json()) as BoxDto;

		if (historyResponse.ok) history = (await historyResponse.json()) as BoxMovementDto[];
		else console.error(`[boxes/${id}] load: history returned ${historyResponse.status}`);

		if (boxesResponse.ok) activeBoxes = (await boxesResponse.json()) as BoxDto[];
		else console.error(`[boxes/${id}] load: boxes returned ${boxesResponse.status}`);

		if (plansResponse.ok) {
			planSummaries = (await plansResponse.json()) as BoxPlanSummary[];
		} else if (plansResponse.status !== 404) {
			planLoadFailed = true;
			console.error(`[boxes/${id}] load: plans returned ${plansResponse.status}`);
		}

		const requestedId = Number(url.searchParams.get('plan'));
		const requestedPlan = Number.isInteger(requestedId)
			? planSummaries.find((plan) => plan.id === requestedId)
			: undefined;
		const selectedPlan =
			requestedPlan ?? planSummaries.find(isActivePlan) ?? planSummaries.at(0);

		if (selectedPlan) {
			const typePath = selectedPlan.type === 'SAVING_GOAL' ? 'saving-goal' : 'spending-budget';
			const detailResponse = await fetch(
				`${BACKEND}/api/boxes/${id}/plans/${typePath}/${selectedPlan.id}`,
				{ headers: authHeaders },
			);
			if (detailResponse.ok) planDetail = (await detailResponse.json()) as BoxPlan;
			else {
				planLoadFailed = true;
				console.error(
					`[boxes/${id}] load: ${typePath}/${selectedPlan.id} returned ${detailResponse.status}`,
				);
			}
		}
	} catch (cause) {
		if ((cause as { status?: number }).status) throw cause;
		console.error(`[boxes/${id}] load: backend unreachable`);
		error(502, m.error_backend_unreachable());
	}

	const form = await superValidate(
		{
			kind: 'DEPOSIT' as const,
			amount: 0,
			effectiveDate: today,
			targetBoxId: 0,
		},
		zod4(movementSchema(today)),
		{ errors: false },
	);

	return {
		box,
		history,
		planSummaries: planSummaries.toSorted((a, b) => b.createdAt.localeCompare(a.createdAt)),
		planDetail,
		planLoadFailed,
		viewingHistorical: planDetail ? !isActivePlan(planDetail) : false,
		transferTargets: activeBoxes
			.filter((candidate) => candidate.id !== id && !candidate.archived)
			.toSorted((a, b) => a.displayOrder - b.displayOrder),
		form,
		today,
	};
};

export const actions: Actions = {
	move: async ({ params, request, fetch, cookies }) => {
		const id = Number(params.id);
		const today = await actionToday(fetch, cookies);
		const form = await superValidate(request, zod4(movementSchema(today)));
		if (!form.valid) return fail(400, { form });

		const endpoint =
			form.data.kind === 'DEPOSIT'
				? 'deposit'
				: form.data.kind === 'WITHDRAWAL'
					? 'withdraw'
					: 'transfer';
		const payload = {
			amount: form.data.amount,
			effectiveDate: form.data.effectiveDate,
			...(form.data.kind === 'TRANSFER' ? { targetBoxId: form.data.targetBoxId } : {}),
		};

		let response: Response;
		try {
			response = await fetch(`${BACKEND}/api/boxes/${id}/${endpoint}`, {
				method: 'POST',
				headers: headers(cookies, true),
				body: JSON.stringify(payload),
			});
		} catch {
			return fail(502, { form: { ...form, message: m.error_backend_unreachable() } });
		}

		if (response.status === 404) {
			return fail(404, { form: { ...form, message: m.error_box_not_found_or_archived() } });
		}
		if (response.status === 400) {
			return fail(400, { form: { ...form, message: m.error_box_movement_invalid() } });
		}
		if (!response.ok) {
			console.error(`[boxes/${id}] ${endpoint}: backend error ${response.status}`);
			return fail(502, { form: { ...form, message: m.error_box_movement() } });
		}

		return { form, movementKind: form.data.kind };
	},

	correct: async ({ params, request, fetch, cookies }) => {
		const id = Number(params.id);
		if (!Number.isInteger(id) || id <= 0) {
			return fail(404, { message: m.error_box_movement_correction_not_found() });
		}

		const today = await actionToday(fetch, cookies);
		const parsed = movementCorrectionSchema(today).safeParse(
			Object.fromEntries(await request.formData()),
		);
		if (!parsed.success) {
			return fail(400, {
				message:
					parsed.error.issues.at(0)?.message ?? m.error_box_movement_correction_invalid(),
			});
		}

		let response: Response;
		try {
			response = await fetch(
				`${BACKEND}/api/boxes/${id}/movements/${parsed.data.movementId}`,
				{
					method: 'PUT',
					headers: headers(cookies, true),
					body: JSON.stringify({
						amount: parsed.data.amount,
						effectiveDate: parsed.data.effectiveDate,
					}),
				},
			);
		} catch {
			return fail(502, { message: m.error_backend_unreachable() });
		}

		if (response.status === 400) {
			return fail(400, { message: m.error_box_movement_correction_invalid() });
		}
		if (response.status === 404) {
			return fail(404, { message: m.error_box_movement_correction_not_found() });
		}
		if (response.status === 409) {
			return fail(409, { message: m.error_box_movement_correction_conflict() });
		}
		if (!response.ok) {
			console.error(
				`[boxes/${id}] correct movement ${parsed.data.movementId}: backend error ${response.status}`,
			);
			return fail(502, { message: m.error_box_movement_correction() });
		}

		return { corrected: true };
	},

	archive: async ({ params, fetch, cookies }) => {
		const id = Number(params.id);
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

	restore: async ({ params, fetch, cookies }) => {
		const id = Number(params.id);
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
