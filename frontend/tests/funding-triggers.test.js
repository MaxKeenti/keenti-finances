// @ts-nocheck
import { describe, expect, test } from 'bun:test';
import {
	fundingExcess,
	mergeFundingSuggestionAllocations,
	selectedFundingTotal,
} from '../src/lib/types/funding-triggers';

function suggestion(boxId, amount, selected = true) {
	return {
		triggerId: boxId,
		boxId,
		boxName: `Box ${boxId}`,
		strategy: 'FIXED_AMOUNT',
		suggestedAmount: amount,
		amount,
		selected,
		edited: false,
	};
}

describe('Funding Trigger allocation helpers', () => {
	test('totals selected suggestions at MXN cent precision and reports excess', () => {
		const suggestions = [suggestion(1, 0.1), suggestion(2, 0.2), suggestion(3, 50, false)];

		expect(selectedFundingTotal(suggestions)).toBe(0.3);
		expect(fundingExcess(100.01, 100)).toBe(0.01);
		expect(fundingExcess(99.99, 100)).toBe(0);
	});

	test('replaces suggestion-managed lines while preserving manual allocations', () => {
		const result = mergeFundingSuggestionAllocations(
			[
				{ boxId: 1, amount: 10 },
				{ boxId: 9, amount: 25 },
			],
			new Set([1]),
			[suggestion(1, 12.34), suggestion(2, 50, false)],
		);

		expect(result).toEqual([
			{ boxId: 9, amount: 25 },
			{ boxId: 1, amount: 12.34 },
		]);
	});

	test('selecting a suggestion replaces a manual line for the same Box', () => {
		const result = mergeFundingSuggestionAllocations(
			[{ boxId: 2, amount: 7 }],
			new Set(),
			[suggestion(2, 8.5)],
		);

		expect(result).toEqual([{ boxId: 2, amount: 8.5 }]);
	});
});
