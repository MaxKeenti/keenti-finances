/**
 * Deterministic synthetic fixtures for the UX execution plan's Slice 0A.
 *
 * These scenarios are invented for verification. They never describe a real
 * account, and nothing here reads or writes development or production data:
 * every value is served by the in-process fixture backend in `backend.ts`.
 *
 * The manifest in `docs/ux/fixtures/2026-09-07-manifest.md` documents the
 * stable IDs, expected balances, and setup/reset instructions. When a value
 * changes here, the manifest follows this file rather than restating it.
 */

import { MEXICO_CITY_EVENING, MEXICO_CITY_MIDDAY, type FixtureClock } from './clock';

/** A response body keyed by `"<METHOD> <path>"`, e.g. `"GET /api/accounts"`. */
export type FixtureRoutes = Record<string, unknown>;

export type Scenario = {
	/** Stable identifier, also used by the manifest and by acceptance notes. */
	id: string;
	description: string;
	clock: FixtureClock;
	/**
	 * Values the scenario asserts. Tests read these instead of repeating
	 * literals, so the manifest, the fixture, and the assertions cannot drift.
	 */
	expected: Record<string, number | string | boolean | null>;
	routes: FixtureRoutes;
};

const YEAR = 2026;

function monthly(): Array<{ month: number; ingress: number; egress: number }> {
	return Array.from({ length: 12 }, (_, index) => ({ month: index + 1, ingress: 0, egress: 0 }));
}

function summary(values: {
	netBalance: number;
	inBoxes: number;
	availableToSpend: number;
	totalIngress?: number;
	totalEgress?: number;
}) {
	return {
		year: YEAR,
		netBalance: values.netBalance,
		inBoxes: values.inBoxes,
		availableToSpend: values.availableToSpend,
		totalIngress: values.totalIngress ?? 0,
		totalEgress: values.totalEgress ?? 0,
		monthly: monthly(),
	};
}

function boxSummary(values: { netBalance: number; inBoxes: number; availableToSpend: number }) {
	return values;
}

function box(values: {
	id: number;
	name: string;
	balance: number;
	displayOrder: number;
	archived?: boolean;
}) {
	return {
		id: values.id,
		name: values.name,
		hue: 220,
		icon: null,
		description: null,
		displayOrder: values.displayOrder,
		balance: values.balance,
		archived: values.archived ?? false,
		createdAt: '2026-01-05T12:00:00Z',
		updatedAt: '2026-01-05T12:00:00Z',
	};
}

function account(values: {
	id: number;
	name: string;
	kind: 'DEBIT' | 'CASH' | 'CREDIT';
	balance: number;
	openingBalance?: number;
}) {
	return {
		id: values.id,
		name: values.name,
		kind: values.kind,
		hue: 200,
		openingBalance: values.openingBalance ?? 0,
		openingDate: '2026-01-01',
		balance: values.balance,
		archived: false,
	};
}

function trackingStatus(values: {
	active: boolean;
	setupRequired: boolean;
	activatedAt: string | null;
	transactionNetBalance: number;
	accountNetBalance: number;
}) {
	return values;
}

/** Tracking is not configured yet: no Financial Account balances exist. */
const trackingInactive: Scenario = {
	id: 'FX-TRACK-INACTIVE-01',
	description: 'Account tracking inactive; setup is still required.',
	clock: MEXICO_CITY_MIDDAY,
	expected: { active: false, setupRequired: true, accountNetBalance: 0 },
	routes: {
		'GET /api/accounts/status': trackingStatus({
			active: false,
			setupRequired: true,
			activatedAt: null,
			transactionNetBalance: 1_500,
			accountNetBalance: 0,
		}),
		'GET /api/accounts': [],
		'GET /api/boxes': [],
		'GET /api/boxes/summary': boxSummary({ netBalance: 0, inBoxes: 0, availableToSpend: 0 }),
		'GET /api/dashboard/summary': summary({ netBalance: 0, inBoxes: 0, availableToSpend: 0 }),
	},
};

/** Tracking is active with an ordinary, non-alarming position. */
const trackingActive: Scenario = {
	id: 'FX-TRACK-ACTIVE-01',
	description: 'Account tracking active with a healthy positive position.',
	clock: MEXICO_CITY_MIDDAY,
	expected: {
		active: true,
		setupRequired: false,
		netBalance: 2_500.75,
		inBoxes: 500,
		availableToSpend: 2_000.75,
	},
	routes: {
		'GET /api/accounts/status': trackingStatus({
			active: true,
			setupRequired: false,
			activatedAt: '2026-01-15',
			transactionNetBalance: 1_500,
			accountNetBalance: 2_500.75,
		}),
		'GET /api/accounts': [
			account({ id: 9101, name: 'Cuenta sintética A', kind: 'DEBIT', balance: 2_500.75 }),
		],
		'GET /api/boxes': [box({ id: 9201, name: 'Caja sintética A', balance: 500, displayOrder: 1 })],
		'GET /api/boxes/summary': boxSummary({
			netBalance: 2_500.75,
			inBoxes: 500,
			availableToSpend: 2_000.75,
		}),
		'GET /api/dashboard/summary': summary({
			netBalance: 2_500.75,
			inBoxes: 500,
			availableToSpend: 2_000.75,
			totalIngress: 6_000,
			totalEgress: 3_499.25,
		}),
	},
};

/**
 * A legitimate zero: tracking is active and every total really is zero.
 * 1A-loader must keep this distinguishable from a failed load.
 */
const legitimateZero: Scenario = {
	id: 'FX-BAL-ZERO-01',
	description: 'Tracking active; all totals are genuinely zero, not a failed load.',
	clock: MEXICO_CITY_MIDDAY,
	expected: { netBalance: 0, inBoxes: 0, availableToSpend: 0, active: true },
	routes: {
		'GET /api/accounts/status': trackingStatus({
			active: true,
			setupRequired: false,
			activatedAt: '2026-02-01',
			transactionNetBalance: 0,
			accountNetBalance: 0,
		}),
		'GET /api/accounts': [
			account({ id: 9102, name: 'Cuenta sintética vacía', kind: 'DEBIT', balance: 0 }),
		],
		'GET /api/boxes': [],
		'GET /api/boxes/summary': boxSummary({ netBalance: 0, inBoxes: 0, availableToSpend: 0 }),
		'GET /api/dashboard/summary': summary({ netBalance: 0, inBoxes: 0, availableToSpend: 0 }),
	},
};

/**
 * Positive Net Balance with Box reserves that exceed it.
 *
 * Deliberately distinct from the 0B `FX-DASH-NEG-01` figures, which Phase 4
 * owns; this one supports 1A-balance and 2C.
 */
const excessiveReservations: Scenario = {
	id: 'FX-BAL-OVERRESERVED-01',
	description: 'Positive Net Balance (3,240.00) with Boxes reserving 4,000.00.',
	clock: MEXICO_CITY_MIDDAY,
	expected: {
		moneyHeld: 3_200,
		creditInFavor: 40,
		netBalance: 3_240,
		inBoxes: 4_000,
		availableToSpend: -760,
	},
	routes: {
		'GET /api/accounts/status': trackingStatus({
			active: true,
			setupRequired: false,
			activatedAt: '2026-01-15',
			transactionNetBalance: 3_240,
			accountNetBalance: 3_240,
		}),
		'GET /api/accounts': [
			account({ id: 9103, name: 'Cuenta sintética B', kind: 'DEBIT', balance: 3_200 }),
			account({ id: 9104, name: 'Tarjeta sintética B', kind: 'CREDIT', balance: 40 }),
		],
		'GET /api/accounts/9104/credit-settings': {
			creditLimit: 8_000,
			statementClosingDay: 5,
			paymentDueDay: 20,
		},
		'GET /api/accounts/9104/credit-statements': [],
		'GET /api/boxes': [
			box({ id: 9202, name: 'Renta sintética', balance: 2_600, displayOrder: 1 }),
			box({ id: 9203, name: 'Despensa sintética', balance: 1_400, displayOrder: 2 }),
		],
		'GET /api/boxes/summary': boxSummary({
			netBalance: 3_240,
			inBoxes: 4_000,
			availableToSpend: -760,
		}),
		'GET /api/dashboard/summary': summary({
			netBalance: 3_240,
			inBoxes: 4_000,
			availableToSpend: -760,
		}),
	},
};

/** Net Balance itself is negative, which is a different story from over-reserving. */
const negativeNetBalance: Scenario = {
	id: 'FX-BAL-NEGNET-01',
	description: 'Negative Net Balance (−750.00) driven by credit debt, with no Box reserves.',
	clock: MEXICO_CITY_MIDDAY,
	expected: {
		moneyHeld: 150,
		creditDebt: -900,
		netBalance: -750,
		inBoxes: 0,
		availableToSpend: -750,
	},
	routes: {
		'GET /api/accounts/status': trackingStatus({
			active: true,
			setupRequired: false,
			activatedAt: '2026-01-15',
			transactionNetBalance: -750,
			accountNetBalance: -750,
		}),
		'GET /api/accounts': [
			account({ id: 9105, name: 'Cuenta sintética C', kind: 'DEBIT', balance: 150 }),
			account({ id: 9106, name: 'Tarjeta sintética C', kind: 'CREDIT', balance: -900 }),
		],
		'GET /api/accounts/9106/credit-settings': {
			creditLimit: 5_000,
			statementClosingDay: 5,
			paymentDueDay: 20,
		},
		'GET /api/accounts/9106/credit-statements': [],
		'GET /api/boxes': [],
		'GET /api/boxes/summary': boxSummary({
			netBalance: -750,
			inBoxes: 0,
			availableToSpend: -750,
		}),
		'GET /api/dashboard/summary': summary({ netBalance: -750, inBoxes: 0, availableToSpend: -750 }),
	},
};

/** An empty Box while Available to Spend is negative: no withdrawal is possible. */
const emptyBoxNegativeAvailable: Scenario = {
	id: 'FX-BOX-EMPTY-NEGAVAIL-01',
	description: 'Box 9205 holds 0.00 while Available to Spend is −700.00.',
	clock: MEXICO_CITY_MIDDAY,
	expected: {
		netBalance: 1_900,
		inBoxes: 2_600,
		availableToSpend: -700,
		emptyBoxId: 9205,
		emptyBoxBalance: 0,
	},
	routes: {
		'GET /api/accounts/status': trackingStatus({
			active: true,
			setupRequired: false,
			activatedAt: '2026-01-15',
			transactionNetBalance: 1_900,
			accountNetBalance: 1_900,
		}),
		'GET /api/accounts': [
			account({ id: 9107, name: 'Cuenta sintética D', kind: 'DEBIT', balance: 1_900 }),
		],
		'GET /api/boxes': [
			box({ id: 9204, name: 'Renta sintética D', balance: 2_600, displayOrder: 1 }),
			box({ id: 9205, name: 'Emergencia sintética', balance: 0, displayOrder: 2 }),
		],
		'GET /api/boxes/9205': box({
			id: 9205,
			name: 'Emergencia sintética',
			balance: 0,
			displayOrder: 2,
		}),
		'GET /api/boxes/9205/movements': [],
		'GET /api/boxes/summary': boxSummary({
			netBalance: 1_900,
			inBoxes: 2_600,
			availableToSpend: -700,
		}),
		'GET /api/dashboard/summary': summary({
			netBalance: 1_900,
			inBoxes: 2_600,
			availableToSpend: -700,
		}),
	},
};

/**
 * A genuine confirmed-statement mismatch: the confirmed statement balance and
 * the recorded ledger activity disagree by 125.50. This is a record mismatch,
 * not over-reservation and not a negative Net Balance.
 */
const statementMismatch: Scenario = {
	id: 'FX-STMT-MISMATCH-01',
	description: 'Confirmed statement of 1,250.00 mismatching recorded activity by 125.50.',
	clock: MEXICO_CITY_MIDDAY,
	expected: {
		officialBalance: 1_250,
		mismatchAmount: 125.5,
		outstandingBalance: 1_250,
		dueDate: '2026-09-20',
	},
	routes: {
		'GET /api/accounts/status': trackingStatus({
			active: true,
			setupRequired: false,
			activatedAt: '2026-01-15',
			transactionNetBalance: -1_124.5,
			accountNetBalance: -1_124.5,
		}),
		'GET /api/accounts': [
			account({ id: 9108, name: 'Tarjeta sintética E', kind: 'CREDIT', balance: -1_124.5 }),
		],
		'GET /api/accounts/9108/credit-settings': {
			creditLimit: 10_000,
			statementClosingDay: 5,
			paymentDueDay: 20,
		},
		'GET /api/accounts/9108/credit-statements': [
			{
				id: 9301,
				periodStart: '2026-08-06',
				periodEnd: '2026-09-05',
				dueDate: '2026-09-20',
				officialBalance: 1_250,
				officialMinimumPayment: 300,
				officialAvoidInterest: 1_250,
				officialNote: null,
				paidAmount: 0,
				outstandingBalance: 1_250,
				reconciliationMismatch: true,
				mismatchAmount: 125.5,
			},
		],
		'GET /api/boxes': [],
		'GET /api/boxes/summary': boxSummary({
			netBalance: -1_124.5,
			inBoxes: 0,
			availableToSpend: -1_124.5,
		}),
		'GET /api/dashboard/summary': summary({
			netBalance: -1_124.5,
			inBoxes: 0,
			availableToSpend: -1_124.5,
		}),
	},
};

/** A shared subscription with real members and generated payment records. */
const subscriptionPopulated: Scenario = {
	id: 'FX-SUB-SHARED-POPULATED-01',
	description: 'Shared subscription 9401 with two members and paid/pending payment records.',
	clock: MEXICO_CITY_MIDDAY,
	expected: { subscriptionId: 9401, memberCount: 2, paymentCount: 3, cost: 299 },
	routes: {
		'GET /api/subscriptions/9401': {
			id: 9401,
			name: 'Streaming sintético',
			cost: 299,
			billingCycle: 'MONTHLY',
			type: 'SHARED',
			categoryId: null,
			nextBillingDate: '2026-09-15',
			tokenUuid: null,
			ownerParticipates: true,
			createdAt: '2026-01-10T12:00:00Z',
		},
		'GET /api/subscriptions/9401/members': [
			{
				id: 9411,
				subscriptionId: 9401,
				contactId: 9501,
				contactName: 'Contacto sintético 1',
				shareAmount: 99.67,
				createdAt: '2026-01-10T12:00:00Z',
			},
			{
				id: 9412,
				subscriptionId: 9401,
				contactId: 9502,
				contactName: 'Contacto sintético 2',
				shareAmount: 99.67,
				createdAt: '2026-01-10T12:00:00Z',
			},
		],
		'GET /api/subscriptions/9401/payments': [
			{
				id: 9421,
				subscriptionId: 9401,
				memberId: 9411,
				billingDate: '2026-09-01',
				amount: 99.67,
				status: 'PAID',
				paidDate: '2026-09-03',
				transactionId: 9601,
				createdAt: '2026-09-01T12:00:00Z',
			},
			{
				id: 9422,
				subscriptionId: 9401,
				memberId: 9412,
				billingDate: '2026-09-01',
				amount: 99.67,
				status: 'PENDING',
				paidDate: null,
				transactionId: null,
				createdAt: '2026-09-01T12:00:00Z',
			},
			{
				id: 9423,
				subscriptionId: 9401,
				memberId: 9411,
				billingDate: '2026-08-01',
				amount: 99.67,
				status: 'PAID',
				paidDate: '2026-08-04',
				transactionId: 9602,
				createdAt: '2026-08-01T12:00:00Z',
			},
		],
		'GET /api/subscriptions/9401/linked-transactions': [
			{
				id: 9601,
				amount: 99.67,
				direction: 'INGRESS',
				description: 'Aportación sintética',
				transactionDate: '2026-09-03',
				categoryId: null,
				categoryName: null,
				categoryHue: null,
				contactId: 9501,
				contactName: 'Contacto sintético 1',
				subscriptionId: 9401,
			},
		],
		'GET /api/transactions': [
			{
				id: 9603,
				amount: 99.67,
				direction: 'INGRESS',
				description: 'Ingreso sintético sin vincular',
				transactionDate: '2026-09-05',
				categoryId: null,
				categoryName: null,
				categoryHue: null,
				contactId: null,
				contactName: null,
				subscriptionId: null,
			},
		],
	},
};

/** A subscription that is genuinely empty, not one whose sections failed. */
const subscriptionEmpty: Scenario = {
	id: 'FX-SUB-EMPTY-01',
	description: 'Subscription 9402 with genuinely empty members and payments.',
	clock: MEXICO_CITY_MIDDAY,
	expected: { subscriptionId: 9402, memberCount: 0, paymentCount: 0, cost: 149 },
	routes: {
		'GET /api/subscriptions/9402': {
			id: 9402,
			name: 'Servicio sintético personal',
			cost: 149,
			billingCycle: 'MONTHLY',
			type: 'PERSONAL',
			categoryId: null,
			nextBillingDate: '2026-09-20',
			tokenUuid: null,
			ownerParticipates: true,
			createdAt: '2026-02-10T12:00:00Z',
		},
		'GET /api/subscriptions/9402/members': [],
		'GET /api/subscriptions/9402/payments': [],
		'GET /api/subscriptions/9402/linked-transactions': [],
		'GET /api/transactions': [],
	},
};

function debtScenario(values: {
	id: string;
	debtId: number;
	totalPaid: number;
	percent: number;
}): Scenario {
	const totalAmount = 1_000;
	const remaining = totalAmount - values.totalPaid;
	return {
		id: values.id,
		description: `Debt ${values.debtId} at ${values.percent}% progress.`,
		clock: MEXICO_CITY_MIDDAY,
		expected: {
			debtId: values.debtId,
			totalAmount,
			totalPaid: values.totalPaid,
			remaining,
			progressPercent: values.percent,
		},
		routes: {
			[`GET /api/debts/${values.debtId}`]: {
				id: values.debtId,
				contactId: 9503,
				contactName: 'Contacto sintético 3',
				description: 'Préstamo sintético',
				totalAmount,
				totalPaid: values.totalPaid,
				remaining,
				status: remaining === 0 ? 'PAID' : 'PENDING',
				createdAt: '2026-03-01T12:00:00Z',
			},
			[`GET /api/debts/${values.debtId}/payments`]:
				values.totalPaid === 0
					? []
					: [
							{
								id: 9700 + values.debtId,
								debtId: values.debtId,
								amount: values.totalPaid,
								paymentDate: '2026-09-01',
								transactionId: 9604,
								notes: null,
								createdAt: '2026-09-01T12:00:00Z',
							},
						],
			'GET /api/categories': [{ id: 9801, name: 'Categoría sintética', type: 'INGRESS' }],
			'GET /api/accounts': [
				account({ id: 9109, name: 'Cuenta sintética F', kind: 'DEBIT', balance: 1_000 }),
			],
			'GET /api/accounts/status': trackingStatus({
				active: true,
				setupRequired: false,
				activatedAt: '2026-01-15',
				transactionNetBalance: 1_000,
				accountNetBalance: 1_000,
			}),
		},
	};
}

const debtZero = debtScenario({ id: 'FX-DEBT-000-01', debtId: 9901, totalPaid: 0, percent: 0 });
const debtTen = debtScenario({ id: 'FX-DEBT-010-01', debtId: 9902, totalPaid: 100, percent: 10 });
const debtComplete = debtScenario({
	id: 'FX-DEBT-100-01',
	debtId: 9903,
	totalPaid: 1_000,
	percent: 100,
});

/**
 * Date boundaries under the controlled clock.
 *
 * At the scenario's instant the User's calendar day is 2026-09-07 in Mexico
 * City, so the obligations below are yesterday, today, and tomorrow. The same
 * instant is 2026-09-08 in Tokyo, which is what makes this a boundary case.
 */
const dateBoundaries: Scenario = {
	id: 'FX-DATE-BOUNDARY-01',
	description: 'Yesterday/today/tomorrow obligations across a user time-zone day boundary.',
	clock: MEXICO_CITY_EVENING,
	expected: {
		localToday: '2026-09-07',
		utcToday: '2026-09-08',
		yesterday: '2026-09-06',
		tomorrow: '2026-09-08',
	},
	routes: {
		'GET /api/accounts/status': trackingStatus({
			active: true,
			setupRequired: false,
			activatedAt: '2026-01-15',
			transactionNetBalance: 2_000,
			accountNetBalance: 2_000,
		}),
		'GET /api/accounts': [
			account({ id: 9110, name: 'Tarjeta sintética G', kind: 'CREDIT', balance: -500 }),
		],
		'GET /api/accounts/9110/credit-settings': {
			creditLimit: 7_000,
			statementClosingDay: 5,
			paymentDueDay: 20,
		},
		'GET /api/accounts/9110/credit-statements': [
			{
				id: 9302,
				periodStart: '2026-08-06',
				periodEnd: '2026-09-05',
				dueDate: '2026-09-06',
				officialBalance: 500,
				officialMinimumPayment: 100,
				officialAvoidInterest: 500,
				officialNote: null,
				paidAmount: 0,
				outstandingBalance: 500,
				reconciliationMismatch: false,
				mismatchAmount: 0,
			},
		],
		'GET /api/subscriptions': [
			{
				id: 9403,
				name: 'Suscripción de ayer',
				cost: 100,
				billingCycle: 'MONTHLY',
				type: 'PERSONAL',
				categoryId: null,
				nextBillingDate: '2026-09-06',
				tokenUuid: null,
				ownerParticipates: true,
				createdAt: '2026-01-10T12:00:00Z',
			},
			{
				id: 9404,
				name: 'Suscripción de hoy',
				cost: 100,
				billingCycle: 'MONTHLY',
				type: 'PERSONAL',
				categoryId: null,
				nextBillingDate: '2026-09-07',
				tokenUuid: null,
				ownerParticipates: true,
				createdAt: '2026-01-10T12:00:00Z',
			},
			{
				id: 9405,
				name: 'Suscripción de mañana',
				cost: 100,
				billingCycle: 'MONTHLY',
				type: 'PERSONAL',
				categoryId: null,
				nextBillingDate: '2026-09-08',
				tokenUuid: null,
				ownerParticipates: true,
				createdAt: '2026-01-10T12:00:00Z',
			},
		],
	},
};

/** A long synthetic account name for 1C's responsive alert checks. */
export const LONG_ACCOUNT_NAME =
	'Cuenta sintética de nómina con nombre extremadamente largo para pruebas responsivas';

const longNames: Scenario = {
	id: 'FX-LONGNAME-01',
	description: 'Overdrawn account with a very long name, for responsive alert layout checks.',
	clock: MEXICO_CITY_MIDDAY,
	expected: { accountId: 9111, balance: -1_234.56, name: LONG_ACCOUNT_NAME },
	routes: {
		'GET /api/accounts/status': trackingStatus({
			active: true,
			setupRequired: false,
			activatedAt: '2026-01-15',
			transactionNetBalance: -1_234.56,
			accountNetBalance: -1_234.56,
		}),
		'GET /api/accounts': [
			account({ id: 9111, name: LONG_ACCOUNT_NAME, kind: 'DEBIT', balance: -1_234.56 }),
		],
		'GET /api/boxes': [],
		'GET /api/boxes/summary': boxSummary({
			netBalance: -1_234.56,
			inBoxes: 0,
			availableToSpend: -1_234.56,
		}),
		'GET /api/dashboard/summary': summary({
			netBalance: -1_234.56,
			inBoxes: 0,
			availableToSpend: -1_234.56,
		}),
	},
};

const ALL: readonly Scenario[] = Object.freeze([
	trackingInactive,
	trackingActive,
	legitimateZero,
	excessiveReservations,
	negativeNetBalance,
	emptyBoxNegativeAvailable,
	statementMismatch,
	subscriptionPopulated,
	subscriptionEmpty,
	debtZero,
	debtTen,
	debtComplete,
	dateBoundaries,
	longNames,
]);

export type ScenarioId = (typeof ALL)[number]['id'];

const BY_ID = new Map(ALL.map((scenario) => [scenario.id, scenario]));

/** Every scenario ID, in manifest order. */
export function scenarioIds(): string[] {
	return ALL.map((scenario) => scenario.id);
}

/**
 * Returns an independent deep copy of a scenario.
 *
 * Copying is the isolation guarantee: a test that mutates what it loads
 * cannot change what the next test loads.
 */
export function loadScenario(id: string): Scenario {
	const scenario = BY_ID.get(id);
	if (!scenario) {
		throw new Error(`Unknown fixture scenario: ${id}. Known: ${scenarioIds().join(', ')}`);
	}
	return structuredClone(scenario);
}
