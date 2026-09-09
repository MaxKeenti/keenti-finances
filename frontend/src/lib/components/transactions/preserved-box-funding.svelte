<script lang="ts">
	/**
	 * The Box funding already saved on a Transaction, shown read-only.
	 *
	 * Editing funding needs Available to Spend; when that total is unavailable the
	 * editor is withheld. The saved allocations are still submitted unchanged, so
	 * they are shown here rather than disappearing from the form. Lowering the
	 * Transaction amount below what the Boxes already funded would leave the
	 * Transaction unpayable by its own funding, so that case says why the amount
	 * is rejected instead of only disabling the save.
	 */
	import { SectionUnavailable } from '$lib/components/section-status';
	import { mxnFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
	import {
		allocationTotal,
		amountToCents,
		type BoxAllocationInput,
	} from '$lib/types/transactions';

	let {
		allocations,
		boxes,
		transactionAmount,
		locale,
	}: {
		allocations: BoxAllocationInput[];
		boxes: Array<{ id: number; name: string }>;
		transactionAmount: number;
		locale: string;
	} = $props();

	const fmt = $derived(mxnFormatter(locale));
	const total = $derived(allocationTotal(allocations));
	const belowSaved = $derived(amountToCents(total) > amountToCents(transactionAmount));

	function boxName(id: number): string {
		return boxes.find((box) => box.id === id)?.name ?? `#${id}`;
	}
</script>

<div class="space-y-2">
	<SectionUnavailable
		compact
		title={m.section_balance_unavailable()}
		description={m.section_box_funding_unavailable()}
	/>

	{#if allocations.length > 0}
		<div class="space-y-2 rounded-lg border p-3">
			<div>
				<p class="text-sm font-medium">{m.transactions_box_funding_preserved_title()}</p>
				<p class="text-xs text-muted-foreground">
					{m.transactions_box_funding_preserved_description()}
				</p>
			</div>
			<ul class="space-y-1 text-sm">
				{#each allocations as allocation (allocation.boxId)}
					<li class="flex items-center justify-between gap-4">
						<span class="min-w-0 truncate">{boxName(allocation.boxId)}</span>
						<span class="shrink-0 tabular-nums">{fmt.format(allocation.amount)}</span>
					</li>
				{/each}
			</ul>
			<p class="border-t pt-2 text-sm font-medium tabular-nums">
				{m.transactions_box_funding_preserved_total({ amount: fmt.format(total) })}
			</p>
			{#if belowSaved}
				<p class="text-sm text-destructive">
					{m.transactions_box_funding_below_saved({ amount: fmt.format(total) })}
				</p>
			{/if}
		</div>
	{/if}
</div>
