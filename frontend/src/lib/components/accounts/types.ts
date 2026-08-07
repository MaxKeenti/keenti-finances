export type Account = {
	id: number;
	name: string;
	kind: string;
	balance: number;
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

export type CreditDetail = {
	settings: { creditLimit: number; statementClosingDay: number; paymentDueDay: number } | null;
	statements: CreditStatement[];
};
