import { z } from 'zod';
import { m } from '$lib/paraglide/messages.js';
import { allocationTotal, hasAtMostTwoDecimalPlaces } from '$lib/types/transactions';

const MAX_AMOUNT = 9_999_999_999.99;

const boxAllocationSchema = z.object({
	boxId: z.coerce.number().int().positive(m.transactions_box_required()),
	amount: z.coerce
		.number()
		.positive(m.validation_amount_positive())
		.max(MAX_AMOUNT, m.transactions_box_amount_too_large())
		.refine(hasAtMostTwoDecimalPlaces, m.transactions_box_amount_precision()),
});

function addDuplicateIssues(
	allocations: Array<{ boxId: number }>,
	path: 'boxFunding' | 'boxDistributions',
	context: z.RefinementCtx,
) {
	const seen = new Set<number>();
	for (let index = 0; index < allocations.length; index += 1) {
		const boxId = allocations[index]?.boxId;
		if (seen.has(boxId)) {
			context.addIssue({
				code: 'custom',
				message: m.transactions_box_duplicate(),
				path: [path, index, 'boxId'],
			});
		}
		seen.add(boxId);
	}
}

export const transactionSchema = z
	.object({
		id: z.coerce.number().optional(),
		amount: z.coerce.number().positive(m.validation_amount_positive()),
		direction: z.enum(['INGRESS', 'EGRESS']),
		description: z.string().max(500).optional(),
		transactionDate: z.string().min(1, m.validation_date_required()),
		categoryId: z.coerce.number().min(1, m.validation_category_required()),
		// Keep the empty sentinel ahead of the coercing branch. `z.coerce.number()`
		// turns an empty string into 0, which made the optional contact select
		// visibly change from "None" to "0" whenever client validation reran.
		contactId: z.union([z.literal(''), z.coerce.number()]).optional(),
		boxFunding: z.array(boxAllocationSchema).default([]),
		boxDistributions: z.array(boxAllocationSchema).default([]),
	})
	.superRefine((value, context) => {
		addDuplicateIssues(value.boxFunding, 'boxFunding', context);
		addDuplicateIssues(value.boxDistributions, 'boxDistributions', context);

		if (value.direction !== 'EGRESS' && value.boxFunding.length > 0) {
			context.addIssue({
				code: 'custom',
				message: m.transactions_box_funding_egress_only(),
				path: ['boxFunding'],
			});
		}
		if (value.direction !== 'INGRESS' && value.boxDistributions.length > 0) {
			context.addIssue({
				code: 'custom',
				message: m.transactions_box_distribution_ingress_only(),
				path: ['boxDistributions'],
			});
		}
		if (allocationTotal(value.boxFunding) > value.amount) {
			context.addIssue({
				code: 'custom',
				message: m.transactions_box_funding_over_total(),
				path: ['boxFunding'],
			});
		}
		if (value.id && value.boxDistributions.length > 0) {
			context.addIssue({
				code: 'custom',
				message: m.transactions_box_distribution_create_only(),
				path: ['boxDistributions'],
			});
		}
	});

export type TransactionFormData = z.infer<typeof transactionSchema>;
