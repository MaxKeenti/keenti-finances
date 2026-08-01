import { amountToCents, centsToAmount, type BoxAllocationInput } from '$lib/types/transactions';

export type FundingTriggerStrategy = 'PLAN_DERIVED' | 'FIXED_AMOUNT' | 'PERCENTAGE';

export type FundingTriggerDto = {
	id: number;
	boxId: number;
	boxName: string;
	categoryId: number;
	categoryName: string;
	strategy: FundingTriggerStrategy;
	fixedAmount: number | null;
	percentage: number | null;
	enabled: boolean;
	createdAt: string;
	updatedAt: string;
};

export type FundingTriggerInput = {
	categoryId: number;
	strategy: FundingTriggerStrategy;
	fixedAmount?: number;
	percentage?: number;
	enabled?: boolean;
};

export type FundingSuggestionDto = {
	triggerId: number;
	boxId: number;
	boxName: string;
	strategy: FundingTriggerStrategy;
	suggestedAmount: number;
};

export type FundingSuggestionSetDto = {
	categoryId: number;
	ingressAmount: number;
	suggestions: FundingSuggestionDto[];
	combinedTotal: number;
};

export type EditableFundingSuggestion = FundingSuggestionDto & {
	selected: boolean;
	amount: number;
	edited: boolean;
};

export function selectedFundingTotal(
	suggestions: readonly Pick<EditableFundingSuggestion, 'selected' | 'amount'>[],
): number {
	return centsToAmount(
		suggestions.reduce(
			(total, suggestion) =>
				suggestion.selected ? total + amountToCents(suggestion.amount) : total,
			0,
		),
	);
}

export function fundingExcess(selectedTotal: number, availableToSpend: number): number {
	return centsToAmount(
		Math.max(0, amountToCents(selectedTotal) - amountToCents(availableToSpend)),
	);
}

/**
 * Replaces only allocations previously managed by the suggestion editor.
 * Manually-added allocations to unrelated Boxes remain intact.
 */
export function mergeFundingSuggestionAllocations(
	allocations: readonly BoxAllocationInput[],
	previousManagedBoxIds: ReadonlySet<number>,
	suggestions: readonly Pick<EditableFundingSuggestion, 'boxId' | 'selected' | 'amount'>[],
): BoxAllocationInput[] {
	const selected = suggestions.filter((suggestion) => suggestion.selected);
	const selectedIds = new Set(selected.map((suggestion) => suggestion.boxId));
	const preserved = allocations.filter(
		(allocation) =>
			!previousManagedBoxIds.has(allocation.boxId) && !selectedIds.has(allocation.boxId),
	);
	return [
		...preserved,
		...selected.map((suggestion) => ({
			boxId: suggestion.boxId,
			amount: centsToAmount(amountToCents(suggestion.amount)),
		})),
	];
}
