/**
 * Deterministic synthetic fixtures for the UX execution plan's Slices 0A and 0B.
 *
 * These scenarios are invented for verification. They never describe a real
 * account, and nothing here reads or writes development or production data:
 * every value is served by the in-process fixture backend in `backend.ts`.
 *
 * `README.md` in this directory documents the stable IDs, expected balances,
 * and setup/reset instructions. When a value changes here, that document
 * follows this file rather than restating it independently.
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
				status: remaining === 0 ? 'PAID' : 'ACTIVE',
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

function receivable(values: {
	id: number;
	contactId: number | null;
	contactName: string | null;
	totalAmount: number;
	totalPaid: number;
	createdAt: string;
}) {
	const remaining = values.totalAmount - values.totalPaid;
	return {
		id: values.id,
		contactId: values.contactId,
		contactName: values.contactName,
		description: `Deuda sintética ${values.id}`,
		totalAmount: values.totalAmount,
		totalPaid: values.totalPaid,
		remaining,
		status: remaining === 0 ? 'PAID' : 'ACTIVE',
		createdAt: values.createdAt,
	};
}

/**
 * Money owed to the User across several debtors, for the 3C totals.
 *
 * It deliberately mixes a debtor with two active debts, a debtor with one, a
 * settled debt that must not be counted, and a debt with no Contact — which
 * cannot be grouped with anything and stands alone.
 */
const receivablesByDebtor: Scenario = {
	id: 'FX-DEBT-RECEIVABLES-01',
	description: 'Outstanding money owed to the User, grouped by debtor, with one settled debt.',
	clock: MEXICO_CITY_MIDDAY,
	expected: {
		totalOutstanding: 2_050,
		outstandingDebtCount: 4,
		debtorCount: 3,
		topDebtorKey: 'contact-9504',
		topDebtorOutstanding: 1_200,
		groupedDebtorKey: 'contact-9503',
		groupedDebtorOutstanding: 750,
		unlinkedDebtorKey: 'debt-9907',
		settledDebtId: 9906,
	},
	routes: {

		'GET /api/boxes/summary': boxSummary({ netBalance: 1_000, inBoxes: 0, availableToSpend: 1_000 }),
		'GET /api/boxes': [],
		'GET /api/trash': [
			{ id: 9590, entityType: 'contact', label: 'Contacto sintético con nombre largo para verificar recuperación', deletedAt: '2026-09-07T18:00:00Z' },
		],
		'GET /api/debts/9904': receivable({ id: 9904, contactId: 9503, contactName: 'Contacto sintético 3', totalAmount: 600, totalPaid: 200, createdAt: '2026-03-01T12:00:00Z' }),
		'GET /api/debts/9904/payments': [{ id: 9708, debtId: 9904, amount: 200, paymentDate: '2026-09-07', transactionId: 9612, notes: null, createdAt: '2026-09-07T18:00:00Z' }],
		'GET /api/transactions/9612': { id: 9612, amount: 200, direction: 'INGRESS', description: 'Pago sintético de deuda', transactionDate: '2026-09-07', categoryId: 9801, categoryName: 'Categoría sintética', contactId: 9503, contactName: 'Contacto sintético 3', boxFunding: [], boxDistributions: [], availableToSpendAmount: 200 },
		'GET /api/debts': [
			receivable({ id: 9904, contactId: 9503, contactName: 'Contacto sintético 3', totalAmount: 600, totalPaid: 200, createdAt: '2026-03-01T12:00:00Z' }),
			receivable({ id: 9905, contactId: 9503, contactName: 'Contacto sintético 3', totalAmount: 350, totalPaid: 0, createdAt: '2026-04-01T12:00:00Z' }),
			receivable({ id: 9906, contactId: 9503, contactName: 'Contacto sintético 3', totalAmount: 500, totalPaid: 500, createdAt: '2026-02-01T12:00:00Z' }),
			receivable({ id: 9907, contactId: null, contactName: null, totalAmount: 100, totalPaid: 0, createdAt: '2026-05-01T12:00:00Z' }),
			receivable({ id: 9908, contactId: 9504, contactName: 'Contacto sintético 4', totalAmount: 1_500, totalPaid: 300, createdAt: '2026-06-01T12:00:00Z' }),
		],
		'GET /api/contacts': [
			{ id: 9503, name: 'Contacto sintético 3', phone: null, email: null },
			{ id: 9504, name: 'Contacto sintético 4', phone: '555-0104', email: null },
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

/*
 * ---------------------------------------------------------------------------
 * Slice 0B — later-journey fixtures
 *
 * Prerequisites for 2A/2B/3A/3B and Phase 4. Same rules as 0A: every value is
 * invented, nothing is read from or written to development data, and the
 * expected figures below are what the acceptance checks assert.
 * ---------------------------------------------------------------------------
 */

function planSummary(values: {
	id: number;
	boxId: number;
	type: BoxPlanType;
	status: BoxPlanStatus;
	createdAt?: string;
	closedAt?: string | null;
	completionAmount?: number | null;
}) {
	return {
		id: values.id,
		boxId: values.boxId,
		type: values.type,
		status: values.status,
		createdAt: values.createdAt ?? '2026-03-01T12:00:00Z',
		closedAt: values.closedAt ?? null,
		completionAmount: values.completionAmount ?? null,
	};
}

type BoxPlanType = 'SAVING_GOAL' | 'SPENDING_BUDGET';
type BoxPlanStatus =
	| 'ACTIVE'
	| 'READY_TO_COMPLETE'
	| 'OVERDUE'
	| 'COMPLETED'
	| 'ABANDONED'
	| 'ENDED';

function savingGoalPeriod(values: {
	id: number;
	startDate: string;
	endDate: string;
	openingBalance: number;
	closingBalance: number;
	regularCommitment: number;
	status: 'OPEN' | 'ACHIEVED' | 'MISSED';
	openingArrears?: number;
	shortfall?: number;
}) {
	const netProgress = values.closingBalance - values.openingBalance;
	const openingArrears = values.openingArrears ?? 0;
	return {
		id: values.id,
		revisionId: values.id + 100,
		startDate: values.startDate,
		endDate: values.endDate,
		openingBalance: values.openingBalance,
		closingBalance: values.closingBalance,
		netProgress,
		regularCommitment: values.regularCommitment,
		openingArrears,
		requiredAmount: values.regularCommitment + openingArrears,
		arrearsCovered: 0,
		regularProgress: netProgress,
		extraProgress: 0,
		shortfall: values.shortfall ?? 0,
		status: values.status,
		evaluatedAt: values.status === 'OPEN' ? null : `${values.endDate}T23:59:00Z`,
	};
}

function savingGoalRevision(values: {
	id: number;
	targetAmount: number;
	targetDate: string;
	regularCommitment: number;
}) {
	return {
		id: values.id,
		effectiveFrom: '2026-03-01',
		cadence: 'MONTHLY' as const,
		anchorWeekday: null,
		anchorDayOfMonth: 1,
		targetAmount: values.targetAmount,
		targetDate: values.targetDate,
		regularCommitment: values.regularCommitment,
		createdAt: '2026-03-01T12:00:00Z',
		supersededAt: null,
		scheduled: false,
	};
}

/**
 * An active Saving Goal that is on its way: 3,000.00 of a 12,000.00 target.
 *
 * Supports 2A's plan type/status on the Boxes overview and 2B's plan detail.
 */
const planGoalActive: Scenario = {
	id: 'FX-PLAN-GOAL-ACTIVE-01',
	description: 'Box 9210 with an ACTIVE Saving Goal at 25% of a 12,000.00 target.',
	clock: MEXICO_CITY_MIDDAY,
	expected: {
		boxId: 9210,
		planId: 9251,
		planType: 'SAVING_GOAL',
		planStatus: 'ACTIVE',
		boxBalance: 3_000,
		targetAmount: 12_000,
		remainingAmount: 9_000,
		progressPercent: 25,
		currentCommitment: 1_500,
	},
	routes: {
		'GET /api/boxes': [box({ id: 9210, name: 'Meta sintética activa', balance: 3_000, displayOrder: 1 })],
		'GET /api/boxes?archived=true': [],
		'GET /api/boxes/9210': box({
			id: 9210,
			name: 'Meta sintética activa',
			balance: 3_000,
			displayOrder: 1,
		}),
		'GET /api/boxes/9210/history': [],
		'GET /api/boxes/9210/plans': [
			planSummary({ id: 9251, boxId: 9210, type: 'SAVING_GOAL', status: 'ACTIVE' }),
		],
		'GET /api/boxes/9210/plans/saving-goal/9251': {
			id: 9251,
			boxId: 9210,
			type: 'SAVING_GOAL',
			status: 'ACTIVE',
			targetAmount: 12_000,
			targetDate: '2026-12-31',
			cadence: 'MONTHLY',
			anchorWeekday: null,
			anchorDayOfMonth: 1,
			regularCommitment: 1_500,
			boxBalance: 3_000,
			remainingAmount: 9_000,
			progressPercent: 25,
			arrears: 0,
			currentCommitment: 1_500,
			projectedCompletionDate: '2026-12-01',
			suggestedExtensionDate: null,
			currentPeriod: savingGoalPeriod({
				id: 9261,
				startDate: '2026-09-01',
				endDate: '2026-09-30',
				openingBalance: 3_000,
				closingBalance: 3_000,
				regularCommitment: 1_500,
				status: 'OPEN',
			}),
			periods: [
				savingGoalPeriod({
					id: 9260,
					startDate: '2026-08-01',
					endDate: '2026-08-31',
					openingBalance: 1_500,
					closingBalance: 3_000,
					regularCommitment: 1_500,
					status: 'ACHIEVED',
				}),
			],
			revisions: [
				savingGoalRevision({
					id: 9271,
					targetAmount: 12_000,
					targetDate: '2026-12-31',
					regularCommitment: 1_500,
				}),
			],
			createdAt: '2026-03-01T12:00:00Z',
			updatedAt: '2026-09-01T12:00:00Z',
			closedAt: null,
			completionAmount: null,
		},
		'GET /api/boxes/summary': boxSummary({
			netBalance: 8_000,
			inBoxes: 3_000,
			availableToSpend: 5_000,
		}),
		'GET /api/accounts/status': trackingStatus({
			active: true,
			setupRequired: false,
			activatedAt: '2026-01-15',
			transactionNetBalance: 8_000,
			accountNetBalance: 8_000,
		}),
	},
};

/**
 * A Saving Goal past its target date with money still missing: status OVERDUE.
 */
const planGoalOverdue: Scenario = {
	id: 'FX-PLAN-GOAL-OVERDUE-01',
	description: 'Box 9211 with an OVERDUE Saving Goal: 1,200.00 of 5,000.00 past its target date.',
	clock: MEXICO_CITY_MIDDAY,
	expected: {
		boxId: 9211,
		planId: 9252,
		planType: 'SAVING_GOAL',
		planStatus: 'OVERDUE',
		boxBalance: 1_200,
		targetAmount: 5_000,
		remainingAmount: 3_800,
		progressPercent: 24,
		targetDate: '2026-08-31',
		arrears: 800,
	},
	routes: {
		'GET /api/boxes': [box({ id: 9211, name: 'Meta sintética vencida', balance: 1_200, displayOrder: 1 })],
		'GET /api/boxes?archived=true': [],
		'GET /api/boxes/9211': box({
			id: 9211,
			name: 'Meta sintética vencida',
			balance: 1_200,
			displayOrder: 1,
		}),
		'GET /api/boxes/9211/history': [],
		'GET /api/boxes/9211/plans': [
			planSummary({ id: 9252, boxId: 9211, type: 'SAVING_GOAL', status: 'OVERDUE' }),
		],
		'GET /api/boxes/9211/plans/saving-goal/9252': {
			id: 9252,
			boxId: 9211,
			type: 'SAVING_GOAL',
			status: 'OVERDUE',
			targetAmount: 5_000,
			targetDate: '2026-08-31',
			cadence: 'MONTHLY',
			anchorWeekday: null,
			anchorDayOfMonth: 1,
			regularCommitment: 800,
			boxBalance: 1_200,
			remainingAmount: 3_800,
			progressPercent: 24,
			arrears: 800,
			currentCommitment: 1_600,
			projectedCompletionDate: null,
			suggestedExtensionDate: '2027-01-31',
			currentPeriod: savingGoalPeriod({
				id: 9263,
				startDate: '2026-09-01',
				endDate: '2026-09-30',
				openingBalance: 1_200,
				closingBalance: 1_200,
				regularCommitment: 800,
				openingArrears: 800,
				status: 'OPEN',
			}),
			periods: [
				savingGoalPeriod({
					id: 9262,
					startDate: '2026-08-01',
					endDate: '2026-08-31',
					openingBalance: 1_200,
					closingBalance: 1_200,
					regularCommitment: 800,
					shortfall: 800,
					status: 'MISSED',
				}),
			],
			revisions: [
				savingGoalRevision({
					id: 9272,
					targetAmount: 5_000,
					targetDate: '2026-08-31',
					regularCommitment: 800,
				}),
			],
			createdAt: '2026-03-01T12:00:00Z',
			updatedAt: '2026-09-01T12:00:00Z',
			closedAt: null,
			completionAmount: null,
		},
		'GET /api/boxes/summary': boxSummary({
			netBalance: 4_000,
			inBoxes: 1_200,
			availableToSpend: 2_800,
		}),
		'GET /api/accounts/status': trackingStatus({
			active: true,
			setupRequired: false,
			activatedAt: '2026-01-15',
			transactionNetBalance: 4_000,
			accountNetBalance: 4_000,
		}),
	},
};

/**
 * A completed Saving Goal. The Box has plan history but no active plan, so the
 * overview must offer "Add plan" rather than describing a finished one as
 * current.
 */
const planGoalCompleted: Scenario = {
	id: 'FX-PLAN-GOAL-COMPLETED-01',
	description: 'Box 9212 whose Saving Goal is COMPLETED at 4,500.00; no active plan remains.',
	clock: MEXICO_CITY_MIDDAY,
	expected: {
		boxId: 9212,
		planId: 9253,
		planType: 'SAVING_GOAL',
		planStatus: 'COMPLETED',
		completionAmount: 4_500,
		activePlans: 0,
		boxBalance: 4_500,
	},
	routes: {
		'GET /api/boxes': [box({ id: 9212, name: 'Meta sintética cumplida', balance: 4_500, displayOrder: 1 })],
		'GET /api/boxes?archived=true': [],
		'GET /api/boxes/9212': box({
			id: 9212,
			name: 'Meta sintética cumplida',
			balance: 4_500,
			displayOrder: 1,
		}),
		'GET /api/boxes/9212/history': [],
		'GET /api/boxes/9212/plans': [
			planSummary({
				id: 9253,
				boxId: 9212,
				type: 'SAVING_GOAL',
				status: 'COMPLETED',
				closedAt: '2026-08-31T18:00:00Z',
				completionAmount: 4_500,
			}),
		],
		'GET /api/boxes/9212/plans/saving-goal/9253': {
			id: 9253,
			boxId: 9212,
			type: 'SAVING_GOAL',
			status: 'COMPLETED',
			targetAmount: 4_500,
			targetDate: '2026-08-31',
			cadence: 'MONTHLY',
			anchorWeekday: null,
			anchorDayOfMonth: 1,
			regularCommitment: 900,
			boxBalance: 4_500,
			remainingAmount: 0,
			progressPercent: 100,
			arrears: 0,
			currentCommitment: 0,
			projectedCompletionDate: '2026-08-31',
			suggestedExtensionDate: null,
			currentPeriod: null,
			periods: [
				savingGoalPeriod({
					id: 9264,
					startDate: '2026-08-01',
					endDate: '2026-08-31',
					openingBalance: 3_600,
					closingBalance: 4_500,
					regularCommitment: 900,
					status: 'ACHIEVED',
				}),
			],
			revisions: [
				savingGoalRevision({
					id: 9273,
					targetAmount: 4_500,
					targetDate: '2026-08-31',
					regularCommitment: 900,
				}),
			],
			createdAt: '2026-03-01T12:00:00Z',
			updatedAt: '2026-08-31T18:00:00Z',
			closedAt: '2026-08-31T18:00:00Z',
			completionAmount: 4_500,
		},
		'GET /api/boxes/summary': boxSummary({
			netBalance: 9_000,
			inBoxes: 4_500,
			availableToSpend: 4_500,
		}),
		'GET /api/accounts/status': trackingStatus({
			active: true,
			setupRequired: false,
			activatedAt: '2026-01-15',
			transactionNetBalance: 9_000,
			accountNetBalance: 9_000,
		}),
	},
};

/**
 * An underfunded Spending Budget: the Box holds 450.00 of a 2,000.00 desired
 * period balance, so the suggested top-up is 1,550.00. The suggestion is
 * guidance; nothing here moves money.
 */
const planBudgetUnderfunded: Scenario = {
	id: 'FX-PLAN-BUDGET-UNDER-01',
	description: 'Box 9213 with a Spending Budget of 2,000.00 funded to 450.00 (top-up 1,550.00).',
	clock: MEXICO_CITY_MIDDAY,
	expected: {
		boxId: 9213,
		planId: 9254,
		planType: 'SPENDING_BUDGET',
		planStatus: 'ACTIVE',
		desiredBalance: 2_000,
		boxBalance: 450,
		suggestedTopUp: 1_550,
		fundedSpending: 1_550,
	},
	routes: {
		'GET /api/boxes': [box({ id: 9213, name: 'Despensa sintética 0B', balance: 450, displayOrder: 1 })],
		'GET /api/boxes?archived=true': [],
		'GET /api/boxes/9213': box({
			id: 9213,
			name: 'Despensa sintética 0B',
			balance: 450,
			displayOrder: 1,
		}),
		'GET /api/boxes/9213/history': [],
		'GET /api/boxes/9213/plans': [
			planSummary({ id: 9254, boxId: 9213, type: 'SPENDING_BUDGET', status: 'ACTIVE' }),
		],
		'GET /api/boxes/9213/plans/spending-budget/9254': {
			id: 9254,
			boxId: 9213,
			type: 'SPENDING_BUDGET',
			status: 'ACTIVE',
			desiredBalance: 2_000,
			cadence: 'MONTHLY',
			anchorWeekday: null,
			anchorDayOfMonth: 1,
			boxBalance: 450,
			suggestedTopUp: 1_550,
			currentPeriod: {
				id: 9265,
				revisionId: 9274,
				periodStart: '2026-09-01',
				periodEnd: '2026-09-30',
				openingBalance: 2_000,
				closingBalance: 450,
				netProgress: -1_550,
				deposits: 0,
				withdrawals: 0,
				transfersIn: 0,
				transfersOut: 0,
				fundedSpending: 1_550,
				suggestedTopUp: 1_550,
				evaluatedAt: null,
			},
			periods: [],
			revisions: [
				{
					id: 9274,
					effectiveFrom: '2026-03-01',
					cadence: 'MONTHLY',
					anchorWeekday: null,
					anchorDayOfMonth: 1,
					desiredBalance: 2_000,
					createdAt: '2026-03-01T12:00:00Z',
					supersededAt: null,
					scheduled: false,
				},
			],
			createdAt: '2026-03-01T12:00:00Z',
			updatedAt: '2026-09-05T12:00:00Z',
			closedAt: null,
			completionAmount: null,
		},
		'GET /api/boxes/summary': boxSummary({
			netBalance: 3_450,
			inBoxes: 450,
			availableToSpend: 3_000,
		}),
		'GET /api/accounts/status': trackingStatus({
			active: true,
			setupRequired: false,
			activatedAt: '2026-01-15',
			transactionNetBalance: 3_450,
			accountNetBalance: 3_450,
		}),
	},
};

/**
 * A Box that never had a plan, alongside one whose plan list is unavailable.
 *
 * 2A must distinguish those two: Box 9214 genuinely has no plan and gets the
 * single "Add plan" action; Box 9215's plans cannot be loaded and must say so
 * instead of being described as unplanned.
 */
const boxesNoPlan: Scenario = {
	id: 'FX-BOX-NOPLAN-01',
	description: 'Box 9214 has no plan (empty list); Box 9215 answers 500 for its plans.',
	clock: MEXICO_CITY_MIDDAY,
	expected: {
		unplannedBoxId: 9214,
		unavailablePlanBoxId: 9215,
		netBalance: 2_400,
		inBoxes: 900,
		availableToSpend: 1_500,
	},
	routes: {
		'GET /api/boxes': [
			box({ id: 9214, name: 'Caja sin plan', balance: 600, displayOrder: 1 }),
			box({ id: 9215, name: 'Caja con planes no disponibles', balance: 300, displayOrder: 2 }),
		],
		'GET /api/boxes?archived=true': [],
		'GET /api/boxes/9214': box({ id: 9214, name: 'Caja sin plan', balance: 600, displayOrder: 1 }),
		'GET /api/boxes/9214/history': [],
		'GET /api/boxes/9214/plans': [],
		'GET /api/boxes/9215': box({
			id: 9215,
			name: 'Caja con planes no disponibles',
			balance: 300,
			displayOrder: 2,
		}),
		'GET /api/boxes/9215/history': [],
		// Declared so a test can inject its failure on a route the scenario owns;
		// the acceptance case is the 500 injected over it, not this body.
		'GET /api/boxes/9215/plans': [],
		'GET /api/boxes/summary': boxSummary({
			netBalance: 2_400,
			inBoxes: 900,
			availableToSpend: 1_500,
		}),
		'GET /api/accounts/status': trackingStatus({
			active: true,
			setupRequired: false,
			activatedAt: '2026-01-15',
			transactionNetBalance: 2_400,
			accountNetBalance: 2_400,
		}),
	},
};

/**
 * A populated Shared Subscription where the Owner participates in the split:
 * 600.00 over the Owner and two Members, 200.00 each, one Member paid.
 */
const subscriptionOwnerParticipating: Scenario = {
	id: 'FX-SUB-SHARED-OWNER-PARTIAL-01',
	description:
		'Shared subscription 9406, owner participating: 600.00 split three ways, one of two Member contributions received.',
	clock: MEXICO_CITY_MIDDAY,
	expected: {
		subscriptionId: 9406,
		cost: 600,
		ownerParticipates: true,
		splitCount: 3,
		shareAmount: 200,
		memberCount: 2,
		expectedContributions: 400,
		collectedContributions: 200,
		outstandingContributions: 200,
		ownShare: 200,
	},
	routes: {
		'GET /api/subscriptions/9406': {
			id: 9406,
			name: 'Streaming compartido sintético',
			cost: 600,
			billingCycle: 'MONTHLY',
			type: 'SHARED',
			categoryId: null,
			nextBillingDate: '2026-09-15',
			tokenUuid: null,
			ownerParticipates: true,
			createdAt: '2026-02-01T12:00:00Z',
		},
		'GET /api/subscriptions/9406/members': [
			{
				id: 9413,
				subscriptionId: 9406,
				contactId: 9504,
				contactName: 'Contacto sintético 4',
				shareAmount: 200,
				createdAt: '2026-02-01T12:00:00Z',
			},
			{
				id: 9414,
				subscriptionId: 9406,
				contactId: 9505,
				contactName: 'Contacto sintético 5',
				shareAmount: 200,
				createdAt: '2026-02-01T12:00:00Z',
			},
		],
		'GET /api/subscriptions/9406/payments': [
			{
				id: 9424,
				subscriptionId: 9406,
				memberId: 9413,
				billingDate: '2026-09-01',
				amount: 200,
				status: 'PAID',
				paidDate: '2026-09-02',
				transactionId: 9605,
				createdAt: '2026-09-01T12:00:00Z',
			},
			{
				id: 9425,
				subscriptionId: 9406,
				memberId: 9414,
				billingDate: '2026-09-01',
				amount: 200,
				status: 'PENDING',
				paidDate: null,
				transactionId: null,
				createdAt: '2026-09-01T12:00:00Z',
			},
		],
		'GET /api/subscriptions/9406/linked-transactions': [
			{
				id: 9605,
				amount: 200,
				direction: 'INGRESS',
				description: 'Aportación sintética recibida',
				transactionDate: '2026-09-02',
				categoryId: null,
				categoryName: null,
				categoryHue: null,
				contactId: 9504,
				contactName: 'Contacto sintético 4',
				subscriptionId: 9406,
			},
		],
		'GET /api/transactions': [],
	},
};

/**
 * The middleman variant: the Owner does not participate, so the same 600.00
 * splits between two Members at 300.00 each and the Owner's own share is 0.00.
 */
const subscriptionMiddleman: Scenario = {
	id: 'FX-SUB-SHARED-MIDDLEMAN-PARTIAL-01',
	description:
		'Shared subscription 9407, owner not participating: 600.00 split between two Members, one contribution received.',
	clock: MEXICO_CITY_MIDDAY,
	expected: {
		subscriptionId: 9407,
		cost: 600,
		ownerParticipates: false,
		splitCount: 2,
		shareAmount: 300,
		memberCount: 2,
		expectedContributions: 600,
		collectedContributions: 300,
		outstandingContributions: 300,
		ownShare: 0,
	},
	routes: {
		'GET /api/subscriptions/9407': {
			id: 9407,
			name: 'Servicio intermediado sintético',
			cost: 600,
			billingCycle: 'MONTHLY',
			type: 'SHARED',
			categoryId: null,
			nextBillingDate: '2026-09-18',
			tokenUuid: null,
			ownerParticipates: false,
			createdAt: '2026-02-05T12:00:00Z',
		},
		'GET /api/subscriptions/9407/members': [
			{
				id: 9415,
				subscriptionId: 9407,
				contactId: 9506,
				contactName: 'Contacto sintético 6',
				shareAmount: 300,
				createdAt: '2026-02-05T12:00:00Z',
			},
			{
				id: 9416,
				subscriptionId: 9407,
				contactId: 9507,
				contactName: 'Contacto sintético 7',
				shareAmount: 300,
				createdAt: '2026-02-05T12:00:00Z',
			},
		],
		'GET /api/subscriptions/9407/payments': [
			{
				id: 9426,
				subscriptionId: 9407,
				memberId: 9415,
				billingDate: '2026-09-01',
				amount: 300,
				status: 'PAID',
				paidDate: '2026-09-04',
				transactionId: 9606,
				createdAt: '2026-09-01T12:00:00Z',
			},
			{
				id: 9427,
				subscriptionId: 9407,
				memberId: 9416,
				billingDate: '2026-09-01',
				amount: 300,
				status: 'PENDING',
				paidDate: null,
				transactionId: null,
				createdAt: '2026-09-01T12:00:00Z',
			},
		],
		'GET /api/subscriptions/9407/linked-transactions': [
			{
				id: 9606,
				amount: 300,
				direction: 'INGRESS',
				description: 'Aportación sintética intermediada',
				transactionDate: '2026-09-04',
				categoryId: null,
				categoryName: null,
				categoryHue: null,
				contactId: 9506,
				contactName: 'Contacto sintético 6',
				subscriptionId: 9407,
			},
		],
		'GET /api/transactions': [],
	},
};

/**
 * Credit in the User's favor while a confirmed statement payment is still
 * unpaid. Both figures are true at once; 3A must explain them rather than
 * declare either wrong.
 */
const creditInFavorUnpaidStatement: Scenario = {
	id: 'FX-CREDIT-INFAVOR-STMT-01',
	description: 'Credit Financial Account 9112 at +85.00 with an unpaid confirmed statement of 640.00.',
	clock: MEXICO_CITY_MIDDAY,
	expected: {
		accountId: 9112,
		creditInFavor: 85,
		creditLimit: 12_000,
		availableCredit: 12_085,
		statementId: 9303,
		officialBalance: 640,
		outstandingBalance: 640,
		dueDate: '2026-09-20',
		netBalance: 1_085,
		inBoxes: 0,
		availableToSpend: 1_085,
	},
	routes: {
		'GET /api/accounts/status': trackingStatus({
			active: true,
			setupRequired: false,
			activatedAt: '2026-01-15',
			transactionNetBalance: 1_085,
			accountNetBalance: 1_085,
		}),
		'GET /api/accounts': [
			account({ id: 9113, name: 'Cuenta sintética H', kind: 'DEBIT', balance: 1_000 }),
			account({ id: 9112, name: 'Tarjeta sintética a favor', kind: 'CREDIT', balance: 85 }),
		],
		'GET /api/accounts/9112/credit-settings': {
			creditLimit: 12_000,
			statementClosingDay: 5,
			paymentDueDay: 20,
		},
		'GET /api/accounts/9112/credit-statements': [
			{
				id: 9303,
				periodStart: '2026-08-06',
				periodEnd: '2026-09-05',
				dueDate: '2026-09-20',
				officialBalance: 640,
				officialMinimumPayment: 150,
				officialAvoidInterest: 640,
				officialNote: null,
				paidAmount: 0,
				outstandingBalance: 640,
				reconciliationMismatch: false,
				mismatchAmount: 0,
			},
		],
		'GET /api/boxes': [],
		'GET /api/boxes?archived=true': [],
		'GET /api/boxes/summary': boxSummary({
			netBalance: 1_085,
			inBoxes: 0,
			availableToSpend: 1_085,
		}),
		'GET /api/dashboard/summary': summary({
			netBalance: 1_085,
			inBoxes: 0,
			availableToSpend: 1_085,
		}),
	},
};

/**
 * Phase 4's dashboard fixture, with the figures the execution plan fixes.
 *
 * `4,120.50 + 55.50 = 4,176.00`; `4,176.00 − 5,300.00 = −1,124.00`;
 * `9,000.00 + 55.50 = 9,055.50`. The 310.25 confirmed statement payment is a
 * separately identified obligation and is never subtracted from Net Balance;
 * available credit is limit-derived capacity and enters none of the totals.
 */
const dashboardNegativeAvailable: Scenario = {
	id: 'FX-DASH-NEG-01',
	description:
		'Money held 4,120.50 plus credit in favor 55.50 = Net 4,176.00, with 5,300.00 in Boxes (Available −1,124.00).',
	clock: MEXICO_CITY_MIDDAY,
	expected: {
		moneyHeld: 4_120.5,
		creditInFavor: 55.5,
		netBalance: 4_176,
		inBoxes: 5_300,
		availableToSpend: -1_124,
		outstandingStatementPayment: 310.25,
		creditLimit: 9_000,
		availableCredit: 9_055.5,
	},
	routes: {
		'GET /api/accounts/status': trackingStatus({
			active: true,
			setupRequired: false,
			activatedAt: '2026-01-15',
			transactionNetBalance: 4_176,
			accountNetBalance: 4_176,
		}),
		'GET /api/accounts': [
			account({ id: 9114, name: 'Cuenta sintética P4', kind: 'DEBIT', balance: 4_120.5 }),
			account({ id: 9115, name: 'Tarjeta sintética P4', kind: 'CREDIT', balance: 55.5 }),
		],
		'GET /api/accounts/9115/credit-settings': {
			creditLimit: 9_000,
			statementClosingDay: 5,
			paymentDueDay: 20,
		},
		'GET /api/accounts/9115/credit-statements': [
			{
				id: 9304,
				periodStart: '2026-08-06',
				periodEnd: '2026-09-05',
				dueDate: '2026-09-20',
				officialBalance: 310.25,
				officialMinimumPayment: 80,
				officialAvoidInterest: 310.25,
				officialNote: null,
				paidAmount: 0,
				outstandingBalance: 310.25,
				reconciliationMismatch: false,
				mismatchAmount: 0,
			},
		],
		'GET /api/boxes': [
			box({ id: 9216, name: 'Renta sintética P4', balance: 3_500, displayOrder: 1 }),
			box({ id: 9217, name: 'Colegiatura sintética P4', balance: 1_800, displayOrder: 2 }),
		],
		'GET /api/boxes?archived=true': [],
		'GET /api/boxes/9216/plans': [
			planSummary({ id: 9255, boxId: 9216, type: 'SPENDING_BUDGET', status: 'ACTIVE' }),
		],
		'GET /api/boxes/9217/plans': [
			planSummary({ id: 9256, boxId: 9217, type: 'SAVING_GOAL', status: 'ACTIVE' }),
		],
		'GET /api/boxes/summary': boxSummary({
			netBalance: 4_176,
			inBoxes: 5_300,
			availableToSpend: -1_124,
		}),
		'GET /api/dashboard/summary': summary({
			netBalance: 4_176,
			inBoxes: 5_300,
			availableToSpend: -1_124,
		}),
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

const recordingRows = Array.from({ length: 31 }, (_, index) => ({
 id: 9650 + index, amount: 10, direction: 'EGRESS', description: index === 30 ? 'Needle groceries' : `Synthetic purchase ${index + 1}`,
 transactionDate: '2026-09-07', categoryId: 9801, categoryName: 'Groceries', categoryHue: 120,
 contactId: null, contactName: null, accountId: 9101, accountName: 'Synthetic cash', accountKind: 'CASH',
 boxFunding: [], boxDistributions: [], availableToSpendAmount: 10,
}));
const recording: Scenario = {
 id: 'FX-RECORDING-01', description: 'Full-history search and optional Box funding with negative Available to Spend.',
 clock: MEXICO_CITY_MIDDAY, expected: { netBalance: 1900, inBoxes: 2600, availableToSpend: -700, transactionCount: 31 },
 routes: {
  ...emptyBoxNegativeAvailable.routes,
  'GET /api/categories': [{id:9801,name:'Groceries',type:'EGRESS',color:120},{id:9802,name:'Income',type:'INGRESS',color:220}],
  'GET /api/contacts': [], 'GET /api/account-transfers': [],
  'GET /api/accounts': [account({id:9101,name:'Synthetic cash',kind:'CASH',balance:1900})],
  'GET /api/transactions': recordingRows,
  'GET /api/transactions?page=0&pageSize=25&sortBy=transactionDate&sortDirection=desc': {items: recordingRows.slice(0,25),pageIndex:0,pageSize:25,totalItems:31,totalPages:2,sortBy:'transactionDate',sortDirection:'desc'},
  'GET /api/boxes/9205': box({id:9205,name:'Empty Box',balance:0,displayOrder:0}),
  'GET /api/boxes/9205/history': [], 'GET /api/boxes/9205/plans': [],
  'GET /api/funding-triggers/suggestions': [],
 },
};

const ALL: readonly Scenario[] = Object.freeze([
	recording,
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
	receivablesByDebtor,
	dateBoundaries,
	longNames,
	// Slice 0B
	planGoalActive,
	planGoalOverdue,
	planGoalCompleted,
	planBudgetUnderfunded,
	boxesNoPlan,
	subscriptionOwnerParticipating,
	subscriptionMiddleman,
	creditInFavorUnpaidStatement,
	dashboardNegativeAvailable,
]);

export type ScenarioId = (typeof ALL)[number]['id'];

const BY_ID = new Map(ALL.map((scenario) => [scenario.id, scenario]));

/** Every scenario ID, in manifest order. */
// Normal plan pages also read suggestion rules and income categories.
for (const scenario of ALL) {
 for (const key of Object.keys(scenario.routes)) {
  const match = key.match(/^GET \/api\/boxes\/(\d+)\/plans$/);
  if (match) {
   scenario.routes[`GET /api/boxes/${match[1]}/funding-triggers`] ??= [];
   scenario.routes['GET /api/categories'] ??= [];
  }
 }
}

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
