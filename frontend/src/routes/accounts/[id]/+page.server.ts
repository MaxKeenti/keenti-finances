import { error, fail } from '@sveltejs/kit';
import { getSession } from '$lib/server/workos-session';
import type { Actions, PageServerLoad } from './$types';
import { m } from '$lib/paraglide/messages.js';

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

type CreditSettings = {
	creditLimit: number;
	statementClosingDay: number;
	paymentDueDay: number;
};
type CreditStatement = {
	id: number;
	periodStart: string;
	periodEnd: string;
	dueDate: string;
	officialBalance: number;
	officialMinimumPayment: number;
	officialAvoidInterest: number;
	officialNote: string | null;
	paidAmount: number;
	outstandingBalance: number;
	reconciliationMismatch: boolean;
	mismatchAmount: number;
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

	const [transactionsRes, transfersRes, settingsRes, statementsRes, msiPlansRes, currentEstimateRes] = await Promise.all([
		fetch(`${BACKEND}/api/transactions`, { headers: auth }),
		fetch(`${BACKEND}/api/account-transfers`, { headers: auth }),
		account.kind === 'CREDIT'
			? fetch(`${BACKEND}/api/accounts/${id}/credit-settings`, {
					headers: auth,
				})
			: Promise.resolve(null),
		account.kind === 'CREDIT'
			? fetch(`${BACKEND}/api/accounts/${id}/credit-statements`, {
					headers: auth,
				})
			: Promise.resolve(null),
		account.kind === 'CREDIT' ? fetch(`${BACKEND}/api/accounts/${id}/msi-plans`, { headers: auth }) : Promise.resolve(null),
		account.kind === 'CREDIT' ? fetch(`${BACKEND}/api/accounts/${id}/credit-statements/current-estimate`, { headers: auth }) : Promise.resolve(null),
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

	const settings = settingsRes?.ok ? ((await settingsRes.json()) as CreditSettings) : null;
	const statements = statementsRes?.ok ? ((await statementsRes.json()) as CreditStatement[]) : [];
	const nextStatement = statements.filter((statement) => statement.outstandingBalance > 0).sort((left, right) => left.dueDate.localeCompare(right.dueDate))[0] ?? null;
	const msiPlans = msiPlansRes?.ok ? await msiPlansRes.json() : [];
	const currentEstimate = currentEstimateRes?.ok ? await currentEstimateRes.json() : null;
	const creditTransactions = transactions.filter((transaction) => transaction.accountId === id && transaction.direction === 'EGRESS');
	return {
		account,
		activity,
		credit:
			account.kind === 'CREDIT'
				? {
						settings,
						statements,
						nextStatement,
						msiPlans,
						currentEstimate,
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
