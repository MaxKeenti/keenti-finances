// @ts-nocheck
/**
 * Slice 3B: Subscription period and cost derivations (decision D2).
 *
 * These assert behavior, not wording: what a figure is derived from, what it
 * refuses to derive, and which claims it never makes. Fixture values come from
 * the 0A/0B scenarios so the tests and the manifest cannot drift.
 */

import { describe, expect, test } from 'bun:test';
import { loadScenario } from './fixtures/scenarios';
import { MEXICO_CITY_EVENING, TOKYO_SAME_INSTANT, instantOf } from './fixtures/clock';
import { billingGenerationStatus, userToday } from '../src/lib/obligation-status';
import { currentSplit, periodSummary, periodsOf, priceEquivalents } from '../src/lib/subscription-summary';

function scenarioParts(id, subscriptionId) {
	const { routes, expected } = loadScenario(id);
	return {
		expected,
		subscription: routes[`GET /api/subscriptions/${subscriptionId}`],
		members: routes[`GET /api/subscriptions/${subscriptionId}/members`],
		payments: routes[`GET /api/subscriptions/${subscriptionId}/payments`],
	};
}

describe('current split', () => {
	test('an owner-participating shared subscription splits over members plus the owner', () => {
		const { subscription, members, expected } = scenarioParts(
			'FX-SUB-SHARED-OWNER-PARTIAL-01',
			9406,
		);
		const split = currentSplit({ subscription, members });

		expect(split.state).toBe('split');
		expect(split.providerCost).toBe(expected.cost);
		expect(split.splitCount).toBe(expected.splitCount);
		expect(split.shareAmount).toBe(expected.shareAmount);
		expect(split.expectedContributions).toBe(expected.expectedContributions);
		expect(split.ownShare).toBe(expected.ownShare);
	});

	test('middleman mode leaves the owner no share and expects the full price from members', () => {
		const { subscription, members, expected } = scenarioParts(
			'FX-SUB-SHARED-MIDDLEMAN-PARTIAL-01',
			9407,
		);
		const split = currentSplit({ subscription, members });

		expect(split.splitCount).toBe(expected.splitCount);
		expect(split.ownShare).toBe(0);
		expect(split.expectedContributions).toBe(expected.expectedContributions);
		// The provider still charges the whole price; the Owner's share is not it.
		expect(split.providerCost).toBe(expected.cost);
	});

	test('a personal subscription is not split and the whole price is the owner’s', () => {
		const { subscription, members } = scenarioParts('FX-SUB-EMPTY-01', 9402);
		const split = currentSplit({ subscription, members });

		expect(split.state).toBe('personal');
		expect(split.ownShare).toBe(149);
		expect(split.expectedContributions).toBe(0);
	});

	test('a shared subscription with no members expects nothing and allocates by participation', () => {
		const participating = currentSplit({
			subscription: { cost: 600, billingCycle: 'MONTHLY', type: 'SHARED', ownerParticipates: true },
			members: [],
		});
		expect(participating.state).toBe('no-members');
		expect(participating.expectedContributions).toBe(0);
		expect(participating.ownShare).toBe(600);

		// Middleman with nobody to forward to: the price is real but unallocated.
		// `0` would claim the User owes nothing for a Subscription they still pay.
		const middleman = currentSplit({
			subscription: { cost: 600, billingCycle: 'MONTHLY', type: 'SHARED', ownerParticipates: false },
			members: [],
		});
		expect(middleman.state).toBe('no-members');
		expect(middleman.ownShare).toBeNull();
	});

	test('an unreadable member list is not a member-less subscription', () => {
		const split = currentSplit({
			subscription: { cost: 600, billingCycle: 'MONTHLY', type: 'SHARED', ownerParticipates: true },
			members: null,
		});

		expect(split.state).toBe('unavailable');
		expect(split.expectedContributions).toBeNull();
		expect(split.ownShare).toBeNull();
		// The price itself was readable and is not withheld with the split.
		expect(split.providerCost).toBe(600);
	});

	test('a share lands on the cent the backend rounds to, not one below it', () => {
		// 20.15 two ways is exactly 10.075. `BigDecimal.divide(2, HALF_UP)` gives
		// 10.08; rounding the float quotient gives 10.07, because JavaScript holds
		// 10.075 as 10.074999999999999. The stored `shareAmount` billing writes is
		// the backend's figure, so the page has to agree with it.
		const split = currentSplit({
			subscription: { cost: 20.15, billingCycle: 'MONTHLY', type: 'SHARED', ownerParticipates: true },
			members: [{ id: 1, shareAmount: null }],
		});
		expect(split.shareAmount).toBe(10.08);

		// 16.15 / 2 = 8.075 -> 8.08, and 0.615 / 2 = 0.3075 -> 0.31 at cents.
		expect(
			currentSplit({
				subscription: { cost: 16.15, billingCycle: 'MONTHLY', type: 'SHARED', ownerParticipates: true },
				members: [{ id: 1, shareAmount: null }],
			}).shareAmount,
		).toBe(8.08);
		expect(
			currentSplit({
				subscription: { cost: 0.61, billingCycle: 'MONTHLY', type: 'SHARED', ownerParticipates: true },
				members: [{ id: 1, shareAmount: null }],
			}).shareAmount,
		).toBe(0.31);
	});

	test('a stored share that cannot be read is estimated and counted, not hidden', () => {
		const split = currentSplit({
			subscription: { cost: 600, billingCycle: 'MONTHLY', type: 'SHARED', ownerParticipates: true },
			members: [
				{ id: 1, shareAmount: 200 },
				{ id: 2, shareAmount: -5 },
			],
		});

		// The readable stored share survives; only the invalid one is replaced by
		// the even split, and the surface is told how many were substituted.
		expect(split.expectedContributions).toBe(400);
		expect(split.expectedBasis).toBe('formula');
		expect(split.unreadableShares).toBe(1);

		const allStored = currentSplit({
			subscription: { cost: 600, billingCycle: 'MONTHLY', type: 'SHARED', ownerParticipates: true },
			members: [
				{ id: 1, shareAmount: 200 },
				{ id: 2, shareAmount: 200 },
			],
		});
		expect(allStored.expectedBasis).toBe('member-shares');
		expect(allStored.unreadableShares).toBe(0);
	});

	test('expected contributions follow the stored shares, not an invented even split', () => {
		// The Members were given uneven shares; today's price divided evenly would
		// claim an expectation billing would never write.
		const split = currentSplit({
			subscription: { cost: 300, billingCycle: 'MONTHLY', type: 'SHARED', ownerParticipates: false },
			members: [
				{ id: 1, shareAmount: 100 },
				{ id: 2, shareAmount: 200 },
			],
		});

		expect(split.expectedContributions).toBe(300);
		expect(split.expectedBasis).toBe('member-shares');
		expect(split.roundingRemainder).toBeNull();
	});

	test('cent rounding is surfaced rather than absorbed into the price', () => {
		const { subscription, members } = scenarioParts('FX-SUB-SHARED-POPULATED-01', 9401);
		const split = currentSplit({ subscription, members });

		expect(split.shareAmount).toBe(99.67);
		// 99.67 x 3 = 299.01 against a 299.00 price.
		expect(split.roundingRemainder).toBe(-0.01);
	});
});

describe('period summary', () => {
	test('collected and outstanding come from the stored records of that period', () => {
		const { payments, expected } = scenarioParts('FX-SUB-SHARED-OWNER-PARTIAL-01', 9406);
		const summary = periodSummary({ records: payments, billingDate: '2026-09-01' });

		expect(summary.state).toBe('has-records');
		expect(summary.collected).toBe(expected.collectedContributions);
		expect(summary.outstanding).toBe(expected.outstandingContributions);
		expect(summary.billed).toBe(expected.collectedContributions + expected.outstandingContributions);
	});

	test('a period keeps its own billed amounts after the price and membership change', () => {
		const { payments } = scenarioParts('FX-SUB-SHARED-POPULATED-01', 9401);
		const august = periodSummary({ records: payments, billingDate: '2026-08-01' });

		// August has one record at 99.67; today's price or a third Member must
		// not retroactively rewrite it.
		expect(august.billed).toBe(99.67);
		expect(august.collected).toBe(99.67);
		expect(august.outstanding).toBe(0);
		expect(august.memberRecords).toHaveLength(1);
	});

	test('an unreadable payments section is not an empty period', () => {
		const summary = periodSummary({ records: null, billingDate: '2026-09-01' });

		expect(summary.state).toBe('unavailable');
		expect(summary.collected).toBeNull();
		expect(summary.outstanding).toBeNull();
	});

	test('a loaded section with no records for the period says so without amounts', () => {
		const { payments } = scenarioParts('FX-SUB-SHARED-POPULATED-01', 9401);
		const summary = periodSummary({ records: payments, billingDate: '2026-07-01' });

		expect(summary.state).toBe('no-records');
		expect(summary.billed).toBeNull();
	});

	test('a paid record with no linked transaction is still received', () => {
		const summary = periodSummary({
			records: [
				{
					id: 1,
					memberId: 11,
					billingDate: '2026-09-01',
					amount: 120,
					status: 'PAID',
					paidDate: null,
					transactionId: null,
				},
			],
			billingDate: '2026-09-01',
		});

		expect(summary.memberRecords[0].status.state).toBe('received');
		expect(summary.collected).toBe(120);
		expect(summary.outstanding).toBe(0);
	});

	test('a pending record with a past billing date is awaiting, never overdue', () => {
		const summary = periodSummary({
			records: [
				{
					id: 1,
					memberId: 11,
					billingDate: '2026-01-01',
					amount: 80,
					status: 'PENDING',
					paidDate: null,
					transactionId: null,
				},
			],
			billingDate: '2026-01-01',
		});

		expect(summary.memberRecords[0].status.state).toBe('awaiting');
		expect(summary.outstanding).toBe(80);
	});

	test('an unreadable record is excluded from the totals and counted', () => {
		const summary = periodSummary({
			records: [
				{ id: 1, memberId: 11, billingDate: '2026-09-01', amount: 100, status: 'PAID', paidDate: null, transactionId: null },
				{ id: 2, memberId: 12, billingDate: '2026-09-01', amount: -40, status: 'PENDING', paidDate: null, transactionId: null },
				{ id: 3, memberId: 13, billingDate: '2026-09-01', amount: 50, status: 'WEIRD', paidDate: null, transactionId: null },
			],
			billingDate: '2026-09-01',
		});

		expect(summary.collected).toBe(100);
		expect(summary.outstanding).toBe(0);
		expect(summary.billed).toBe(100);
		expect(summary.unreadableCount).toBe(2);
		// The totals cover part of the period, and say so.
		expect(summary.partial).toBe(true);
	});

	test('a period whose records are all unreadable has no totals, not zero ones', () => {
		const summary = periodSummary({
			records: [
				{ id: 1, memberId: 11, billingDate: '2026-09-01', amount: -40, status: 'PENDING', paidDate: null, transactionId: null },
				{ id: 2, memberId: 12, billingDate: '2026-09-01', amount: 50, status: 'WEIRD', paidDate: null, transactionId: null },
			],
			billingDate: '2026-09-01',
		});

		// `0` here would report a period as billed nothing and collected nothing
		// because its records were malformed — a claim about the User's money
		// that the records never made.
		expect(summary.billed).toBeNull();
		expect(summary.collected).toBeNull();
		expect(summary.outstanding).toBeNull();
		expect(summary.unreadableCount).toBe(2);
		expect(summary.memberRecords).toHaveLength(2);
	});

	test('totals are exact at cents rather than drifting in binary floats', () => {
		const summary = periodSummary({
			records: [0.1, 0.2, 0.3, 10.07, 10.08].map((amount, index) => ({
				id: index + 1,
				memberId: 10 + index,
				billingDate: '2026-09-01',
				amount,
				status: 'PAID',
				paidDate: null,
				transactionId: null,
			})),
			billingDate: '2026-09-01',
		});

		expect(summary.collected).toBe(20.75);
		expect(summary.billed).toBe(20.75);
	});

	test('every own-share record is reported, and an unreadable one is not zero', () => {
		const summary = periodSummary({
			records: [
				{ id: 1, memberId: null, billingDate: '2026-09-01', amount: 149, status: 'PENDING', paidDate: null, transactionId: null },
				{ id: 2, memberId: null, billingDate: '2026-09-01', amount: null, status: 'PENDING', paidDate: null, transactionId: null },
			],
			billingDate: '2026-09-01',
		});

		// Reading only the first record would drop the second and round its
		// unreadable amount to 0.00.
		expect(summary.ownerRecords).toHaveLength(2);
		expect(summary.ownerRecords[0].status.amount).toBe(149);
		expect(summary.ownerRecords[1].status.amount).toBeNull();
		expect(summary.unreadableOwnerCount).toBe(1);
		// Own-share records are still not contributions, readable or not: the
		// period has no Member records, so nothing was expected from anyone.
		expect(summary.collected).toBe(0);
		expect(summary.unreadableCount).toBe(0);
	});

	test('a personal subscription’s own-share record is not a contribution', () => {
		const summary = periodSummary({
			records: [
				{ id: 1, memberId: null, billingDate: '2026-09-01', amount: 149, status: 'PENDING', paidDate: null, transactionId: null },
			],
			billingDate: '2026-09-01',
		});

		expect(summary.ownerRecords).toHaveLength(1);
		expect(summary.memberRecords).toHaveLength(0);
		expect(summary.collected).toBe(0);
		expect(summary.outstanding).toBe(0);
	});

	test('periods are listed newest first and only from loaded records', () => {
		const { payments } = scenarioParts('FX-SUB-SHARED-POPULATED-01', 9401);

		expect(periodsOf(payments)).toEqual(['2026-09-01', '2026-08-01']);
		expect(periodsOf(null)).toEqual([]);
	});
});

describe('price equivalents', () => {
	test('yearly plans count as a twelfth of the monthly equivalent', () => {
		const { routes, expected } = loadScenario('FX-SUB-LIST-01');
		const totals = priceEquivalents(routes['GET /api/subscriptions']);

		expect(totals.monthly).toBe(expected.monthlyEquivalent);
		expect(totals.yearly).toBe(expected.yearlyEquivalent);
		expect(totals.excluded).toBe(0);
	});

	test('an unreadable list yields no total rather than zero', () => {
		expect(priceEquivalents(null)).toBeNull();
	});

	test('a subscription with an unusable cycle is excluded and counted', () => {
		const totals = priceEquivalents([
			{ cost: 100, billingCycle: 'MONTHLY' },
			{ cost: 100, billingCycle: 'WEEKLY' },
		]);

		expect(totals.monthly).toBe(100);
		expect(totals.excluded).toBe(1);
	});

	test('the monthly figure is exactly a twelfth of the yearly one', () => {
		// 100.00 a year is 8.3333… a month. Summing floats and rounding each
		// figure independently let the two disagree; both now come from the same
		// exact cent total.
		const totals = priceEquivalents([
			{ cost: 100, billingCycle: 'YEARLY' },
			{ cost: 0.1, billingCycle: 'MONTHLY' },
			{ cost: 0.2, billingCycle: 'MONTHLY' },
		]);

		expect(totals.yearly).toBe(103.6);
		expect(totals.monthly).toBe(8.63);
		expect(totals.excluded).toBe(0);
	});
});

describe('generation cursor against the user’s calendar day', () => {
	const cursor = '2026-09-07';

	test('the same instant is due today in Mexico City and pending in Tokyo', () => {
		const mexico = billingGenerationStatus({
			nextBillingDate: cursor,
			today: userToday(MEXICO_CITY_EVENING.timeZone, instantOf(MEXICO_CITY_EVENING)),
		});
		const tokyo = billingGenerationStatus({
			nextBillingDate: cursor,
			today: userToday(TOKYO_SAME_INSTANT.timeZone, instantOf(TOKYO_SAME_INSTANT)),
		});

		expect(mexico.state).toBe('due-today');
		expect(tokyo.state).toBe('pending');
	});

	test('a future cursor is caught up and keeps its scheduled date', () => {
		const generation = billingGenerationStatus({
			nextBillingDate: '2026-09-15',
			today: userToday(MEXICO_CITY_EVENING.timeZone, instantOf(MEXICO_CITY_EVENING)),
		});

		expect(generation.state).toBe('caught-up');
		expect(generation.scheduledDate).toBe('2026-09-15');
	});

	test('an unusable zone yields no due status instead of a guessed one', () => {
		const generation = billingGenerationStatus({
			nextBillingDate: cursor,
			today: userToday('Not/AZone', instantOf(MEXICO_CITY_EVENING)),
		});

		expect(generation.state).toBe('unavailable');
	});
});

test('missing participation preserves the price but never guesses the split', () => {
	for (const ownerParticipates of [null, undefined, 'true']) {
		const result = currentSplit({ subscription: {cost: 600, type: 'SHARED', billingCycle:'MONTHLY', ownerParticipates}, members: [{id:1,shareAmount:300}] });
		expect(result.providerCost).toBe(600);
		expect(result.state).toBe('unavailable');
		expect(result.ownShare).toBeNull();
		expect(result.expectedContributions).toBeNull();
	}
});
