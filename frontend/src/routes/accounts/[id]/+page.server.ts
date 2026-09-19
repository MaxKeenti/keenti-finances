import { error, fail } from '@sveltejs/kit';
import { getSession } from '$lib/server/workos-session';
import type { Actions, PageServerLoad } from './$types';
import { m } from '$lib/paraglide/messages.js';
import { loadOptionalSection, loadSection } from '$lib/server/section-load';
import {
	parseCreditSettingsDetail,
	parseCurrentCreditEstimate,
	parseCreditStatements,
	type CreditSettingsDetail,
	type CreditStatementSummary,
} from '$lib/server/payloads';
import { sectionOk } from '$lib/types/section';

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

type Account = {
	id: number;
	name: string;
	kind: string;
	hue: number;
	openingBalance: number;
	openingDate: string;
	balance: number;
	archived: boolean;
};

type Transaction = {
	id: number;
	amount: number;
	direction: 'INGRESS' | 'EGRESS';
	description: string | null;
	transactionDate: string;
	categoryName: string | null;
	accountId: number | null;
};

type Transfer = {
	id: number;
	sourceAccountId: number;
	destinationAccountId: number;
	sourceAccountName: string | null;
	destinationAccountName: string | null;
	amount: number;
	transferDate: string;
	notes: string | null;
};

type Activity = {
	id: string;
	type: 'TRANSACTION' | 'TRANSFER';
	date: string;
	title: string;
	detail: string | null;
	amount: number;
};

function headers(cookies: Parameters<typeof getSession>[0], json = false): Record<string, string> {
	const token = getSession(cookies)?.accessToken;
	return {
		...(json ? { 'content-type': 'application/json' } : {}),
		...(token ? { Authorization: `Bearer ${token}` } : {}),
	};
}

export const load: PageServerLoad = async ({ params, fetch, cookies }) => {
	const id = Number(params.id);
	if (!Number.isInteger(id) || id <= 0) error(404, m.error_account_not_found());

	const auth = headers(cookies);
	const accountRes = await fetch(`${BACKEND}/api/accounts/${id}`, {
		headers: auth,
	});
	if (!accountRes.ok) error(accountRes.status === 404 ? 404 : 502, m.error_account_not_found());
	const account = (await accountRes.json()) as Account;

	const isCredit = account.kind === 'CREDIT';
	const [transactionsRes, transfersRes, settingsSection, statementsSection, msiPlansRes, currentEstimateRes] =
		await Promise.all([
			fetch(`${BACKEND}/api/transactions`, { headers: auth }),
			fetch(`${BACKEND}/api/account-transfers`, { headers: auth }),
			// A Credit Financial Account with nothing configured yet answers 404.
			// That is an absence, not a failure — there is no limit, so available
			// credit is genuinely unknown rather than unreadable.
			isCredit
				? loadOptionalSection<CreditSettingsDetail>(
						fetch,
						`${BACKEND}/api/accounts/${id}/credit-settings`,
						{
							parse: parseCreditSettingsDetail,
							headers: auth,
							label: `account/credit-settings/${id}`,
							absentStatuses: [404],
						},
					)
				: Promise.resolve(sectionOk<CreditSettingsDetail | null>(null)),
			// Confirmed statements carry bank-issued figures the User pays
			// against. An unreadable list must stay unavailable: an empty list
			// would read as "nothing left to pay".
			isCredit
				? loadSection<CreditStatementSummary[]>(
						fetch,
						`${BACKEND}/api/accounts/${id}/credit-statements`,
						{
							parse: parseCreditStatements,
							headers: auth,
							label: `account/credit-statements/${id}`,
						},
					)
				: Promise.resolve(sectionOk<CreditStatementSummary[]>([])),
			isCredit ? fetch(`${BACKEND}/api/accounts/${id}/msi-plans`, { headers: auth }) : Promise.resolve(null),
			isCredit
				? loadOptionalSection(fetch, `${BACKEND}/api/accounts/${id}/credit-statements/current-estimate`, {
					parse: parseCurrentCreditEstimate, headers: auth, label: `account/estimate/${id}`,
					absentStatuses: [409],
				})
				: Promise.resolve(null),
		]);
	const transactions = transactionsRes.ok ? ((await transactionsRes.json()) as Transaction[]) : [];
	const transfers = transfersRes.ok ? ((await transfersRes.json()) as Transfer[]) : [];
	const activity: Activity[] = [
		...transactions
			.filter((transaction) => transaction.accountId === id)
			.map((transaction) => ({
				id: `transaction-${transaction.id}`,
				type: 'TRANSACTION' as const,
				date: transaction.transactionDate,
				title: transaction.description || transaction.categoryName || m.entity_transaction(),
				detail: transaction.categoryName,
				amount: transaction.direction === 'INGRESS' ? transaction.amount : -transaction.amount,
			})),
		...transfers
			.filter((transfer) => transfer.sourceAccountId === id || transfer.destinationAccountId === id)
			.map((transfer) => {
				const outgoing = transfer.sourceAccountId === id;
				return {
					id: `transfer-${transfer.id}`,
					type: 'TRANSFER' as const,
					date: transfer.transferDate,
					title: outgoing
						? m.account_activity_transfer_to({ name: transfer.destinationAccountName ?? m.transfer_archived_account() })
						: m.account_activity_transfer_from({ name: transfer.sourceAccountName ?? m.transfer_archived_account() }),
					detail: transfer.notes,
					amount: outgoing ? -transfer.amount : transfer.amount,
				};
			}),
	].sort((left, right) => right.date.localeCompare(left.date));

	const msiPlans = msiPlansRes?.ok ? await msiPlansRes.json() : [];
	// The estimate is Keenti's own projection from recorded activity. It is a
	// separate read from the confirmed snapshots and stays labeled as an
	// estimate; a failed one is withheld rather than shown as a figure.
	const currentEstimate = currentEstimateRes?.status === 'ok' ? currentEstimateRes.data : null;
	const estimateAvailable = currentEstimate !== null;
	// A successful settings absence and the expected schedule conflict are a
	// setup state, not an outage. Never infer absence from a failed settings read.
	const statementScheduleUnconfigured = settingsSection.status === 'ok' && settingsSection.data === null
		&& currentEstimateRes?.status === 'ok' && currentEstimateRes.data === null;
	const creditTransactions = transactions.filter((transaction) => transaction.accountId === id && transaction.direction === 'EGRESS');
	return {
		account,
		activity,
		credit: isCredit
			? {
					settings: settingsSection,
					statements: statementsSection,
					msiPlans,
					currentEstimate,
					estimateAvailable,
					statementScheduleUnconfigured,
					creditTransactions,
				}
			: null,
	};
};

export const actions: Actions = {
	updateAppearance: async ({ params, request, fetch, cookies }) => {
		const data = await request.formData();
		const hue = Number(data.get('hue'));
		if (!Number.isInteger(hue) || hue < 0 || hue > 359) return fail(400, { message: m.error_account_colour_invalid() });
		const response = await fetch(`${BACKEND}/api/accounts/${params.id}/appearance`, {
			method: 'PUT',
			headers: headers(cookies, true),
			body: JSON.stringify({ hue }),
		});
		if (!response.ok)
			return fail(response.status === 404 ? 404 : 400, {
				message: m.error_account_colour_save(),
			});
		return { appearanceUpdated: true };
	},
	saveCreditSettings: async ({ params, request, fetch, cookies }) => {
		const data = await request.formData();
		const response = await fetch(`${BACKEND}/api/accounts/${params.id}/credit-settings`, {
			method: 'PUT',
			headers: headers(cookies, true),
			body: JSON.stringify({
				creditLimit: Number(data.get('creditLimit')),
				statementClosingDay: Number(data.get('statementClosingDay')),
				paymentDueDay: Number(data.get('paymentDueDay')),
			}),
		});
		if (!response.ok) return fail(400, { message: m.error_credit_settings_save() });
		return { creditSettingsSaved: true };
	},
	confirmCreditStatement: async ({ params, request, fetch, cookies }) => {
		const data = await request.formData();
		const response = await fetch(`${BACKEND}/api/accounts/${params.id}/credit-statements`, {
			method: 'POST',
			headers: headers(cookies, true),
			body: JSON.stringify({
				periodStart: data.get('periodStart'),
				periodEnd: data.get('periodEnd'),
				dueDate: data.get('dueDate'),
				officialBalance: Number(data.get('officialBalance')),
				officialMinimumPayment: Number(data.get('officialMinimumPayment')),
				officialAvoidInterest: Number(data.get('officialAvoidInterest')),
				officialNote: String(data.get('officialNote') ?? '') || null,
			}),
		});
		if (!response.ok)
			return fail(response.status === 409 ? 409 : 400, {
				message: m.error_statement_confirm(),
			});
		return { statementConfirmed: true };
	},
	reconfirmCreditStatement: async ({ params, request, fetch, cookies }) => {
		const data = await request.formData();
		const statementId = Number(data.get('statementId'));
		const response = await fetch(`${BACKEND}/api/accounts/${params.id}/credit-statements/${statementId}/reconfirm`, {
			method: 'POST',
			headers: headers(cookies, true),
			body: JSON.stringify({
				periodStart: data.get('periodStart'),
				periodEnd: data.get('periodEnd'),
				dueDate: data.get('dueDate'),
				officialBalance: Number(data.get('officialBalance')),
				officialMinimumPayment: Number(data.get('officialMinimumPayment')),
				officialAvoidInterest: Number(data.get('officialAvoidInterest')),
				officialNote: String(data.get('officialNote') ?? '') || null,
			}),
		});
		if (!response.ok)
			return fail(response.status === 409 ? 409 : 400, {
				message: m.error_statement_reconfirm(),
			});
		return { statementReconfirmed: true };
	},
	archive: async ({ params, fetch, cookies }) => {
		const response = await fetch(`${BACKEND}/api/accounts/${params.id}/archive`, {
			method: 'POST',
			headers: headers(cookies, true),
		});
		if (!response.ok) {
			return fail(response.status === 409 ? 409 : 400, {
				message: response.status === 409 ? 'Settle all confirmed Credit Statements before archiving this account.' : m.account_archive_zero_required(),
			});
		}
		return { archived: true };
	},
	restore: async ({ params, fetch, cookies }) => {
		const response = await fetch(`${BACKEND}/api/accounts/${params.id}/restore`, {
			method: 'POST',
			headers: headers(cookies, true),
		});
		if (!response.ok) {
			return fail(response.status === 409 ? 409 : 400, {
				message: m.error_account_exists(),
			});
		}
		return { restored: true };
	},
	createMsiPlan: async ({ params, request, fetch, cookies }) => {
		const data = await request.formData();
		const response = await fetch(`${BACKEND}/api/accounts/${params.id}/msi-plans`, {
			method: 'POST',
			headers: headers(cookies, true),
			body: JSON.stringify({
				transactionId: Number(data.get('transactionId')),
				installmentCount: Number(data.get('installmentCount')),
				firstInstallmentDate: data.get('firstInstallmentDate'),
			}),
		});
		if (!response.ok)
			return fail(response.status === 409 ? 409 : 400, {
				message: m.error_msi_create(),
			});
		return { msiCreated: true };
	},
	endMsiPlan: async ({ params, request, fetch, cookies }) => {
		const data = await request.formData();
		const response = await fetch(`${BACKEND}/api/accounts/${params.id}/msi-plans/${Number(data.get('planId'))}/end`, {
			method: 'POST',
			headers: headers(cookies, true),
			body: JSON.stringify({ reason: data.get('reason') }),
		});
		if (!response.ok)
			return fail(response.status === 409 ? 409 : 400, {
				message: m.error_msi_end(),
			});
		return { msiEnded: true };
	},
};
