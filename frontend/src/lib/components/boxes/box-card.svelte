<script lang="ts">
	import { CircleAlert, PanelRightOpen, Target } from '@lucide/svelte';
	import type { Snippet } from 'svelte';
	import * as Card from '$lib/components/ui/card';
	import { Badge } from '$lib/components/ui/badge';
	import { Button } from '$lib/components/ui/button';
	import { m } from '$lib/paraglide/messages.js';
	import type { BoxDto } from '$lib/types/boxes';
	import { type BoxPlanState, type BoxPlanSummary } from '$lib/types/box-plans';

	let {
		box,
		formattedBalance,
		archived = false,
		planState = { kind: 'unavailable' },
		actions,
	}: {
		box: BoxDto;
		formattedBalance: string;
		archived?: boolean;
		/** What the Box's plan list supports saying — see `boxPlanState`. */
		planState?: BoxPlanState;
		actions?: Snippet;
	} = $props();

	const href = $derived(`/boxes/${box.id}`);

	function planTypeLabel(plan: BoxPlanSummary): string {
		return plan.type === 'SAVING_GOAL' ? m.box_plan_saving_goal() : m.box_plan_spending_budget();
	}

	function planStatusLabel(plan: BoxPlanSummary): string {
		switch (plan.status) {
			case 'READY_TO_COMPLETE':
				return m.box_plan_status_ready();
			case 'OVERDUE':
				return m.box_plan_status_overdue();
			default:
				return m.box_plan_status_active();
		}
	}
</script>

<Card.Root
	class="relative bg-gradient-to-br from-[oklch(0.97_0.025_var(--box-hue))] to-card dark:from-[oklch(0.27_0.035_var(--box-hue))]"
	style={`--box-hue: ${box.hue}`}
>
	<Card.Header>
		<div class="flex min-w-0 items-start gap-3">
			<div
				class="flex size-11 shrink-0 items-center justify-center rounded-xl bg-[oklch(0.88_0.12_var(--box-hue))] text-xl text-[oklch(0.32_0.08_var(--box-hue))] shadow-sm ring-1 ring-black/5"
				aria-hidden="true"
			>
				{box.icon || '□'}
			</div>
			<div class="min-w-0 flex-1">
				<Card.Title class="truncate">
					<!-- The name is the entry point to the Box, not decoration. The
					     accessible name says where the link goes rather than repeating
					     the visible text alone. -->
					<a
						{href}
						class="underline-offset-4 outline-none hover:underline focus-visible:ring-2 focus-visible:ring-ring focus-visible:rounded-sm"
						aria-label={m.boxes_open_box_aria({ name: box.name })}
					>
						{box.name}
					</a>
				</Card.Title>
				<Card.Description class="line-clamp-2 min-h-8">{box.description || m.boxes_no_description()}</Card.Description>
			</div>
		</div>
	</Card.Header>
	<Card.Content class="space-y-4">
		<div>
			<p class="text-xs text-muted-foreground">{m.boxes_balance()}</p>
			<p class="text-2xl font-semibold tabular-nums">{formattedBalance}</p>
		</div>

		{#if !archived}
			<!-- What this Box is for. An unreadable plan list says so; it never
			     borrows the unplanned state's invitation to create one. -->
			<div class="space-y-2">
				<p class="text-xs text-muted-foreground">{m.box_plan_section_title()}</p>
				{#if planState.kind === 'active'}
					<div class="flex flex-wrap items-center gap-2">
						<Badge variant="secondary">{planTypeLabel(planState.plan)}</Badge>
						<Badge variant={planState.plan.status === 'OVERDUE' ? 'destructive' : 'outline'}>
							{planStatusLabel(planState.plan)}
						</Badge>
					</div>
				{:else if planState.kind === 'none'}
					<div class="flex flex-wrap items-center gap-2">
						<p class="text-sm text-muted-foreground">{m.boxes_no_plan()}</p>
						<Button href={`${href}?plan=new`} size="sm" variant="secondary">
							<Target data-icon="inline-start" />
							{m.box_plan_create()}
						</Button>
					</div>
				{:else}
					<p class="flex items-center gap-1.5 text-sm text-muted-foreground">
						<CircleAlert class="size-3.5 shrink-0" aria-hidden="true" />
						{m.boxes_plan_unavailable()}
					</p>
				{/if}
			</div>
		{/if}

		<Button {href} variant="outline" class="w-full">
			<PanelRightOpen data-icon="inline-start" />
			{m.boxes_open_box()}
		</Button>
		<p class="text-xs text-muted-foreground">{m.boxes_open_box_hint()}</p>
	</Card.Content>
	<Card.Footer class="flex flex-wrap items-center justify-between gap-2 bg-background/45 px-4 py-3">
		{#if archived}<Badge variant="secondary">{m.boxes_archived()}</Badge>{/if}
		{@render actions?.()}
	</Card.Footer>
</Card.Root>
