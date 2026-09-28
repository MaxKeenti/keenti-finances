<script lang="ts">
	/**
	 * One preview response, exactly as the server returned it.
	 *
	 * Nothing here computes a figure. The three projection states stay
	 * visibly different: only `complete` has a headline; `partial` is a
	 * subtotal that is never called money left; `unavailable` shows no
	 * projected figure at all. Timing and undated Debts render from their own
	 * statuses whatever the projection's.
	 */
	import * as Card from '$lib/components/ui/card';
	import * as Table from '$lib/components/ui/table';
	import PlanningTiming from './planning-timing.svelte';
	import PlanningUndatedDebts from './planning-undated-debts.svelte';
	import {
		formatSnapshotTime,
		missingInputMessage,
		noteMessage,
		rowName,
		type LabelContext,
	} from '$lib/planning-labels';
	import type {
		PlanningDraft,
		PlanningPreviewResponse,
		PreviewMissingInput,
	} from '$lib/planning-preview';
	import { m } from '$lib/paraglide/messages.js';

	let {
		response,
		submitted,
		context,
		locale,
		fallbackTimeZone,
		receiptLabel,
		onGoToRow,
		heading = $bindable(null),
	}: {
		response: PlanningPreviewResponse;
		/** The draft the response indexes into. */
		submitted: PlanningDraft;
		context: LabelContext;
		locale: string;
		fallbackTimeZone: string | null;
		receiptLabel: (index: number) => string;
		onGoToRow: (input: PreviewMissingInput) => void;
		heading?: HTMLElement | null;
	} = $props();

	const baseline = $derived(response.baseline);
	const projected = $derived(response.projected);
	const snapshot = $derived(
		formatSnapshotTime(response.generatedAt, response.timeZone ?? fallbackTimeZone, locale),
	);
	const creditInFavor = $derived(
		baseline !== null && baseline.creditInFavor !== null && baseline.creditInFavor > 0
			? baseline.creditInFavor
			: null,
	);
	const notes = $derived(
		response.notes
			// The credit-in-favor note is stated with its amount just above.
			.filter((code) => !(code === 'CREDIT_IN_FAVOR_IN_BASELINE' && creditInFavor !== null))
			.map((code) => noteMessage(code, submitted.costs.length)),
	);
	// A partial projection is a subtotal of the rows entered, not an outcome,
	// so its column never reads "After this scenario".
	const afterLabel = $derived(
		response.status === 'partial' ? m.planning_projection_after_partial() : m.planning_projection_after(),
	);
</script>

{#snippet missingList(inputs: PreviewMissingInput[])}
	<ul class="space-y-1.5 text-sm">
		{#each inputs as input, index (`${input.reason}-${input.itemIndex}-${input.receiptIndex}-${input.boxId}-${index}`)}
			<li class="flex flex-wrap items-baseline gap-x-2">
				<span class="min-w-0 break-words">{missingInputMessage(input, context)}</span>
				{#if input.itemIndex !== null || input.receiptIndex !== null}
					<button
						type="button"
						class="text-xs font-medium text-primary underline-offset-2 hover:underline focus-visible:underline focus-visible:outline-none"
						onclick={() => onGoToRow(input)}
					>
						{m.planning_go_to_row({ row: rowName(input) })}
					</button>
				{/if}
			</li>
		{/each}
	</ul>
{/snippet}

{#snippet comparison(current: { netBalance: number; inBoxes: number; availableToSpend: number }, after: { netBalance: number; inBoxes: number; availableToSpend: number } | null)}
	<Table.Root>
		<Table.Header>
			<Table.Row>
				<Table.Head>{m.planning_projection_measure()}</Table.Head>
				<Table.Head class="text-right">{m.planning_projection_current()}</Table.Head>
				{#if after !== null}
					<Table.Head class="text-right">{afterLabel}</Table.Head>
				{/if}
			</Table.Row>
		</Table.Header>
		<Table.Body>
			{#each [
				{ label: m.planning_projection_row_available(), now: current.availableToSpend, then: after?.availableToSpend },
				{ label: m.planning_projection_row_net(), now: current.netBalance, then: after?.netBalance },
				{ label: m.planning_projection_row_boxes(), now: current.inBoxes, then: after?.inBoxes },
			] as row (row.label)}
				<Table.Row>
					<Table.Cell class="whitespace-normal">{row.label}</Table.Cell>
					<Table.Cell class="text-right tabular-nums">{context.money(row.now)}</Table.Cell>
					{#if after !== null && row.then !== undefined}
						<Table.Cell class="text-right tabular-nums">{context.money(row.then)}</Table.Cell>
					{/if}
				</Table.Row>
			{/each}
		</Table.Body>
	</Table.Root>
{/snippet}

<section class="space-y-6" aria-labelledby="planning-result-heading">
	<div class="space-y-1">
		<h2
			id="planning-result-heading"
			bind:this={heading}
			tabindex="-1"
			class="font-heading text-lg font-semibold outline-none"
		>
			{m.planning_result_heading()}
		</h2>
		<p class="text-xs text-muted-foreground">
			{m.planning_result_snapshot({ time: snapshot })}
			{#if response.window !== null}
				{m.planning_result_window({
					from: context.date(response.window.from),
					to: context.date(response.window.to),
				})}
			{/if}
		</p>
	</div>

	<Card.Root>
		<Card.Content class="space-y-4">
			{#if response.status === 'complete' && projected !== null && baseline !== null}
				<div class="space-y-1">
					<p class="text-sm text-muted-foreground">{m.planning_projection_complete_label()}</p>
					<p class="text-3xl font-bold tabular-nums {projected.availableToSpend < 0 ? 'text-money-negative' : ''}">
						{context.money(projected.availableToSpend)}
					</p>
					<p class="text-xs text-muted-foreground">{m.planning_projection_hypothetical()}</p>
				</div>
				{@render comparison(baseline, projected)}
			{:else if response.status === 'partial' && projected !== null && baseline !== null}
				<div class="space-y-1">
					<p class="font-medium">{m.planning_projection_partial_title()}</p>
					<p class="text-sm text-muted-foreground">
						{m.planning_projection_partial_label()}:
						<span class="font-semibold tabular-nums text-foreground">{context.money(projected.availableToSpend)}</span>
					</p>
					<p class="text-xs text-muted-foreground">{m.planning_projection_hypothetical()}</p>
				</div>
				<div class="space-y-2 rounded-md border border-dashed px-3 py-2">
					<p class="text-sm font-medium">{m.planning_projection_missing_title()}</p>
					{@render missingList(response.missingInputs)}
				</div>
				{@render comparison(baseline, projected)}
			{:else}
				<div class="space-y-2" role="note">
					<p class="font-medium">{m.planning_projection_unavailable_title()}</p>
					<p class="text-xs text-muted-foreground">{m.planning_projection_unavailable_description()}</p>
					{@render missingList(response.missingInputs)}
				</div>
				<div class="space-y-2">
					<h3 class="text-sm font-semibold">{m.planning_baseline_title()}</h3>
					{#if baseline === null}
						<p class="text-sm text-muted-foreground">{m.planning_baseline_unavailable()}</p>
					{:else}
						{@render comparison(baseline, null)}
					{/if}
				</div>
			{/if}

			{#if creditInFavor !== null}
				<p class="text-xs text-muted-foreground">
					{m.planning_credit_in_favor({ amount: context.money(creditInFavor) })}
				</p>
			{/if}
		</Card.Content>
	</Card.Root>

	{#if projected !== null}
		<section class="space-y-2" aria-labelledby="planning-boxes-heading">
			<h3 id="planning-boxes-heading" class="font-heading text-base font-semibold">
				{m.planning_projection_boxes_title()}
			</h3>
			{#if projected.perBox.length === 0}
				<p class="text-sm text-muted-foreground">{m.planning_projection_no_boxes()}</p>
			{:else}
				<Table.Root>
					<Table.Header>
						<Table.Row>
							<Table.Head>{m.planning_projection_box_col()}</Table.Head>
							<Table.Head class="text-right">{m.planning_projection_current()}</Table.Head>
							<Table.Head class="text-right">{afterLabel}</Table.Head>
						</Table.Row>
					</Table.Header>
					<Table.Body>
						{#each projected.perBox as box (box.boxId)}
							<Table.Row>
								<Table.Cell class="max-w-48 whitespace-normal break-words">{box.name}</Table.Cell>
								<Table.Cell class="text-right tabular-nums">{context.money(box.balance)}</Table.Cell>
								<Table.Cell class="text-right tabular-nums">{context.money(box.projectedBalance)}</Table.Cell>
							</Table.Row>
						{/each}
					</Table.Body>
				</Table.Root>
			{/if}
		</section>
	{/if}

	{#if response.includedReceipts.length > 0}
		<section class="space-y-2" aria-labelledby="planning-included-receipts-heading">
			<h3 id="planning-included-receipts-heading" class="font-heading text-base font-semibold">
				{m.planning_included_receipts_title()}
			</h3>
			<ul class="space-y-1 text-sm">
				{#each response.includedReceipts as receipt (receipt.receiptIndex)}
					<li class="break-words tabular-nums">
						{m.planning_included_receipt({
							label: receiptLabel(receipt.receiptIndex),
							amount: context.money(receipt.amount),
							date: context.date(receipt.date),
						})}
					</li>
				{/each}
			</ul>
		</section>
	{/if}

	{#if notes.length > 0}
		<section class="space-y-2" aria-labelledby="planning-notes-heading">
			<h3 id="planning-notes-heading" class="font-heading text-base font-semibold">
				{m.planning_notes_title()}
			</h3>
			<ul class="list-disc space-y-1 pl-5 text-xs text-muted-foreground">
				{#each notes as note}
					<li>{note}</li>
				{/each}
			</ul>
		</section>
	{/if}

	<PlanningTiming timing={response.timing} {context} />
	<PlanningUndatedDebts debts={response.undatedDebts} {context} />
</section>
