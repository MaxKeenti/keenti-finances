<script lang="ts">
	/**
	 * Section 3 — active Box Plans (ADR-0021).
	 *
	 * Plans are guidance. A suggested top-up says what the plan would like the
	 * Box to hold; it never allocates anything, and nothing on this page can.
	 * Every figure is the one the plan services publish — recomputing a
	 * remaining amount or a top-up here is how two screens start disagreeing
	 * about the same plan.
	 *
	 * Money reserved in Boxes is already inside In Boxes and outside Available
	 * to Spend. It is shown here as context for the plans, not added again.
	 */
	import * as Card from '$lib/components/ui/card';
	import { Button } from '$lib/components/ui/button';
	import { Badge } from '$lib/components/ui/badge';
	import { ArrowRight } from '@lucide/svelte';
	import { SectionUnavailable } from '$lib/components/section-status';
	import { boxPlanStatusLabel, boxPlanTypeLabel } from '$lib/box-plan-labels';
	import { plansEmptiness, plansTotalsAreComplete } from '$lib/dashboard-sections';
	import type { OverviewPlans } from '$lib/server/payloads';
	import { m } from '$lib/paraglide/messages.js';

	let {
		plans,
		format,
	}: {
		plans: OverviewPlans;
		format: (value: number) => string;
	} = $props();

	const emptiness = $derived(plansEmptiness(plans));
	const totalsComplete = $derived(plansTotalsAreComplete(plans));
</script>

<section class="space-y-3" aria-labelledby="dashboard-plans-heading">
	<div class="flex flex-wrap items-center justify-between gap-2">
		<h2 id="dashboard-plans-heading" class="font-heading text-lg font-semibold">
			{m.dashboard_plans_title()}
		</h2>
		<Button href="/boxes" size="sm" variant="ghost" class="text-xs">
			{m.nav_boxes()}
			<ArrowRight class="size-3.5" aria-hidden="true" />
		</Button>
	</div>

	<!-- A plan that could not be read is not an absent plan, so the notice comes
	     with a Retry rather than reading as a settled state. -->
	{#if plans.partial}
		<SectionUnavailable
			compact
			title={m.section_plans_partial()}
			description={m.dashboard_plans_partial()}
		/>
	{/if}

	<!-- "No active plans, create one" is a positive claim about this User's
	     Boxes, so it is made only from a complete read (`empty`). Under a failed
	     read (`unknown`) an existing plan could be sitting behind it, and
	     inviting the User to create another is the wrong offer; the partial
	     notice above stands alone instead. -->
	{#if emptiness === 'empty'}
		<div class="space-y-2 rounded-md border border-dashed px-3 py-3 text-sm">
			<p class="font-medium">{m.dashboard_plans_none_title()}</p>
			<p class="text-xs text-muted-foreground">{m.dashboard_plans_none_description()}</p>
			<Button href="/boxes" size="sm" variant="outline">{m.box_plan_create()}</Button>
		</div>
	{:else if emptiness === 'populated'}
		<!-- With a plan unread, the reserved figure covers only the Boxes that
		     could be read. It is labelled a subtotal rather than presented as the
		     total reserved across every planned Box. -->
		<p class="text-sm text-muted-foreground">
			{totalsComplete ? m.dashboard_plans_reserved() : m.dashboard_plans_reserved_partial()}:
			<span class="tabular-nums">{format(plans.reservedInPlannedBoxes)}</span>
		</p>
		<div class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
			{#each plans.items as plan (plan.planId)}
				<Card.Root>
					<Card.Header class="gap-1">
						<Card.Description class="flex flex-wrap items-center gap-1.5">
							<span class="min-w-0 truncate">{plan.boxName}</span>
							<Badge variant="secondary">{boxPlanTypeLabel(plan.type)}</Badge>
							<Badge variant="outline">{boxPlanStatusLabel(plan.status)}</Badge>
						</Card.Description>
						<Card.Title class="text-xl font-bold tabular-nums">{format(plan.boxBalance)}</Card.Title>
					</Card.Header>
					<Card.Content class="space-y-1 text-xs text-muted-foreground">
						{#if plan.type === 'SAVING_GOAL'}
							{#if plan.targetAmount !== null}
								<p>{m.box_plan_target()}: <span class="tabular-nums">{format(plan.targetAmount)}</span></p>
							{/if}
							{#if plan.remainingAmount !== null}
								<p>
									{m.box_plan_remaining()}:
									<span class="tabular-nums">{format(plan.remainingAmount)}</span>
								</p>
							{/if}
							{#if plan.currentCommitment !== null}
								<!-- The commitment already includes any carried shortfall;
								     it is the plan's own figure, not one derived here. -->
								<p>
									{m.box_plan_current_commitment()}:
									<span class="tabular-nums">{format(plan.currentCommitment)}</span>
								</p>
							{/if}
							{#if plan.suggestedContribution !== null}
								<p>{m.dashboard_plans_suggested_topup({ amount: format(plan.suggestedContribution) })}</p>
							{/if}
						{:else}
							{#if plan.desiredBalance !== null}
								<p>
									{m.box_plan_desired_balance()}:
									<span class="tabular-nums">{format(plan.desiredBalance)}</span>
								</p>
							{/if}
							{#if plan.suggestedTopUp !== null}
								<p>{m.dashboard_plans_suggested_topup({ amount: format(plan.suggestedTopUp) })}</p>
							{/if}
						{/if}
					</Card.Content>
					<Card.Footer>
						<Button href="/boxes/{plan.boxId}" size="sm" variant="outline">
							{m.boxes_open_box()}
						</Button>
					</Card.Footer>
				</Card.Root>
			{/each}
		</div>
		<!-- Said once, under the suggestions, rather than on every card. -->
		<p class="text-xs text-muted-foreground">{m.dashboard_plans_suggestion_note()}</p>
	{/if}

	{#if !plans.partial && plans.boxesWithoutActivePlan > 0}
		<p class="text-xs text-muted-foreground">
			{m.dashboard_plans_unplanned_count({ count: plans.boxesWithoutActivePlan })}
		</p>
	{/if}
</section>
