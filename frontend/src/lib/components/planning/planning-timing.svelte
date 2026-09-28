<script lang="ts">
	/**
	 * D5 output 2 — the statement timing list.
	 *
	 * Beside the projection, never netted into it: the purchases behind a
	 * confirmed statement already moved Net Balance, and no funding account is
	 * inferred. Overdue statements keep their own group, estimates are context
	 * only, and "none confirmed" never reads as "nothing owed". No totals.
	 */
	import { Badge } from '$lib/components/ui/badge';
	import { timingReasonMessage, type LabelContext } from '$lib/planning-labels';
	import type { PreviewStatementDue, PreviewTiming } from '$lib/planning-preview';
	import { m } from '$lib/paraglide/messages.js';

	let { timing, context }: { timing: PreviewTiming; context: LabelContext } = $props();

	// The only data-free reasons are the ones that say why nothing is listed.
	const reasons = $derived(timing.reasons.map((reason) => timingReasonMessage(reason, context)));
</script>

{#snippet statementRow(statement: PreviewStatementDue)}
	<li class="space-y-1 rounded-md border px-3 py-2 text-sm">
		<div class="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
			<span class="min-w-0 break-words font-medium">{statement.accountName}</span>
			<span class="text-xs text-muted-foreground">
				{statement.dueDate === null
					? m.planning_timing_due_unknown()
					: m.planning_timing_due({ date: context.date(statement.dueDate) })}
			</span>
		</div>
		<div class="flex flex-wrap gap-x-4 gap-y-1 text-xs tabular-nums">
			<span>{m.planning_timing_outstanding({ amount: context.money(statement.outstandingBalance) })}</span>
			{#if statement.minimumPayment !== null}
				<span class="text-muted-foreground">
					{m.planning_timing_minimum({ amount: context.money(statement.minimumPayment) })}
				</span>
			{/if}
		</div>
		{#if statement.reconciliationMismatch}
			<Badge variant="outline" class="whitespace-normal text-left">{m.planning_timing_mismatch()}</Badge>
		{/if}
	</li>
{/snippet}

<section class="space-y-3" aria-labelledby="planning-timing-heading">
	<div class="space-y-1">
		<h3 id="planning-timing-heading" class="font-heading text-base font-semibold">
			{m.planning_timing_title()}
		</h3>
		<p class="text-xs text-muted-foreground">{m.planning_timing_description()}</p>
	</div>

	{#if timing.status === 'notApplicable'}
		<p class="text-sm text-muted-foreground">{m.planning_timing_not_applicable()}</p>
	{:else if timing.status === 'unavailable'}
		<!-- No Retry button here: the list is part of the preview response, so
		     calculating again is the retry, and the page says so. -->
		<div class="rounded-md border border-dashed px-3 py-2 text-sm" role="note">
			<p class="font-medium">{m.planning_timing_unavailable()}</p>
			<p class="text-xs text-muted-foreground">
				{[...reasons, m.planning_timing_unavailable_description()].join(' ')}
			</p>
		</div>
	{:else}
		{#if timing.status === 'partial'}
			<div class="rounded-md border border-dashed px-3 py-2 text-sm" role="note">
				<p class="font-medium">{m.planning_timing_partial()}</p>
				<ul class="mt-1 list-disc space-y-1 pl-5 text-xs text-muted-foreground">
					{#each reasons as reason}
						<li>{reason}</li>
					{/each}
				</ul>
			</div>
		{/if}

		<div class="space-y-2">
			<h4 class="text-sm font-semibold">{m.planning_timing_overdue_title()}</h4>
			{#if timing.overdue.length === 0}
				<p class="text-xs text-muted-foreground">{m.planning_timing_none_overdue()}</p>
			{:else}
				<ul class="space-y-2">
					{#each timing.overdue as statement (statement.statementId)}
						{@render statementRow(statement)}
					{/each}
				</ul>
			{/if}
		</div>

		<div class="space-y-2">
			<h4 class="text-sm font-semibold">{m.planning_timing_dated_title()}</h4>
			{#if timing.dated.length === 0}
				<p class="text-xs text-muted-foreground">{m.planning_timing_none_confirmed()}</p>
			{:else}
				<ul class="space-y-2">
					{#each timing.dated as statement (statement.statementId)}
						{@render statementRow(statement)}
					{/each}
				</ul>
			{/if}
		</div>

		{#if timing.estimates.length > 0}
			<div class="space-y-2">
				<h4 class="text-sm font-semibold">{m.planning_timing_estimates_title()}</h4>
				<ul class="space-y-2">
					{#each timing.estimates as estimate, index (`${estimate.accountId}-${estimate.periodEnd}-${index}`)}
						<li class="rounded-md border border-dashed px-3 py-2 text-xs">
							<span class="font-medium">{estimate.accountName}</span>
							<span class="block text-muted-foreground tabular-nums">
								{m.planning_timing_estimate_row({
									end: estimate.periodEnd === null ? '—' : context.date(estimate.periodEnd),
									amount: context.money(estimate.estimatedBalance),
								})}
								{#if estimate.dueDate !== null}
									· {m.planning_timing_due({ date: context.date(estimate.dueDate) })}
								{/if}
							</span>
						</li>
					{/each}
				</ul>
			</div>
		{/if}
	{/if}
</section>
