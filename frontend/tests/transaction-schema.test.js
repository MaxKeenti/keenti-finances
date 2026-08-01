// @ts-nocheck
import { describe, expect, test } from 'bun:test';
import { transactionSchema } from '../src/lib/schemas/transaction';

describe('Transaction schema', () => {
	test('preserves the empty optional contact sentinel during client validation', () => {
		const result = transactionSchema.parse({
			amount: 3_000,
			direction: 'INGRESS',
			description: 'Salary',
			transactionDate: '2026-07-31',
			categoryId: 1,
			contactId: '',
			boxFunding: [],
			boxDistributions: [],
		});

		expect(result.contactId).toBe('');
	});
});
