// @ts-nocheck
/**
 * Slice 1A-balance / 3A: what the balance and credit figures mean (decision D1).
 *
 * The fixtures supply the figures; these tests check the explanations attached
 * to them — above all that no explanation is ever produced by comparing totals
 * to each other, and that a failed read never becomes a zero.
 */

import { describe, expect, test } from 'bun:test';
import {
	availableCredit,
	availableToSpendExplanation,
	creditMagnitude,
	creditPosition,
	netBalanceSource,
} from '../src/lib/balance-presentation';
import { loadScenario } from './fixtures/scenarios';

function totalsOf(scenarioId) {
	const summary = loadScenario(scenarioId).routes['GET /api/boxes/summary'];
	return {
		netBalance: summary.netBalance,
		inBoxes: summary.inBoxes,
		availableToSpend: summary.availableToSpend,
	};
}

describe('net balance source', () => {
	test('tracking inactive explains the Transaction formula', () => {
		const status = loadScenario('FX-TRACK-INACTIVE-01').routes['GET /api/accounts/status'];
		expect(netBalanceSource(status)).toBe('transactions');
	});

	test('tracking active explains the Financial Account ledger', () => {
		const status = loadScenario('FX-TRACK-ACTIVE-01').routes['GET /api/accounts/status'];
		expect(netBalanceSource(status)).toBe('accounts');
	});

	test('an unavailable or malformed status is unknown, never a guessed mode', () => {
		expect(netBalanceSource(null)).toBe('unknown');
		expect(netBalanceSource({})).toBe('unknown');
		expect(netBalanceSource({ active: 'true' })).toBe('unknown');
		expect(netBalanceSource({ active: 1 })).toBe('unknown');
	});

	test('the mode is never inferred from the totals agreeing or disagreeing', () => {
		// Identical totals with tracking off must still read as Transactions,
		// and differing totals with tracking on must still read as Accounts.
		expect(netBalanceSource({ active: false, transactionNetBalance: 500, accountNetBalance: 500 })).toBe(
			'transactions',
		);
		expect(netBalanceSource({ active: true, transactionNetBalance: 1_500, accountNetBalance: 2_500.75 })).toBe(
			'accounts',
		);
		// A zero ledger with tracking on is still the ledger.
		expect(netBalanceSource({ active: true, accountNetBalance: 0 })).toBe('accounts');
	});
});

describe('available to spend explanation', () => {
	test('a healthy position is reconciled', () => {
		const result = availableToSpendExplanation(totalsOf('FX-TRACK-ACTIVE-01'));
		expect(result.state).toBe('reconciled');
		expect(result.shortfall).toBe(0);
	});

	test('a legitimate zero is reconciled, and is not a failed read', () => {
		expect(availableToSpendExplanation(totalsOf('FX-BAL-ZERO-01')).state).toBe('reconciled');
		expect(availableToSpendExplanation(null).state).toBe('unavailable');
		expect(availableToSpendExplanation(null).shortfall).toBeNull();
	});

	test('positive net with excessive reservations is over-reservation', () => {
		const result = availableToSpendExplanation(totalsOf('FX-BAL-OVERRESERVED-01'));
		expect(result.state).toBe('over-reserved');
		expect(result.shortfall).toBe(760);
		expect(result.withdrawalPossible).toBe(true);
	});

	test('a negative Net Balance is a different story from over-reserving', () => {
		const result = availableToSpendExplanation(totalsOf('FX-BAL-NEGNET-01'));
		expect(result.state).toBe('negative-net');
		expect(result.shortfall).toBe(750);
		// Nothing is reserved, so withdrawing from a Box cannot resolve it.
		expect(result.withdrawalPossible).toBe(false);
	});

	test('no withdrawal is suggested when every Box is empty', () => {
		const scenario = loadScenario('FX-BOX-EMPTY-NEGAVAIL-01');
		const boxes = scenario.routes['GET /api/boxes'];
		const emptyBox = boxes.find((box) => box.id === scenario.expected.emptyBoxId);
		expect(emptyBox.balance).toBe(0);

		// The whole overview: one Box does hold money, so withdrawal is offered.
		const overview = availableToSpendExplanation(totalsOf('FX-BOX-EMPTY-NEGAVAIL-01'), {
			boxBalances: boxes.map((box) => box.balance),
		});
		expect(overview.state).toBe('over-reserved');
		expect(overview.withdrawalPossible).toBe(true);

		// The empty Box's own detail page must not offer to withdraw from it.
		const detail = availableToSpendExplanation(totalsOf('FX-BOX-EMPTY-NEGAVAIL-01'), {
			boxBalances: [emptyBox.balance],
		});
		expect(detail.withdrawalPossible).toBe(false);
	});

	test('the Phase 4 dashboard figures reconcile and explain as over-reservation', () => {
		const expected = loadScenario('FX-DASH-NEG-01').expected;
		expect(expected.moneyHeld + expected.creditInFavor).toBe(expected.netBalance);
		expect(expected.netBalance - expected.inBoxes).toBe(expected.availableToSpend);

		const result = availableToSpendExplanation(totalsOf('FX-DASH-NEG-01'));
		expect(result.state).toBe('over-reserved');
		expect(result.shortfall).toBe(1_124);
		// The 310.25 confirmed statement payment is never folded into this.
		expect(result.shortfall).not.toBe(1_124 + expected.outstandingStatementPayment);
	});

	test('the negative-net magnitude is the Net Balance, not the Available shortfall', () => {
		// Net −200 with 50 reserved leaves Available at −250. The recorded
		// position is 200 below zero; saying 250 would charge the reservation to
		// the ledger and overstate what the User actually owes against.
		const result = availableToSpendExplanation({
			netBalance: -200,
			inBoxes: 50,
			availableToSpend: -250,
		});
		expect(result.state).toBe('negative-net');
		expect(result.negativeNet).toBe(200);
		expect(result.shortfall).toBe(250);
	});

	test('a non-negative Net Balance has no negative-net magnitude to print', () => {
		const overReserved = availableToSpendExplanation(totalsOf('FX-BAL-OVERRESERVED-01'));
		expect(overReserved.state).toBe('over-reserved');
		expect(overReserved.negativeNet).toBeNull();
		expect(availableToSpendExplanation(totalsOf('FX-BAL-ZERO-01')).negativeNet).toBeNull();
		// A failed read is not a zero here either.
		expect(availableToSpendExplanation(null).negativeNet).toBeNull();
	});

	test('with nothing in Boxes the two magnitudes coincide', () => {
		const result = availableToSpendExplanation(totalsOf('FX-BAL-NEGNET-01'));
		expect(result.negativeNet).toBe(result.shortfall);
	});

	test('box balances are only consulted when supplied', () => {
		expect(availableToSpendExplanation(totalsOf('FX-TRACK-ACTIVE-01')).withdrawalPossible).toBeNull();
	});
});

describe('credit labels', () => {
	test('the signed balance decides the label', () => {
		expect(creditPosition(-900)).toBe('debt');
		expect(creditPosition(85)).toBe('in-favor');
		expect(creditPosition(0)).toBe('settled');
		expect(creditPosition(null)).toBe('unknown');
		expect(creditPosition('85')).toBe('unknown');
	});

	test('debt is displayed as a magnitude', () => {
		expect(creditMagnitude(-1_124.5)).toBe(1_124.5);
		expect(creditMagnitude(undefined)).toBeNull();
	});

	test('available credit is limit-derived and stays out of the totals', () => {
		const scenario = loadScenario('FX-CREDIT-INFAVOR-STMT-01');
		const settings = scenario.routes['GET /api/accounts/9112/credit-settings'];
		const credit = scenario.routes['GET /api/accounts'].find((account) => account.id === 9112);

		expect(availableCredit(settings.creditLimit, credit.balance)).toBe(
			scenario.expected.availableCredit,
		);
		// Net Balance includes the signed credit balance but never the limit.
		expect(scenario.expected.netBalance).toBe(1_000 + scenario.expected.creditInFavor);
	});

	test('no configured limit means unknown available credit, not zero', () => {
		expect(availableCredit(null, 85)).toBeNull();
		expect(availableCredit(undefined, 85)).toBeNull();
		expect(availableCredit(12_000, null)).toBeNull();
	});

	test('available credit never goes below zero when the limit is breached', () => {
		expect(availableCredit(5_000, -6_000)).toBe(0);
	});
});
