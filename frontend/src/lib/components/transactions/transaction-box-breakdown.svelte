<script lang="ts">
	import { Badge } from '$lib/components/ui/badge';
	import { mxnFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
	import {
		allocationTotal,
		type BoxDistributionDto,
		type BoxFundingDto,
		type TransactionDirection,
	} from '$lib/types/transactions';

	let {
		direction,
		amount,
		boxFunding = [],
		boxDistributions = [],
		availableToSpendAmount,
		locale,
		showEmpty = false,
	}: {
		direction: TransactionDirection;
		amount: number;
		boxFunding?: BoxFundingDto[];
		boxDistributions?: BoxDistributionDto[];
		availableToSpendAmount?: number;
		locale: string;
		showEmpty?: boolean;
	} = $props();

	const fmt = $derived(mxnFormatter(locale));
	const freeAmount = $derived(
		typeof availableToSpendAmount === 'number'
			? availableToSpendAmount
			: Math.max(0, amount - allocationTotal(boxFunding)),
	);
	const hasBreakdown = $derived(
		(direction === 'EGRESS' && (boxFunding.length > 0 || freeAmount > 0)) ||
			boxDistributions.length > 0,
	);
</script>

{#if hasBreakdown || showEmpty}
	<div class="flex flex-wrap items-center gap-1.5" aria-label={m.transactions_box_allocations_title()}>
		{#if direction === 'EGRESS'}
			{#each boxFunding as funding (`${funding.boxId}-${funding.lineOrder}`)}
				<Badge href={`/boxes/${funding.boxId}`} variant="secondary">
					{funding.boxName}: {fmt.format(funding.amount)}
				</Badge>
			{/each}
			{#if freeAmount > 0}
				<Badge variant="outline">{m.transactions_box_free_badge({ amount: fmt.format(freeAmount) })}</Badge>
			{/if}
			{#if boxFunding.length === 0 && freeAmount <= 0 && boxDistributions.length === 0 && showEmpty}
				<span class="text-sm text-muted-foreground">{m.transactions_box_no_funding()}</span>
			{/if}
		{/if}
		{#each boxDistributions as distribution (`${distribution.boxId}-${distribution.lineOrder}`)}
			<Badge href={`/boxes/${distribution.boxId}`} variant="success">
				{direction === 'EGRESS'
					? m.transactions_box_deposit_badge({
							name: distribution.boxName,
							amount: fmt.format(distribution.amount),
						})
					: `${distribution.boxName}: ${fmt.format(distribution.amount)}`}
			</Badge>
		{/each}
		{#if direction === 'INGRESS' && boxDistributions.length === 0 && showEmpty}
			<span class="text-sm text-muted-foreground">{m.transactions_box_no_distributions()}</span>
		{/if}
	</div>
{/if}
