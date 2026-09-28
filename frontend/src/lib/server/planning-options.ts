/**
 * Option catalogs for the planning preview (slice 5C).
 *
 * These lists only decide what the User may *select*. They never supply an
 * amount to the preview: the server reloads every Box balance and receipt
 * amount when it calculates (D5). Each catalog is its own section, so a failed
 * read says "unavailable" instead of offering an empty list that reads as
 * "you have nothing to select".
 *
 * The receipt catalogs are complete reads, not the dashboard's expected-money
 * section, which is capped and so cannot list everything selectable:
 *
 * - Debts: the User's full Debt list, keeping ACTIVE INGRESS Debts with a
 *   remaining amount the preview can carry.
 * - Payment Records: every Subscription's own records, keeping PENDING ones
 *   owed by a Subscription Member. PAID records are excluded whether or not a
 *   Transaction was linked, and the Subscription's own charge (no Member) is
 *   the Owner's price, not money owed to them. Trashed Subscriptions are not
 *   in the Subscription list, so their records are never offered.
 *
 * Every request here is a GET. Nothing is generated, linked or settled.
 */

import { loadSection, type SectionFetch } from '$lib/server/section-load';
import {
	parseMembers,
	parsePayments,
	parseSubscriptions,
	type MemberResponse,
} from '$lib/server/payloads';
import { sectionOk, sectionUnavailable, type Section } from '$lib/types/section';

export type PlanningBoxOption = { id: number; name: string; balance: number };

export type PlanningDebtOption = {
	id: number;
	description: string;
	contactName: string | null;
	remaining: number;
};

export type PlanningPaymentRecordOption = {
	id: number;
	subscriptionId: number;
	subscriptionName: string;
	memberId: number;
	/** `null` when the Member's name could not be read or they have no Contact. */
	memberName: string | null;
	billingDate: string;
	amount: number;
};

export type PlanningOptions = {
	boxes: Section<PlanningBoxOption[]>;
	debts: Section<PlanningDebtOption[]>;
	paymentRecords: Section<PlanningPaymentRecordOption[]>;
};

const MAX_PREVIEW_AMOUNT_CENTS = 999_999_999;

/**
 * How many Subscriptions have their payments and Members read at once.
 *
 * Every return to the planning tab reloads these catalogs (a hidden tab may
 * have fallen behind the ledger), so the per-Subscription reads are bounded
 * rather than fired all together for a User with many Subscriptions.
 */
export const SUBSCRIPTION_READ_CONCURRENCY = 4;

/** `map` with at most `limit` calls pending at once; results keep input order. */
export async function mapWithConcurrency<T, R>(
	items: readonly T[],
	limit: number,
	fn: (item: T) => Promise<R>,
): Promise<R[]> {
	const results = new Array<R>(items.length);
	let next = 0;
	async function worker() {
		while (next < items.length) {
			const index = next++;
			results[index] = await fn(items[index]);
		}
	}
	await Promise.all(Array.from({ length: Math.min(Math.max(1, limit), items.length) }, worker));
	return results;
}

/**
 * Mirrors the server's receipt predicate (positive, whole cents, at most
 * 9,999,999.99) so the catalog does not offer a record the preview will
 * always call ineligible. The server still decides.
 */
export function isPreviewAmount(value: number): boolean {
	if (!Number.isFinite(value) || value <= 0) return false;
	const cents = Math.round(value * 100);
	return Math.abs(value * 100 - cents) < 1e-6 && cents <= MAX_PREVIEW_AMOUNT_CENTS;
}

function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function num(value: unknown): number | null {
	return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

/** Active Boxes, including those with no Box Plan. An unreadable row fails the list. */
export function parseBoxOptions(value: unknown): PlanningBoxOption[] | null {
	if (!Array.isArray(value)) return null;
	const boxes: PlanningBoxOption[] = [];
	for (const item of value) {
		if (!isRecord(item)) return null;
		const id = num(item.id);
		const balance = num(item.balance);
		if (id === null || balance === null || typeof item.name !== 'string') return null;
		if (item.archived === true) continue;
		boxes.push({ id, name: item.name, balance });
	}
	return boxes;
}

/**
 * Selectable INGRESS Debts.
 *
 * Direction, status and remaining are all required to decide eligibility, so
 * an unreadable one fails the list rather than hiding one Debt.
 */
export function parseDebtOptions(value: unknown): PlanningDebtOption[] | null {
	if (!Array.isArray(value)) return null;
	const debts: PlanningDebtOption[] = [];
	for (const item of value) {
		if (!isRecord(item)) return null;
		const id = num(item.id);
		const remaining = num(item.remaining);
		const { direction, status } = item;
		if (id === null || remaining === null) return null;
		if (typeof direction !== 'string' || typeof status !== 'string') return null;
		if (direction !== 'INGRESS' || status !== 'ACTIVE' || !isPreviewAmount(remaining)) continue;
		debts.push({
			id,
			description: typeof item.description === 'string' ? item.description : '',
			contactName: typeof item.contactName === 'string' ? item.contactName : null,
			remaining,
		});
	}
	return debts;
}

async function loadPaymentRecordOptions(
	fetchFn: SectionFetch,
	backend: string,
	headers: Record<string, string>,
): Promise<Section<PlanningPaymentRecordOption[]>> {
	const subscriptions = await loadSection(fetchFn, `${backend}/api/subscriptions`, {
		parse: parseSubscriptions,
		headers,
		label: 'planning/subscriptions',
	});
	if (subscriptions.status !== 'ok') return sectionUnavailable(subscriptions.reason);

	const perSubscription = await mapWithConcurrency(
		subscriptions.data,
		SUBSCRIPTION_READ_CONCURRENCY,
		async (subscription) => {
			const [payments, members] = await Promise.all([
				loadSection(fetchFn, `${backend}/api/subscriptions/${subscription.id}/payments`, {
					parse: parsePayments,
					headers,
					label: `planning/subscriptions/${subscription.id}/payments`,
				}),
				// Names only. Personal Subscriptions have no Members to read.
				subscription.type === 'SHARED'
					? loadSection<MemberResponse[]>(
							fetchFn,
							`${backend}/api/subscriptions/${subscription.id}/members`,
							{
								parse: parseMembers,
								headers,
								label: `planning/subscriptions/${subscription.id}/members`,
							},
						)
					: Promise.resolve(sectionOk<MemberResponse[]>([])),
			]);
			return { subscription, payments, members };
		},
	);

	const options: PlanningPaymentRecordOption[] = [];
	for (const { subscription, payments, members } of perSubscription) {
		// One unreadable Subscription makes the catalog incomplete, and an
		// incomplete catalog is not offered as if it were the whole list.
		if (payments.status !== 'ok') return sectionUnavailable(payments.reason);
		const names = new Map(
			(members.status === 'ok' ? members.data : []).map((member) => [member.id, member.contactName]),
		);
		for (const record of payments.data) {
			if (record.status !== 'PENDING' || record.memberId === null) continue;
			if (!isPreviewAmount(record.amount)) continue;
			options.push({
				id: record.id,
				subscriptionId: subscription.id,
				subscriptionName: subscription.name,
				memberId: record.memberId,
				memberName: names.get(record.memberId) ?? null,
				billingDate: record.billingDate,
				amount: record.amount,
			});
		}
	}
	options.sort((a, b) => a.billingDate.localeCompare(b.billingDate) || a.id - b.id);
	return sectionOk(options);
}

export async function loadPlanningOptions(
	fetchFn: SectionFetch,
	backend: string,
	headers: Record<string, string>,
): Promise<PlanningOptions> {
	const [boxes, debts, paymentRecords] = await Promise.all([
		loadSection(fetchFn, `${backend}/api/boxes`, {
			parse: parseBoxOptions,
			headers,
			label: 'planning/boxes',
		}),
		loadSection(fetchFn, `${backend}/api/debts`, {
			parse: parseDebtOptions,
			headers,
			label: 'planning/debts',
		}),
		loadPaymentRecordOptions(fetchFn, backend, headers),
	]);
	return { boxes, debts, paymentRecords };
}
