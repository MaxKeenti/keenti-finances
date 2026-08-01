export type TransactionDirection = 'INGRESS' | 'EGRESS';

export type BoxAllocationInput = {
	boxId: number;
	amount: number;
};

export type BoxFundingDto = BoxAllocationInput & {
	boxName: string;
	lineOrder: number;
};

export type BoxDistributionDto = BoxAllocationInput & {
	boxName: string;
	lineOrder: number;
	effectiveDate: string;
};

export type TransactionBoxFields = {
	boxFunding: BoxFundingDto[];
	boxDistributions: BoxDistributionDto[];
	/** The part of an EGRESS Transaction paid from Available to Spend. */
	availableToSpendAmount: number;
};

export function amountToCents(value: number | string | null | undefined): number {
	const amount = Number(value);
	return Number.isFinite(amount) ? Math.round(amount * 100) : 0;
}

export function centsToAmount(value: number): number {
	return value / 100;
}

export function allocationTotal(allocations: readonly BoxAllocationInput[]): number {
	return centsToAmount(
		allocations.reduce((total, allocation) => total + amountToCents(allocation.amount), 0),
	);
}

export function hasAtMostTwoDecimalPlaces(value: number): boolean {
	return Math.abs(value * 100 - Math.round(value * 100)) < 1e-7;
}

export function normalizeTransactionBoxFields<T extends { amount: number }>(
	transaction: T & Partial<TransactionBoxFields>,
): T & TransactionBoxFields {
	const boxFunding = Array.isArray(transaction.boxFunding) ? transaction.boxFunding : [];
	const boxDistributions = Array.isArray(transaction.boxDistributions)
		? transaction.boxDistributions
		: [];
	const fundedAmount = allocationTotal(boxFunding);
	const fallbackAvailable = Math.max(0, centsToAmount(amountToCents(transaction.amount) - amountToCents(fundedAmount)));

	return {
		...transaction,
		boxFunding,
		boxDistributions,
		availableToSpendAmount:
			typeof transaction.availableToSpendAmount === 'number'
				? transaction.availableToSpendAmount
				: fallbackAvailable,
	};
}
