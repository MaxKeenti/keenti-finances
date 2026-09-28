// @ts-nocheck
/**
 * Slice 5C: the planning preview's draft, request, response and staleness
 * rules (`src/lib/planning-preview.ts`).
 *
 * These assert invariants — what may be sent, what may be shown, and which
 * response may reach the screen — rather than copy. Response bodies come from
 * the fixture stand-in for the endpoint, so the parser is exercised against
 * the same envelope the browser checks use.
 */

import { describe, expect, test } from 'bun:test';
import { readFileSync } from 'node:fs';
import {
	MAX_PLANNING_COSTS,
	MAX_PLANNING_RECEIPTS,
	PREVIEW_REQUEST_FIELDS,
	PREVIEW_RESPONSE_FIELDS,
	PlanningPreviewSession,
	addCalendarDays,
	addCost,
	addReceipt,
	buildPreviewRequest,
	emptyDraft,
	groupMissingInputs,
	invalidCostFields,
	parsePreviewResponse,
	planningWindow,
	reconcileReceipts,
	removeCost,
	resetConfirmations,
	updateCost,
	updateReceiptDate,
	validateDraft,
	withdrawForStaleness,
} from '../src/lib/planning-preview';
import { userToday } from '../src/lib/obligation-status';
import { MEXICO_CITY_EVENING, TOKYO_SAME_INSTANT, instantOf } from './fixtures/clock';
import { createFixtureBackend } from './fixtures/backend';

const JAVA = new URL('../../backend/src/main/java/com/keenti/finances/', import.meta.url);

/** A draft with one complete cost and one dated receipt. */
function readyDraft() {
	let draft = addCost(emptyDraft());
	const key = draft.costs[0].key;
	draft = updateCost(draft, key, { amount: '1200.10', date: '2026-10-02', notYetRecordedConfirmed: true });
	draft = addReceipt(draft, 'DEBT', 9920);
	draft = updateReceiptDate(draft, draft.receipts[0].key, '2026-10-10');
	return { ...draft, essentialsReviewed: true };
}

function deferred() {
	let resolve;
	let reject;
	const promise = new Promise((res, rej) => {
		resolve = res;
		reject = rej;
	});
	return { promise, resolve, reject };
}

function jsonResponse(body, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

/** A preview body from the fixture stand-in, at the D5 clock. */
async function fixtureBody(scenarioId, draft) {
	const backend = createFixtureBackend(scenarioId);
	const response = await backend.fetch('http://fixture/api/planning/preview', {
		method: 'POST',
		body: JSON.stringify(buildPreviewRequest(draft)),
	});
	return response.json();
}

/**
 * A fetch whose responses the test releases by hand, in any order. The
 * signal is recorded but deliberately *not* honoured, so a test proves the
 * session discards stale responses even when a transport ignores the abort.
 */
function manualFetch() {
	const calls = [];
	const fetchFn = (url, init) => {
		const call = { url, init, ...deferred() };
		calls.push(call);
		return call.promise;
	};
	return { fetchFn, calls };
}

describe('planning window', () => {
	test('is thirty calendar days starting on the User’s own today', () => {
		const window = planningWindow(userToday('America/Mexico_City', instantOf(MEXICO_CITY_EVENING)));
		expect(window).toEqual({ from: '2026-09-07', to: '2026-10-06' });
	});

	test('the same instant gives a different window in another zone', () => {
		const mexico = planningWindow(userToday(MEXICO_CITY_EVENING.timeZone, instantOf(MEXICO_CITY_EVENING)));
		const tokyo = planningWindow(userToday(TOKYO_SAME_INSTANT.timeZone, instantOf(TOKYO_SAME_INSTANT)));
		expect(tokyo.from).toBe(addCalendarDays(mexico.from, 1));
	});

	test('an unusable zone has no window rather than a UTC one', () => {
		expect(planningWindow(userToday('Mars/Olympus_Mons', new Date()))).toBeNull();
		expect(planningWindow(userToday(undefined, new Date()))).toBeNull();
	});

	test('calendar arithmetic crosses month, year and leap-day boundaries', () => {
		expect(addCalendarDays('2026-12-15', 29)).toBe('2027-01-13');
		expect(addCalendarDays('2028-02-15', 29)).toBe('2028-03-15');
		expect(addCalendarDays('2026-03-01', -1)).toBe('2026-02-28');
	});
});

describe('draft', () => {
	test('costs and receipts stop at fifty rows each', () => {
		let draft = emptyDraft();
		for (let i = 0; i < MAX_PLANNING_COSTS + 5; i += 1) draft = addCost(draft);
		for (let i = 0; i < MAX_PLANNING_RECEIPTS + 5; i += 1) draft = addReceipt(draft, 'DEBT', 1000 + i);
		expect(draft.costs).toHaveLength(MAX_PLANNING_COSTS);
		expect(draft.receipts).toHaveLength(MAX_PLANNING_RECEIPTS);
		expect(validateDraft(draft, new Set()).costs.size).toBe(MAX_PLANNING_COSTS);
	});

	test('row keys stay unique after removals, so focus and keyed rows cannot collide', () => {
		let draft = addCost(addCost(addCost(emptyDraft())));
		draft = removeCost(draft, draft.costs[1].key);
		draft = addCost(draft);
		const keys = draft.costs.map((cost) => cost.key);
		expect(new Set(keys).size).toBe(keys.length);
	});

	test('the same receipt cannot be selected twice', () => {
		const once = addReceipt(emptyDraft(), 'PAYMENT_RECORD', 9443);
		expect(addReceipt(once, 'PAYMENT_RECORD', 9443)).toBe(once);
		expect(addReceipt(once, 'DEBT', 9443).receipts).toHaveLength(2);
	});

	test('a newly selected receipt has no date until the User types one', () => {
		const draft = addReceipt(emptyDraft(), 'DEBT', 9920);
		expect(draft.receipts[0].date).toBe('');
		expect(validateDraft(draft, new Set()).ready).toBe(false);
	});

	test('choosing no Box drops the Box amount with it', () => {
		let draft = addCost(emptyDraft());
		const key = draft.costs[0].key;
		draft = updateCost(draft, key, { boxId: '9240', boxAmount: '500' });
		draft = updateCost(draft, key, { boxId: '' });
		expect(draft.costs[0].boxAmount).toBe('');
		expect(buildPreviewRequest(draft).items[0]).toMatchObject({ boxId: null, boxAmount: null });
	});

	test('resetting confirmations keeps every typed value', () => {
		const draft = readyDraft();
		const reset = resetConfirmations(draft);
		expect(reset.essentialsReviewed).toBe(false);
		expect(reset.costs.every((cost) => cost.notYetRecordedConfirmed === false)).toBe(true);
		expect(reset.costs.map(({ notYetRecordedConfirmed, ...rest }) => rest)).toEqual(
			draft.costs.map(({ notYetRecordedConfirmed, ...rest }) => rest),
		);
		expect(reset.receipts).toEqual(draft.receipts);
	});

	test('a reloaded catalog removes receipts it no longer offers, and names them', () => {
		let draft = addReceipt(addReceipt(emptyDraft(), 'DEBT', 9920), 'PAYMENT_RECORD', 9443);
		const { draft: next, removed } = reconcileReceipts(draft, {
			debts: new Set([9921]),
			paymentRecords: new Set([9443]),
		});
		expect(removed.map((receipt) => receipt.recordId)).toEqual([9920]);
		expect(next.receipts.map((receipt) => receipt.recordId)).toEqual([9443]);
	});

	test('an unavailable catalog proves nothing and removes nothing', () => {
		const draft = addReceipt(emptyDraft(), 'DEBT', 9920);
		const result = reconcileReceipts(draft, { debts: null, paymentRecords: null });
		expect(result.removed).toEqual([]);
		expect(result.draft).toBe(draft);
	});
});

describe('local validation', () => {
	function costIssues(patch, boxIds = new Set([9240])) {
		let draft = addCost(emptyDraft());
		draft = updateCost(draft, draft.costs[0].key, { amount: '10', date: '2026-10-02', ...patch });
		return validateDraft(draft, boxIds).costs.get(draft.costs[0].key) ?? [];
	}

	test('amounts that cannot be sent as decimal text are refused before sending', () => {
		for (const amount of ['1e3', '-5', '1,000', '12abc', ' ', '1.']) {
			expect(costIssues({ amount }).length).toBeGreaterThan(0);
		}
	});

	test('money rules are the server’s: zero, over-limit and fractional cents are sent', () => {
		for (const amount of ['0', '10000000', '1.005']) expect(costIssues({ amount })).toEqual([]);
	});

	test('every cost needs its own explicit date', () => {
		expect(costIssues({ date: '' })).toContain('DATE_REQUIRED');
		expect(costIssues({ date: '2026-02-30' })).toContain('DATE_REQUIRED');
	});

	test('choosing a Box requires saying how much it pays, from a Box that exists', () => {
		expect(costIssues({ boxId: '9240' })).toContain('BOX_AMOUNT_REQUIRED');
		expect(costIssues({ boxId: '9999', boxAmount: '5' })).toContain('BOX_UNAVAILABLE');
		expect(costIssues({ boxId: '9240', boxAmount: '5' })).toEqual([]);
	});

	test('an unavailable Box catalog keeps the chosen Box and leaves it to the server', () => {
		// A failed Box read proves nothing, so funding the User already chose is
		// neither refused nor dropped; the server answers BOX_NOT_FOUND if needed.
		expect(costIssues({ boxId: '9240', boxAmount: '5' }, null)).toEqual([]);
		expect(costIssues({ boxId: '9999', boxAmount: '5' }, null)).toEqual([]);
		expect(costIssues({ boxId: '9240' }, null)).toEqual(['BOX_AMOUNT_REQUIRED']);

		let draft = addCost(emptyDraft());
		draft = updateCost(draft, draft.costs[0].key, { amount: '10', date: '2026-10-02', boxId: '9240', boxAmount: '5' });
		expect(validateDraft(draft, null).ready).toBe(true);
		expect(buildPreviewRequest(draft).items[0]).toMatchObject({ boxId: 9240, boxAmount: '5' });
	});

	test('unconfirmed rows and an unreviewed checklist do not block sending', () => {
		const draft = resetConfirmations(readyDraft());
		expect(validateDraft(draft, new Set()).ready).toBe(true);
	});
});

describe('invalid fields', () => {
	const fields = (cost, local, server = []) =>
		[...invalidCostFields(cost, local, server.map((reason) => ({ reason })))].sort();

	test('only the inputs an issue is about are marked invalid', () => {
		// An unconfirmed row leaves a valid amount and date unmarked.
		expect(fields({ boxId: '' }, [], ['COST_NOT_CONFIRMED_UNRECORDED'])).toEqual(['confirm']);
		expect(fields({ boxId: '9240' }, ['BOX_UNAVAILABLE'], ['BOX_NOT_FOUND'])).toEqual(['box']);
		expect(fields({ boxId: '' }, ['DESCRIPTION_TOO_LONG'])).toEqual(['description']);
		expect(fields({ boxId: '' }, ['AMOUNT_FORMAT'], ['DATE_OUT_OF_WINDOW'])).toEqual(['amount', 'date']);
		expect(fields({ boxId: '9240' }, ['BOX_AMOUNT_REQUIRED'], ['FUNDING_EXCEEDS_COST'])).toEqual(['box-amount']);
	});

	test('a Box-amount problem without a Box marks the Box select, which exists', () => {
		expect(fields({ boxId: '' }, [], ['BOX_REQUIRED'])).toEqual(['box']);
	});

	test('a reason naming no single input marks none, leaving the row message', () => {
		expect(fields({ boxId: '' }, [], ['SOMETHING_NEW', 'BOX_CAPACITY_EXCEEDED'])).toEqual([]);
	});
});

describe('request', () => {
	test('amounts travel exactly as typed and receipts carry no amount', () => {
		const request = buildPreviewRequest(readyDraft());
		expect(request.horizonDays).toBe(30);
		expect(request.items[0].amount).toBe('1200.10');
		expect(request.expectedReceipts[0]).toEqual({ recordId: 9920, recordKind: 'DEBT', date: '2026-10-10' });
		expect(JSON.stringify(request)).not.toContain('netBalance');
	});

	test('field names match the declared request contract exactly', () => {
		const request = buildPreviewRequest(readyDraft());
		expect(Object.keys(request).sort()).toEqual([...PREVIEW_REQUEST_FIELDS.envelope].sort());
		expect(Object.keys(request.items[0]).sort()).toEqual([...PREVIEW_REQUEST_FIELDS.item].sort());
		expect(Object.keys(request.expectedReceipts[0]).sort()).toEqual(
			[...PREVIEW_REQUEST_FIELDS.expectedReceipt].sort(),
		);
	});
});

/** Record component names of `record Name(...)` in a Java source file. */
function javaRecord(file, name) {
	const source = readFileSync(new URL(file, JAVA), 'utf8');
	const start = source.indexOf(`record ${name}(`);
	if (start < 0) throw new Error(`record ${name} not found in ${file}`);
	let depth = 0;
	let end = start + `record ${name}(`.length - 1;
	for (; end < source.length; end += 1) {
		if (source[end] === '(') depth += 1;
		if (source[end] === ')' && --depth === 0) break;
	}
	const body = source
		.slice(start + `record ${name}(`.length, end)
		.replace(/@\w+(\([^)]*\))?/g, '')
		.replace(/<[^<>]*(<[^<>]*>)?[^<>]*>/g, '');
	return body
		.split(',')
		.map((part) => part.trim().split(/\s+/).pop())
		.filter(Boolean);
}

describe('API contract against the backend records', () => {
	const REST = 'infrastructure/adapter/in/rest/';
	const MODEL = 'domain/model/PlanningPreview.java';

	test('request field names are the Java request record’s', () => {
		const file = `${REST}PlanningPreviewRequest.java`;
		expect(javaRecord(file, 'PlanningPreviewRequest')).toEqual([...PREVIEW_REQUEST_FIELDS.envelope]);
		expect(javaRecord(file, 'Item')).toEqual([...PREVIEW_REQUEST_FIELDS.item]);
		expect(javaRecord(file, 'ExpectedReceipt')).toEqual([...PREVIEW_REQUEST_FIELDS.expectedReceipt]);
	});

	test('response field names are the Java response records’', () => {
		const file = `${REST}PlanningPreviewResponse.java`;
		expect(javaRecord(file, 'PlanningPreviewResponse')).toEqual([...PREVIEW_RESPONSE_FIELDS.envelope]);
		expect(javaRecord(file, 'Window')).toEqual([...PREVIEW_RESPONSE_FIELDS.window]);
		expect(javaRecord(file, 'Baseline')).toEqual([...PREVIEW_RESPONSE_FIELDS.baseline]);
		expect(javaRecord(file, 'Projected')).toEqual([...PREVIEW_RESPONSE_FIELDS.projected]);
		expect(javaRecord(file, 'Timing')).toEqual([...PREVIEW_RESPONSE_FIELDS.timing]);
		expect(javaRecord(MODEL, 'BoxProjection')).toEqual([...PREVIEW_RESPONSE_FIELDS.perBox]);
		expect(javaRecord(MODEL, 'MissingInput')).toEqual([...PREVIEW_RESPONSE_FIELDS.missingInput]);
		expect(javaRecord(MODEL, 'IncludedReceipt')).toEqual([...PREVIEW_RESPONSE_FIELDS.includedReceipt]);
		expect(javaRecord(MODEL, 'StatementDue')).toEqual([...PREVIEW_RESPONSE_FIELDS.statementDue]);
		expect(javaRecord(MODEL, 'StatementEstimate')).toEqual([...PREVIEW_RESPONSE_FIELDS.statementEstimate]);
		expect(javaRecord(MODEL, 'TimingReason')).toEqual([...PREVIEW_RESPONSE_FIELDS.timingReason]);
		expect(javaRecord(MODEL, 'UndatedDebt')).toEqual([...PREVIEW_RESPONSE_FIELDS.undatedDebt]);
	});

	test('the fixture stand-in answers with exactly the contract’s envelope fields', async () => {
		const body = await fixtureBody('FX-HORIZON-UI-01', emptyDraft());
		expect(Object.keys(body).sort()).toEqual([...PREVIEW_RESPONSE_FIELDS.envelope].sort());
	});
});

describe('response parsing', () => {
	test('fixture responses in every projection and section state parse', async () => {
		const scenarios = [
			'FX-HORIZON-UI-01',
			'FX-HORIZON-SECTIONS-DOWN-01',
			'FX-HORIZON-BASELINE-DOWN-01',
			'FX-HORIZON-TRACKING-OFF-01',
			'FX-HORIZON-ZONE-01',
		];
		for (const id of scenarios) {
			expect(parsePreviewResponse(await fixtureBody(id, emptyDraft()))).not.toBeNull();
		}
	});

	test('an unavailable projection carrying figures is refused, not shown', async () => {
		const body = await fixtureBody('FX-HORIZON-UI-01', emptyDraft());
		expect(parsePreviewResponse({ ...body, status: 'unavailable' })).toBeNull();
		expect(parsePreviewResponse({ ...body, status: 'complete', projected: null })).toBeNull();
		expect(parsePreviewResponse({ ...body, baseline: null })).toBeNull();
	});

	test('a missing or non-numeric figure fails the whole response instead of becoming zero', async () => {
		const body = await fixtureBody('FX-HORIZON-UI-01', emptyDraft());
		expect(parsePreviewResponse({ ...body, projected: { ...body.projected, availableToSpend: '7000' } })).toBeNull();
		expect(parsePreviewResponse({ ...body, baseline: { ...body.baseline, netBalance: null } })).toBeNull();
		expect(parsePreviewResponse({ ...body, timing: undefined })).toBeNull();
		expect(parsePreviewResponse({ ...body, generatedAt: 'yesterday' })).toBeNull();
	});

	test('undated Debts are null exactly when the server marks them unavailable', async () => {
		const body = await fixtureBody('FX-HORIZON-UI-01', emptyDraft());
		expect(parsePreviewResponse({ ...body, undatedDebtsStatus: 'unavailable' })).toBeNull();
		expect(parsePreviewResponse({ ...body, undatedDebts: null })).toBeNull();
		const down = parsePreviewResponse(await fixtureBody('FX-HORIZON-SECTIONS-DOWN-01', emptyDraft()));
		expect(down.undatedDebts).toBeNull();
		expect(down.projected).not.toBeNull();
	});

	test('an unknown reason code from a newer server is kept, not dropped', async () => {
		const body = await fixtureBody('FX-HORIZON-UI-01', emptyDraft());
		const parsed = parsePreviewResponse({
			...body,
			status: 'unavailable',
			projected: null,
			missingInputs: [{ reason: 'SOMETHING_NEW', itemIndex: 0, receiptIndex: null, boxId: null, shortfall: null }],
		});
		expect(parsed.missingInputs[0].reason).toBe('SOMETHING_NEW');
	});

	test('missing inputs are grouped by the row they name', () => {
		const grouped = groupMissingInputs([
			{ reason: 'DATE_OUT_OF_WINDOW', itemIndex: 1, receiptIndex: null, boxId: null, shortfall: null },
			{ reason: 'INVALID_AMOUNT', itemIndex: 1, receiptIndex: null, boxId: null, shortfall: null },
			{ reason: 'RECEIPT_INELIGIBLE', itemIndex: null, receiptIndex: 0, boxId: null, shortfall: null },
			{ reason: 'BOX_CAPACITY_EXCEEDED', itemIndex: null, receiptIndex: null, boxId: 9240, shortfall: 500 },
		]);
		expect(grouped.byCost.get(1)).toHaveLength(2);
		expect(grouped.byReceipt.get(0)[0].reason).toBe('RECEIPT_INELIGIBLE');
		expect(grouped.general.map((input) => input.boxId)).toEqual([9240]);
	});
});

describe('preview session', () => {
	const okBody = () => fixtureBody('FX-HORIZON-UI-01', emptyDraft());

	test('posts the built request to the preview endpoint and nowhere else', async () => {
		const { fetchFn, calls } = manualFetch();
		const session = new PlanningPreviewSession();
		const draft = readyDraft();
		const done = session.calculate(draft, fetchFn);
		expect(calls).toHaveLength(1);
		expect(calls[0].url).toBe('/api/planning/preview');
		expect(calls[0].init.method).toBe('POST');
		expect(JSON.parse(calls[0].init.body)).toEqual(buildPreviewRequest(draft));
		calls[0].resolve(jsonResponse(await okBody()));
		expect(await done).toBe(true);
		expect(session.state.kind).toBe('result');
	});

	test('an edit during a request clears the screen and the late response is discarded', async () => {
		const { fetchFn, calls } = manualFetch();
		const session = new PlanningPreviewSession();
		const done = session.calculate(readyDraft(), fetchFn);
		session.clear('edited');
		expect(session.state.kind).toBe('idle');
		expect(calls[0].init.signal.aborted).toBe(true);
		calls[0].resolve(jsonResponse(await okBody()));
		expect(await done).toBe(false);
		expect(session.state.kind).toBe('idle');
	});

	test('an older slow response never replaces a newer one, whatever the arrival order', async () => {
		const { fetchFn, calls } = manualFetch();
		const session = new PlanningPreviewSession();
		const older = session.calculate(readyDraft(), fetchFn);
		const newerDraft = updateCost(readyDraft(), readyDraft().costs[0].key, { amount: '99' });
		const newer = session.calculate(newerDraft, fetchFn);

		const newerBody = await okBody();
		calls[1].resolve(jsonResponse(newerBody));
		expect(await newer).toBe(true);
		const shown = session.state;

		calls[0].resolve(jsonResponse({ ...newerBody, generatedAt: '2020-01-01T00:00:00Z' }));
		expect(await older).toBe(false);
		expect(session.state).toBe(shown);
		expect(session.state.submitted.costs[0].amount).toBe('99');
	});

	test('a failure of a superseded request does not overwrite the current result', async () => {
		const { fetchFn, calls } = manualFetch();
		const session = new PlanningPreviewSession();
		const older = session.calculate(readyDraft(), fetchFn);
		const newer = session.calculate(readyDraft(), fetchFn);
		calls[1].resolve(jsonResponse(await okBody()));
		await newer;
		calls[0].reject(new TypeError('network down'));
		expect(await older).toBe(false);
		expect(session.state.kind).toBe('result');
	});

	test('the shown result keeps the rows it was calculated from', async () => {
		const { fetchFn, calls } = manualFetch();
		const session = new PlanningPreviewSession();
		const draft = readyDraft();
		const done = session.calculate(draft, fetchFn);
		draft.costs[0].amount = 'mutated later';
		calls[0].resolve(jsonResponse(await okBody()));
		await done;
		expect(session.state.submitted.costs[0].amount).toBe('1200.10');
	});

	test('failures are named and never carry a figure', async () => {
		const cases = [
			[() => Promise.reject(new TypeError('offline')), 'unreachable'],
			[() => Promise.resolve(jsonResponse({ error: 'x' }, 401)), 'auth'],
			[() => Promise.resolve(jsonResponse({ error: 'x' }, 400)), 'rejected'],
			[() => Promise.resolve(jsonResponse({ error: 'x' }, 502)), 'server'],
			[() => Promise.resolve(new Response('not json', { status: 200 })), 'invalid'],
			[() => Promise.resolve(jsonResponse({ status: 'complete' })), 'invalid'],
		];
		for (const [fetchFn, failure] of cases) {
			const session = new PlanningPreviewSession();
			await session.calculate(readyDraft(), fetchFn);
			expect(session.state).toEqual({ kind: 'failed', failure });
		}
	});

	test('a plain edit after a failure dismisses it without inventing a notice', async () => {
		const session = new PlanningPreviewSession();
		await session.calculate(readyDraft(), () => Promise.reject(new TypeError('offline')));
		session.clear('edited');
		expect(session.state).toEqual({ kind: 'idle', cleared: null });
	});
});

describe('hidden tab and refresh', () => {
	test('returning withdraws the result and every confirmation before the next calculation', async () => {
		const session = new PlanningPreviewSession();
		const draft = readyDraft();
		await session.calculate(draft, async () => jsonResponse(await fixtureBody('FX-HORIZON-UI-01', draft)));
		expect(session.state.kind).toBe('result');

		const next = withdrawForStaleness(session, draft, 'returned');
		expect(session.state).toEqual({ kind: 'idle', cleared: 'returned' });
		expect(next.essentialsReviewed).toBe(false);
		expect(next.costs.every((cost) => !cost.notYetRecordedConfirmed)).toBe(true);
		expect(next.costs[0].amount).toBe(draft.costs[0].amount);
	});

	test('a request in flight while the tab hides can never land afterwards', async () => {
		const { fetchFn, calls } = manualFetch();
		const session = new PlanningPreviewSession();
		const done = session.calculate(readyDraft(), fetchFn);
		withdrawForStaleness(session, readyDraft(), 'returned');
		calls[0].resolve(jsonResponse(await fixtureBody('FX-HORIZON-UI-01', emptyDraft())));
		expect(await done).toBe(false);
		expect(session.state).toEqual({ kind: 'idle', cleared: 'returned' });
	});

	test('refresh behaves the same way', async () => {
		const session = new PlanningPreviewSession();
		const next = withdrawForStaleness(session, readyDraft(), 'refreshed');
		expect(session.state).toEqual({ kind: 'idle', cleared: 'refreshed' });
		expect(next.costs.every((cost) => !cost.notYetRecordedConfirmed)).toBe(true);
	});

	test('hiding an untouched page shows no notice', () => {
		const session = new PlanningPreviewSession();
		withdrawForStaleness(session, emptyDraft(), 'returned');
		expect(session.state).toEqual({ kind: 'idle', cleared: null });
	});

	test('the planning modules never touch browser storage', () => {
		for (const file of [
			'../src/lib/planning-preview.ts',
			'../src/lib/planning-labels.ts',
			'../src/routes/planning/+page.svelte',
			'../src/lib/components/planning/planning-result.svelte',
		]) {
			const source = readFileSync(new URL(file, import.meta.url), 'utf8');
			expect(source).not.toMatch(/localStorage|sessionStorage|indexedDB|document\.cookie/);
		}
	});
});
