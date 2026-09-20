/**
 * User-facing labels for Box Plan types and statuses (ADR-0021).
 *
 * The same pattern as `statement-labels.ts` and `subscription-labels.ts`: the
 * wording lives in one place so the Boxes overview, a Box's own page and the
 * dashboard cannot describe the same plan differently.
 *
 * `boxPlanStatusLabel` covers the closed statuses too. The dashboard only ever
 * receives active ones, but a label function that quietly renders `ENDED` as
 * "Active" would be wrong wherever it is reused next.
 */

import { m } from '$lib/paraglide/messages.js';
import type { BoxPlanStatus, BoxPlanType } from '$lib/types/box-plans';

export function boxPlanTypeLabel(type: BoxPlanType): string {
	return type === 'SAVING_GOAL' ? m.box_plan_saving_goal() : m.box_plan_spending_budget();
}

export function boxPlanStatusLabel(status: BoxPlanStatus): string {
	switch (status) {
		case 'READY_TO_COMPLETE':
			return m.box_plan_status_ready();
		case 'OVERDUE':
			return m.box_plan_status_overdue();
		case 'COMPLETED':
			return m.box_plan_status_completed();
		case 'ABANDONED':
			return m.box_plan_status_abandoned();
		case 'ENDED':
			return m.box_plan_status_ended();
		default:
			return m.box_plan_status_active();
	}
}
