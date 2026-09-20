// @ts-nocheck
/**
 * What a dashboard section is entitled to claim.
 *
 * Every regression here is the same mistake in a different section: treating
 * "we found nothing" as "there is nothing", or narrating a total with only some
 * of the terms that went into it. Both produce a page that is confidently
 * wrong about the User's money, which is worse than one that admits a gap.
 *
 * These drive the predicates the sections actually render from, so a component
 * that goes back to an inline `items.length === 0` fails here.
 */

import { describe, expect, test } from 'bun:test';
import {
	attentionEmptiness,
	creditTerms,
	expectedEmptiness,
	plansEmptiness,
	plansTotalsAreComplete,
} from '../src/lib/dashboard-sections';
import { loadScenario } from './fixtures/scenarios';

function position(values = {}) {
	return {
		trackingActive: true,
		setupRequired: false,
		moneyHeld: 1_000,
		creditDebt: 0,
		creditInFavor: 0,
		netBalance: 1_000,
		inBoxes: 0,
		availableToSpend: 1_000,
		availableCredit: null,
		creditLimitsPartial: false,
		accounts: [],
		...values,
	};
}

function plans(values = {}) {
	return {
		inBoxes: 0,
		reservedInPlannedBoxes: 0,
		items: [],
		boxesWithoutActivePlan: 0,
		partial: false,
		...values,
	};
}

function expected(values = {}) {
	return {
		debtsOutstanding: 0,
		debtCount: 0,
		debtsTruncated: false,
		debts: [],
		contributionsAvailable: true,
		contributionsOutstanding: 0,
		contributionCount: 0,
		contributionsTruncated: false,
		contributions: [],
		partial: false,
		...values,
	};
}

describe('the Net Balance sentence names every credit term inside it', () => {
	// The regression: one card owed while another is overpaid. Net Balance
	// contains both, so "held minus debt = net" is wrong by exactly the credit
	// in the User's favour — the numbers on screen visibly fail to add up.
	test('debt and credit in favor together are a mixed sentence, not a debt one', () => {
		expect(creditTerms(position({ creditDebt: 900, creditInFavor: 55.5 }))).toBe('mixed');
	});

	test('one term alone still gets its own sentence', () => {
		expect(creditTerms(position({ creditDebt: 900 }))).toBe('debt');
		expect(creditTerms(position({ creditInFavor: 55.5 }))).toBe('in-favor');
		expect(creditTerms(position())).toBe('settled');
	});

	// Before tracking is activated there are no signed account balances to
	// split, and a zeroed breakdown would be a guess (decision D1).
	test('no breakdown is narrated before tracking is activated', () => {
		expect(
			creditTerms(position({ trackingActive: false, moneyHeld: null, creditDebt: null, creditInFavor: null })),
		).toBeNull();
	});
});

describe('emptiness is only claimed from a complete read', () => {
	test('no plans read and no plans found are different answers', () => {
		expect(plansEmptiness(plans())).toBe('empty');
		// A Box whose plan failed to load may well have the plan the User is
		// about to be invited to create.
		expect(plansEmptiness(plans({ partial: true }))).toBe('unknown');
	});

	test('reserved money is a subtotal while any plan is unread', () => {
		expect(plansTotalsAreComplete(plans({ items: [{}], reservedInPlannedBoxes: 500 }))).toBe(true);
		expect(
			plansTotalsAreComplete(plans({ items: [{}], reservedInPlannedBoxes: 500, partial: true })),
		).toBe(false);
	});

	test('a failed contribution read is not "nobody owes you anything"', () => {
		expect(expectedEmptiness(expected())).toBe('empty');
		expect(
			expectedEmptiness(expected({ contributionsAvailable: false, contributionsOutstanding: null })),
		).toBe('unknown');
		expect(expectedEmptiness(expected({ partial: true }))).toBe('unknown');
	});

	test('found money is populated whether or not the read was complete', () => {
		expect(expectedEmptiness(expected({ debtCount: 1, debtsOutstanding: 900 }))).toBe('populated');
		expect(
			expectedEmptiness(
				expected({ debtCount: 1, contributionsAvailable: false, contributionsOutstanding: null }),
			),
		).toBe('populated');
	});

	test('attention cannot report a clean bill without the reads behind it', () => {
		const clean = { alertCount: 0, partial: false, positionAvailable: true };
		expect(attentionEmptiness(clean)).toBe('empty');
		// No position means no account balance or credit limit was checked, so no
		// overdrawn or over-limit alert could have been raised at all.
		expect(attentionEmptiness({ ...clean, positionAvailable: false })).toBe('unknown');
		// A card whose statements failed may owe money nobody can see.
		expect(attentionEmptiness({ ...clean, partial: true })).toBe('unknown');
		expect(attentionEmptiness({ ...clean, alertCount: 2 })).toBe('populated');
	});
});

describe('the fixture year is internally coherent', () => {
	/**
	 * A flat zero month series under a non-zero annual total renders a year with
	 * income above two charts that both say there are no transactions yet. Any
	 * chart assertion written against that fixture proves nothing, so the months
	 * have to reconcile to the totals printed beside them.
	 */
	test('every scenario’s months add up to the annual totals it reports', () => {
		const ids = ['FX-DASH-NEG-01'];
		for (const id of ids) {
			const history = loadScenario(id).routes['GET /api/dashboard/overview'].history.data;
			const sum = (key) =>
				Math.round(history.monthly.reduce((total, month) => total + month[key], 0) * 100) / 100;

			expect(history.monthly).toHaveLength(12);
			expect(sum('ingress')).toBe(history.totalIngress);
			expect(sum('egress')).toBe(history.totalEgress);
		}
	});

	test('a year with recorded activity does not render as an empty chart', () => {
		const history = loadScenario('FX-DASH-NEG-01').routes['GET /api/dashboard/overview'].history
			.data;
		expect(history.totalIngress).toBeGreaterThan(0);
		expect(history.monthly.some((month) => month.ingress > 0)).toBe(true);
	});
});
