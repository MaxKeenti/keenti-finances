import type { Section } from '$lib/types/section';

export type Account = {
	id: number;
	name: string;
	kind: string;
	hue: number;
	balance: number;
	archived?: boolean;
};

export type Transfer = {
	id: number;
	sourceAccountId: number;
	sourceAccountName: string | null;
	destinationAccountId: number;
	destinationAccountName: string | null;
	amount: number;
	transferDate: string;
	notes: string | null;
};

export type CreditStatement = {
	id: number;
	periodStart: string;
	periodEnd: string;
	/** `null` when the statement carries no usable due date (decision D2). */
	dueDate: string | null;
	officialBalance: number;
	officialMinimumPayment: number;
	officialAvoidInterest: number;
	officialNote: string | null;
	paidAmount: number;
	outstandingBalance: number;
	reconciliationMismatch: boolean;
	mismatchAmount: number;
};

/**
 * The Credit reads, each as its own section.
 *
 * Settings and statements fail independently, and both distinguish "not
 * configured"/"none confirmed" from "could not be read" — a Credit Financial
 * Account with no saved settings is a normal state, while an unreadable one
 * must not be presented as having no limit and nothing owed.
 */
export type CreditDetail = {
	settings: Section<{
		creditLimit: number;
		statementClosingDay: number;
		paymentDueDay: number;
	} | null>;
	statements: Section<CreditStatement[]>;
};
