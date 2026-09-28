// @ts-nocheck
/**
 * Slice 5C: the fixture stand-in for `POST /api/planning/preview`.
 *
 * The page is verified in a browser against this stand-in, so it must agree
 * with D5 wherever D5 is explicit. These tests pin it to the approved worked
 * examples (the same figures as the backend's `PlanningPreviewFixtures`) and
 * to the contract's invariants, and check that every answer parses with the
 * page's own parser.
 */

import { describe, expect, test } from 'bun:test';
import { createFixtureBackend } from './fixtures/backend';
import { HORIZON_EXAMPLE_REQUESTS, loadScenario } from './fixtures/scenarios';
import { parsePreviewResponse } from '../src/lib/planning-preview';

async function preview(scenarioId, request, options = {}) {
	const backend = createFixtureBackend(scenarioId, options);
	const response = await backend.fetch('http://fixture/api/planning/preview', {
		method: 'POST',
		body: typeof request === 'string' ? request : JSON.stringify(request),
	});
	backend.assertNoUndeclaredRoutes();
	const body = await response.json();
	if (response.status === 200) {
		// Every 200 the stand-in produces must be one the page can read.
		expect(parsePreviewResponse(body)).not.toBeNull();
		expectLedgerIdentity(body);
	}
	return { status: response.status, body };
}

/** U' = N' − B' and U = N − B, to the cent, whenever a figure is present. */
function expectLedgerIdentity(body) {
	for (const totals of [body.baseline, body.projected]) {
		if (totals === null) continue;
		expect(Math.round(totals.netBalance * 100) - Math.round(totals.inBoxes * 100)).toBe(
			Math.round(totals.availableToSpend * 100),
		);
	}
}

function example(id) {
	return HORIZON_EXAMPLE_REQUESTS[id];
}

function cost(amount, date, extra = {}) {
	return { amount, date, description: null, boxId: null, boxAmount: null, notYetRecordedConfirmed: true, ...extra };
}

function request(items = [], expectedReceipts = [], essentialsReviewed = true) {
	return { horizonDays: 30, essentialsReviewed, items, expectedReceipts };
}

describe('approved D5 worked examples', () => {
	test('every example starts from the same invented baseline', () => {
		for (const id of Object.keys(HORIZON_EXAMPLE_REQUESTS)) {
			const expected = loadScenario(id.split('/')[0]).expected;
			expect(expected.netBalance - expected.inBoxes).toBe(expected.availableToSpend);
		}
	});

	test('ex.1 a cash cost lowers Net Balance and Available to Spend by its amount', async () => {
		const { body } = await preview('FX-HORIZON-CASH-01', example('FX-HORIZON-CASH-01'));
		const expected = loadScenario('FX-HORIZON-CASH-01').expected;
		expect(body.window).toEqual({ from: '2026-09-20', to: '2026-10-19' });
		expect(body.status).toBe(expected.status);
		expect(body.projected).toMatchObject({
			netBalance: expected.projectedNetBalance,
			inBoxes: expected.projectedInBoxes,
			availableToSpend: expected.projectedAvailableToSpend,
		});
	});

	test('ex.2 a fully Box-funded cost leaves Available to Spend unchanged', async () => {
		const { body } = await preview('FX-HORIZON-BOXFUNDED-01', example('FX-HORIZON-BOXFUNDED-01'));
		const expected = loadScenario('FX-HORIZON-BOXFUNDED-01').expected;
		expect(body.projected.availableToSpend).toBe(expected.availableToSpend);
		expect(body.projected.availableToSpend).toBe(expected.projectedAvailableToSpend);
		expect(body.projected.perBox.find((box) => box.boxId === 9240).projectedBalance).toBe(0);
	});

	test('ex.3 funding beyond the Box is refused with its shortfall, and no figure', async () => {
		const expected = loadScenario('FX-HORIZON-BOXSHORT-01').expected;
		const refused = await preview('FX-HORIZON-BOXSHORT-01', example('FX-HORIZON-BOXSHORT-01'));
		expect(refused.body.status).toBe('unavailable');
		expect(refused.body.projected).toBeNull();
		expect(refused.body.missingInputs).toEqual([
			{ reason: 'BOX_CAPACITY_EXCEEDED', itemIndex: null, receiptIndex: null, boxId: 9240, shortfall: expected.shortfall },
		]);

		const reduced = await preview('FX-HORIZON-BOXSHORT-01', example('FX-HORIZON-BOXSHORT-01/reduced'));
		expect(reduced.body.projected).toMatchObject({
			netBalance: expected.projectedNetBalance,
			inBoxes: expected.projectedInBoxes,
			availableToSpend: expected.projectedAvailableToSpend,
		});
	});

	test('ex.3 capacity is cumulative across rows funded from the same Box', async () => {
		const { body } = await preview(
			'FX-HORIZON-BOXSHORT-01',
			request([
				cost('1500', '2026-10-01', { boxId: 9240, boxAmount: '1500' }),
				cost('800', '2026-10-05', { boxId: 9240, boxAmount: '800' }),
			]),
		);
		expect(body.missingInputs).toEqual([
			expect.objectContaining({ reason: 'BOX_CAPACITY_EXCEEDED', boxId: 9240, shortfall: 300 }),
		]);
	});

	test('ex.4 a confirmed statement is listed in the window and deducted from nothing', async () => {
		const expected = loadScenario('FX-HORIZON-STMT-NEUTRAL-01').expected;
		const { body } = await preview('FX-HORIZON-STMT-NEUTRAL-01', example('FX-HORIZON-STMT-NEUTRAL-01'));
		expect(body.projected.availableToSpend).toBe(expected.projectedAvailableToSpend);
		expect(body.timing.dated).toEqual([
			expect.objectContaining({ dueDate: expected.statementDue, outstandingBalance: expected.statementOutstanding }),
		]);
	});

	test('ex.11 a recorded future-dated EGRESS is not forecast again', async () => {
		const expected = loadScenario('FX-HORIZON-FUTURE-RECORDED-01').expected;
		const { body } = await preview('FX-HORIZON-FUTURE-RECORDED-01', example('FX-HORIZON-FUTURE-RECORDED-01'));
		expect(body.projected.netBalance).toBe(expected.projectedNetBalance);
		expect(body.projected.availableToSpend).toBe(expected.projectedAvailableToSpend);
		expect(body.notes).toContain('LEDGER_TOTAL_NOT_CASH');
	});

	test('ex.8 income is excluded by default and, opted in, raises both totals if received', async () => {
		const expected = loadScenario('FX-HORIZON-INCOME-OPTIN-01').expected;
		const off = await preview('FX-HORIZON-INCOME-OPTIN-01', example('FX-HORIZON-INCOME-OPTIN-01'));
		expect(off.body.projected.availableToSpend).toBe(expected.projectedAvailableToSpend);
		expect(off.body.includedReceipts).toEqual([]);

		const on = await preview('FX-HORIZON-INCOME-OPTIN-01', example('FX-HORIZON-INCOME-OPTIN-01/opted-in'));
		expect(on.body.projected.netBalance).toBe(expected.optedInNetBalance);
		expect(on.body.projected.availableToSpend).toBe(expected.optedInAvailableToSpend);
		expect(on.body.includedReceipts).toEqual([
			{ receiptIndex: 0, recordKind: 'DEBT', recordId: expected.debtId, amount: 4_500, date: '2026-10-10' },
		]);
		expect(on.body.notes).toContain('RECEIPTS_IF_RECEIVED');
	});

	test('ex.9 unreviewed essentials give a partial subtotal with the reason, never complete', async () => {
		const expected = loadScenario('FX-HORIZON-PARTIAL-01').expected;
		const { body } = await preview('FX-HORIZON-PARTIAL-01', example('FX-HORIZON-PARTIAL-01'));
		expect(body.status).toBe(expected.status);
		expect(body.projected.availableToSpend).toBe(expected.projectedAvailableToSpend);
		expect(body.missingInputs.map((input) => input.reason)).toEqual(['ESSENTIALS_NOT_REVIEWED']);
	});
});

describe('receipts are resolved by the server, not the page', () => {
	test('the server’s own amount is used; a client-supplied amount is ignored', async () => {
		const { body } = await preview(
			'FX-HORIZON-UI-01',
			request([], [{ recordId: 9443, recordKind: 'PAYMENT_RECORD', date: '2026-10-03', amount: 999_999 }]),
		);
		expect(body.includedReceipts[0].amount).toBe(133.33);
	});

	test('PAID without a Transaction, the Owner’s own charge and EGRESS Debts are ineligible', async () => {
		const receipts = [
			{ recordId: 9446, recordKind: 'PAYMENT_RECORD', date: '2026-10-03' },
			{ recordId: 9445, recordKind: 'PAYMENT_RECORD', date: '2026-10-03' },
			{ recordId: 9448, recordKind: 'PAYMENT_RECORD', date: '2026-10-03' },
			{ recordId: 9922, recordKind: 'DEBT', date: '2026-10-03' },
			{ recordId: 9923, recordKind: 'DEBT', date: '2026-10-03' },
		];
		const { body } = await preview('FX-HORIZON-UI-01', request([], receipts));
		expect(body.status).toBe('unavailable');
		expect(body.projected).toBeNull();
		expect(body.missingInputs.map((input) => [input.reason, input.receiptIndex])).toEqual(
			receipts.map((_, index) => ['RECEIPT_INELIGIBLE', index]),
		);
	});

	test('unknown and duplicate receipts are named by row', async () => {
		const { body } = await preview(
			'FX-HORIZON-UI-01',
			request([], [
				{ recordId: 9920, recordKind: 'DEBT', date: '2026-10-03' },
				{ recordId: 9920, recordKind: 'DEBT', date: '2026-10-04' },
				{ recordId: 424242, recordKind: 'DEBT', date: '2026-10-03' },
			]),
		);
		expect(body.missingInputs).toEqual(
			expect.arrayContaining([
				expect.objectContaining({ reason: 'DUPLICATE_RECEIPT', receiptIndex: 1 }),
				expect.objectContaining({ reason: 'RECEIPT_NOT_FOUND', receiptIndex: 2 }),
			]),
		);
		expect(body.projected).toBeNull();
	});

	test('a change after the catalog was read is reported per row (stale scenario)', async () => {
		const { body } = await preview(
			'FX-HORIZON-STALE-01',
			request([], [
				{ recordId: 9920, recordKind: 'DEBT', date: '2026-10-03' },
				{ recordId: 9444, recordKind: 'PAYMENT_RECORD', date: '2026-10-03' },
				{ recordId: 9443, recordKind: 'PAYMENT_RECORD', date: '2026-10-03' },
			]),
		);
		expect(body.missingInputs.map((input) => [input.reason, input.receiptIndex])).toEqual([
			['RECEIPT_INELIGIBLE', 0],
			['RECEIPT_NOT_FOUND', 1],
		]);
		expect(body.includedReceipts).toEqual([]);
	});
});

describe('row validation and statuses', () => {
	test('business-invalid rows make the projection unavailable, each reason naming its row', async () => {
		const { body } = await preview(
			'FX-HORIZON-UI-01',
			request([
				cost('0', '2026-10-01'),
				cost('10', '2026-12-01'),
				cost('1.005', '2026-10-01'),
				cost('10', '2026-10-01', { boxAmount: '5' }),
				cost('10', '2026-10-01', { boxId: 9240, boxAmount: '20' }),
				cost('10', '2026-10-01', { boxId: 424242, boxAmount: '5' }),
			]),
		);
		expect(body.status).toBe('unavailable');
		expect(body.projected).toBeNull();
		expect(body.missingInputs.map((input) => [input.reason, input.itemIndex])).toEqual([
			['INVALID_AMOUNT', 0],
			['DATE_OUT_OF_WINDOW', 1],
			['INVALID_AMOUNT', 2],
			['BOX_REQUIRED', 3],
			['FUNDING_EXCEEDS_COST', 4],
			['BOX_NOT_FOUND', 5],
		]);
		// Timing and undated Debts are still evaluated.
		expect(body.timing.status).toBe('partial');
		expect(body.undatedDebts).not.toBeNull();
	});

	test('complete only when every cost is confirmed unrecorded and essentials are reviewed', async () => {
		const unconfirmed = await preview(
			'FX-HORIZON-UI-01',
			request([cost('10', '2026-10-01'), cost('20', '2026-10-02', { notYetRecordedConfirmed: false })]),
		);
		expect(unconfirmed.body.status).toBe('partial');
		expect(unconfirmed.body.missingInputs).toEqual([
			expect.objectContaining({ reason: 'COST_NOT_CONFIRMED_UNRECORDED', itemIndex: 1 }),
		]);
		const complete = await preview('FX-HORIZON-UI-01', request([cost('10', '2026-10-01')]));
		expect(complete.body.status).toBe('complete');
		expect(complete.body.missingInputs).toEqual([]);
	});

	test('fifty costs are accepted; fifty-one, a wrong horizon or an unreadable amount are 400s', async () => {
		const fifty = Array.from({ length: 50 }, () => cost('1', '2026-10-01'));
		expect((await preview('FX-HORIZON-UI-01', request(fifty))).status).toBe(200);
		expect((await preview('FX-HORIZON-UI-01', request([...fifty, cost('1', '2026-10-01')]))).status).toBe(400);
		expect((await preview('FX-HORIZON-UI-01', { ...request(), horizonDays: 31 })).status).toBe(400);
		expect((await preview('FX-HORIZON-UI-01', request([cost('mucho', '2026-10-01')]))).status).toBe(400);
		expect((await preview('FX-HORIZON-UI-01', request([{ ...cost('1', '2026-10-01'), date: undefined }]))).status).toBe(400);
		expect((await preview('FX-HORIZON-UI-01', '{not json')).status).toBe(400);
	});
});

describe('independent sections', () => {
	test('overdue statements stay visible, later ones are omitted, and nothing is netted', async () => {
		const { body } = await preview('FX-HORIZON-UI-01', request());
		expect(body.timing.overdue.map((row) => row.statementId)).toEqual([9311]);
		expect(body.timing.dated.map((row) => row.statementId)).toEqual([9310]);
		expect(body.timing.reasons.map((reason) => reason.code).sort()).toEqual([
			'RECONCILIATION_MISMATCH',
			'UNCONFIRMED_STATEMENT',
		]);
		expect(body.projected.availableToSpend).toBe(body.baseline.availableToSpend);
	});

	test('undated Debts in both Directions are listed per Debt, including the same Contact', async () => {
		const { body } = await preview('FX-HORIZON-UI-01', request());
		const byContact = body.undatedDebts.filter((debt) => debt.contactId === 9521);
		expect(byContact.map((debt) => [debt.direction, debt.remaining])).toEqual([
			['INGRESS', 300],
			['EGRESS', 500],
		]);
		expect(body.undatedDebts.some((debt) => debt.debtId === 9923)).toBe(false);
	});

	test('credit in favor is reported as part of the baseline, not cash', async () => {
		const { body } = await preview('FX-HORIZON-UI-01', request());
		expect(body.baseline.creditInFavor).toBe(loadScenario('FX-HORIZON-UI-01').expected.creditInFavor);
		expect(body.notes).toContain('CREDIT_IN_FAVOR_IN_BASELINE');
	});

	test('failed timing and Debt reads leave the projection intact', async () => {
		const { body } = await preview('FX-HORIZON-SECTIONS-DOWN-01', request([cost('10', '2026-10-01')]));
		expect(body.status).toBe('complete');
		expect(body.timing).toMatchObject({ status: 'unavailable', dated: [], overdue: [] });
		expect(body.undatedDebts).toBeNull();
		expect(body.undatedDebtsStatus).toBe('unavailable');
	});

	test('a failed balance read leaves timing and Debts intact and shows no figure', async () => {
		const { body } = await preview('FX-HORIZON-BASELINE-DOWN-01', request([cost('10', '2026-10-01')]));
		expect(body.baseline).toBeNull();
		expect(body.projected).toBeNull();
		expect(body.missingInputs.map((input) => input.reason)).toEqual(['BASELINE_UNAVAILABLE']);
		expect(body.timing.dated).toHaveLength(1);
		expect(body.undatedDebts).not.toBeNull();
	});

	test('before tracking, timing is not applicable rather than failed', async () => {
		const { body } = await preview('FX-HORIZON-TRACKING-OFF-01', request());
		expect(body.timing.status).toBe('notApplicable');
		expect(body.baseline.source).toBe('transactions');
		expect(body.baseline.creditInFavor).toBeNull();
	});

	test('an unusable zone has no window and is never replaced by UTC', async () => {
		const { body } = await preview('FX-HORIZON-ZONE-01', request([cost('10', '2026-10-01')]));
		expect(body.window).toBeNull();
		expect(body.timeZone).toBeNull();
		expect(body.projected).toBeNull();
		expect(body.missingInputs.map((input) => input.reason)).toEqual(['ZONE_UNAVAILABLE']);
	});

	test('the window follows the clock the request is answered at', async () => {
		const late = await preview('FX-HORIZON-UI-01', request(), { now: new Date('2026-09-21T05:59:00Z') });
		const next = await preview('FX-HORIZON-UI-01', request(), { now: new Date('2026-09-21T06:00:00Z') });
		expect(late.body.window.from).toBe('2026-09-20');
		expect(next.body.window.from).toBe('2026-09-21');
	});
});
