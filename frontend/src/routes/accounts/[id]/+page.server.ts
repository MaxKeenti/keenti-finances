import { error, fail } from '@sveltejs/kit';
import { getSession } from '$lib/server/workos-session';
import type { Actions, PageServerLoad } from './$types';

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

type Account = {
	id: number;
	name: string;
	kind: string;
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

type CreditSettings = { creditLimit: number; statementClosingDay: number; paymentDueDay: number };
type CreditStatement = { id: number; dueDate: string; officialBalance: number; officialMinimumPayment: number; officialAvoidInterest: number; paidAmount: number; outstandingBalance: number };

function headers(cookies: Parameters<typeof getSession>[0], json = false): Record<string, string> {
	const token = getSession(cookies)?.accessToken;
	return { ...(json ? { 'content-type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) };
}

export const load: PageServerLoad = async ({ params, fetch, cookies }) => {
	const id = Number(params.id);
	if (!Number.isInteger(id) || id <= 0) error(404, 'Financial Account not found');

	const auth = headers(cookies);
	const accountRes = await fetch(`${BACKEND}/api/accounts/${id}`, { headers: auth });
	if (!accountRes.ok) error(accountRes.status === 404 ? 404 : 502, 'Financial Account not found');
	const account = await accountRes.json() as Account;

	const [transactionsRes, transfersRes, settingsRes, statementsRes, msiPlansRes] = await Promise.all([
		fetch(`${BACKEND}/api/transactions`, { headers: auth }),
		fetch(`${BACKEND}/api/account-transfers`, { headers: auth }),
		account.kind === 'CREDIT' ? fetch(`${BACKEND}/api/accounts/${id}/credit-settings`, { headers: auth }) : Promise.resolve(null),
		account.kind === 'CREDIT' ? fetch(`${BACKEND}/api/accounts/${id}/credit-statements`, { headers: auth }) : Promise.resolve(null),
		account.kind === 'CREDIT' ? fetch(`${BACKEND}/api/accounts/${id}/msi-plans`, { headers: auth }) : Promise.resolve(null),
	]);
	const transactions = transactionsRes.ok ? await transactionsRes.json() as Transaction[] : [];
	const transfers = transfersRes.ok ? await transfersRes.json() as Transfer[] : [];
	const activity: Activity[] = [
		...transactions
			.filter((transaction) => transaction.accountId === id)
			.map((transaction) => ({
				id: `transaction-${transaction.id}`,
				type: 'TRANSACTION' as const,
				date: transaction.transactionDate,
				title: transaction.description || transaction.categoryName || 'Transaction',
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
						? `Transfer to ${transfer.destinationAccountName ?? 'Archived account'}`
						: `Transfer from ${transfer.sourceAccountName ?? 'Archived account'}`,
					detail: transfer.notes,
					amount: outgoing ? -transfer.amount : transfer.amount,
				};
			}),
	].sort((left, right) => right.date.localeCompare(left.date));

	const settings = settingsRes?.ok ? await settingsRes.json() as CreditSettings : null;
	const statements = statementsRes?.ok ? await statementsRes.json() as CreditStatement[] : [];
	const nextStatement = statements
		.filter((statement) => statement.outstandingBalance > 0)
		.sort((left, right) => left.dueDate.localeCompare(right.dueDate))[0] ?? null;
	const msiPlans = msiPlansRes?.ok ? await msiPlansRes.json() : [];
	const creditTransactions = transactions.filter((transaction) => transaction.accountId === id && transaction.direction === 'EGRESS');
	return { account, activity, credit: account.kind === 'CREDIT' ? { settings, statements, nextStatement, msiPlans, creditTransactions } : null };
};

export const actions: Actions = {
	archive: async ({ params, fetch, cookies }) => {
		const response = await fetch(`${BACKEND}/api/accounts/${params.id}/archive`, {
			method: 'POST', headers: headers(cookies, true),
		});
		if (!response.ok) {
			return fail(response.status === 409 ? 409 : 400, {
				message: response.status === 409
					? 'Settle all confirmed Credit Statements before archiving this account.'
					: 'Bring the account balance to zero before archiving it.',
			});
		}
		return { archived: true };
	},
	restore: async ({ params, fetch, cookies }) => {
		const response = await fetch(`${BACKEND}/api/accounts/${params.id}/restore`, {
			method: 'POST', headers: headers(cookies, true),
		});
		if (!response.ok) {
			return fail(response.status === 409 ? 409 : 400, {
				message: 'An active account with this name already exists.',
			});
		}
		return { restored: true };
	},
	createMsiPlan: async ({ params, request, fetch, cookies }) => {
		const data = await request.formData();
		const response = await fetch(`${BACKEND}/api/accounts/${params.id}/msi-plans`, {
			method: 'POST', headers: headers(cookies, true), body: JSON.stringify({
				transactionId: Number(data.get('transactionId')), installmentCount: Number(data.get('installmentCount')),
				firstInstallmentDate: data.get('firstInstallmentDate'),
			}),
		});
		if (!response.ok) return fail(response.status === 409 ? 409 : 400, { message: 'The MSI plan could not be created. Use a credit-account expense whose amount divides evenly into the selected installments.' });
		return { msiCreated: true };
	},
};
