// @ts-nocheck
import { describe, expect, test } from 'bun:test';
import {
	boxMovementTransactionSourceState,
	hasClickableBoxMovementTransaction,
	isCorrectableBoxMovement,
} from '../src/lib/types/boxes';

describe('Box movement correction helpers', () => {
	test('allows internal movements but never linked spending', () => {
		for (const type of ['DEPOSIT', 'WITHDRAWAL', 'TRANSFER_IN', 'TRANSFER_OUT']) {
			expect(isCorrectableBoxMovement(type)).toBe(true);
		}
		expect(isCorrectableBoxMovement('SPENDING')).toBe(false);
	});

	test('labels changed and removed income sources with removed taking precedence', () => {
		expect(
			boxMovementTransactionSourceState({
				relatedTransactionId: null,
				relatedTransactionChanged: false,
				relatedTransactionRemoved: false,
			}),
		).toBe('NONE');
		expect(
			boxMovementTransactionSourceState({
				relatedTransactionId: 10,
				relatedTransactionChanged: false,
				relatedTransactionRemoved: false,
			}),
		).toBe('CURRENT');
		expect(
			boxMovementTransactionSourceState({
				relatedTransactionId: 10,
				relatedTransactionChanged: true,
				relatedTransactionRemoved: false,
			}),
		).toBe('CHANGED');
		expect(
			boxMovementTransactionSourceState({
				relatedTransactionId: 10,
				relatedTransactionChanged: true,
				relatedTransactionRemoved: true,
			}),
		).toBe('REMOVED');
	});

	test('never exposes a link for a removed source transaction', () => {
		expect(
			hasClickableBoxMovementTransaction({
				relatedTransactionId: 10,
				relatedTransactionRemoved: false,
			}),
		).toBe(true);
		expect(
			hasClickableBoxMovementTransaction({
				relatedTransactionId: 10,
				relatedTransactionRemoved: true,
			}),
		).toBe(false);
		expect(
			hasClickableBoxMovementTransaction({
				relatedTransactionId: null,
				relatedTransactionRemoved: false,
			}),
		).toBe(false);
	});
});
