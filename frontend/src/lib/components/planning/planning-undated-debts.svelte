<script lang="ts">
	/**
	 * Undated Debts in both Debt Directions (ADR-0023).
	 *
	 * Listed, never deducted or added: a Debt has no date, so it cannot be
	 * placed in the window. The two sides stay apart and are never netted, even
	 * for the same Contact, and no side total is invented here.
	 */
	import type { LabelContext } from '$lib/planning-labels';
	import type { PreviewUndatedDebt } from '$lib/planning-preview';
	import { m } from '$lib/paraglide/messages.js';

	let { debts, context }: { debts: PreviewUndatedDebt[] | null; context: LabelContext } = $props();

	const sides = $derived(
		debts === null
			? null
			: [
					{
						id: 'in',
						title: m.debts_side_owed_to_you(),
						rows: debts.filter((debt) => debt.direction === 'INGRESS'),
					},
					{
						id: 'out',
						title: m.debts_side_you_owe(),
						rows: debts.filter((debt) => debt.direction === 'EGRESS'),
					},
				],
	);
</script>

<section class="space-y-3" aria-labelledby="planning-debts-heading">
	<div class="space-y-1">
		<h3 id="planning-debts-heading" class="font-heading text-base font-semibold">
			{m.planning_debts_title()}
		</h3>
		<p class="text-xs text-muted-foreground">{m.planning_debts_description()}</p>
	</div>

	{#if sides === null}
		<div class="rounded-md border border-dashed px-3 py-2 text-sm" role="note">
			<p class="font-medium">{m.planning_debts_unavailable()}</p>
			<p class="text-xs text-muted-foreground">{m.planning_debts_unavailable_description()}</p>
		</div>
	{:else}
		<div class="grid gap-4 sm:grid-cols-2">
			{#each sides as side (side.id)}
				<div class="min-w-0 space-y-2">
					<h4 class="text-sm font-semibold">{side.title}</h4>
					{#if side.rows.length === 0}
						<p class="text-xs text-muted-foreground">{m.planning_debts_none_side()}</p>
					{:else}
						<ul class="space-y-2">
							{#each side.rows as debt (debt.debtId)}
								<li class="rounded-md border px-3 py-2 text-sm">
									<span class="block break-words font-medium">
										{debt.contactName ?? m.planning_receipt_no_contact()}
									</span>
									{#if debt.description}
										<span class="block break-words text-xs text-muted-foreground">{debt.description}</span>
									{/if}
									<span class="block text-xs tabular-nums">
										{m.planning_debts_remaining({ amount: context.money(debt.remaining) })}
									</span>
								</li>
							{/each}
						</ul>
					{/if}
				</div>
			{/each}
		</div>
	{/if}
</section>
