<script lang="ts">
	import {
		CalendarDays,
		CheckCircle2,
		CircleDollarSign,
		History,
		Pencil,
		Target,
		TriangleAlert,
		WalletCards,
	} from '@lucide/svelte';
	import { toast } from 'svelte-sonner';
	import { adaptiveConfirm } from '$lib/components/adaptive-confirm';
	import * as Alert from '$lib/components/ui/alert';
	import { Badge } from '$lib/components/ui/badge';
	import { Button } from '$lib/components/ui/button';
	import * as Card from '$lib/components/ui/card';
	import { Progress } from '$lib/components/ui/progress';
	import * as Table from '$lib/components/ui/table';
	import * as Tabs from '$lib/components/ui/tabs';
	import { formatLocale, mxnFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
	import type { BoxMovementDto } from '$lib/types/boxes';
	import {
		isActivePlan,
		type BoxPlan,
		type BoxPlanStatus,
		type PlanCadence,
		type SavingGoalPeriod,
		type SpendingBudgetPeriod,
	} from '$lib/types/box-plans';
	import PlanCalendar from './plan-calendar.svelte';
	import PlanRevisionDialog from './plan-revision-dialog.svelte';

	let {
		plan,
		movements,
		locale,
		timeZone,
		viewingHistorical,
		onChanged,
		onTopUp,
		topUpDisabled = false,
	}: {
		plan: BoxPlan;
		movements: BoxMovementDto[];
		locale: string;
		timeZone: string;
		viewingHistorical: boolean;
		onChanged: (plan: BoxPlan) => void | Promise<void>;
		onTopUp: (amount: number) => void;
		topUpDisabled?: boolean;
	} = $props();

	let revisionOpen = $state(false);
	let lifecycleLoading = $state(false);
	let selectedTab = $state('overview');

	const fmt = $derived(mxnFormatter(locale));
	const dateFmt = $derived(
		new Intl.DateTimeFormat(formatLocale(locale), { dateStyle: 'medium', timeZone: 'UTC' }),
	);
	const timestampFmt = $derived(
		new Intl.DateTimeFormat(formatLocale(locale), {
			dateStyle: 'medium',
			timeStyle: 'short',
			timeZone,
		}),
	);
	const active = $derived(isActivePlan(plan));
	const goalDisplayBalance = $derived(
		plan.type === 'SAVING_GOAL' && !active && plan.completionAmount !== null
			? plan.completionAmount
			: plan.boxBalance,
	);
	const goalProgress = $derived(
		plan.type === 'SAVING_GOAL'
			? Math.min(
					100,
					Math.max(
						0,
						active
							? plan.progressPercent
							: (goalDisplayBalance / Math.max(plan.targetAmount, 0.01)) * 100,
					),
				)
			: 0,
	);
	const budgetProgress = $derived(
		plan.type === 'SPENDING_BUDGET'
			? Math.min(100, Math.max(0, (plan.boxBalance / Math.max(plan.desiredBalance, 0.01)) * 100))
			: 0,
	);
	const currentRevisionId = $derived.by(() => {
		const periodRevisionId = plan.currentPeriod?.revisionId;
		if (periodRevisionId) return periodRevisionId;
		return plan.revisions
			.filter((revision) => !revision.scheduled && revision.supersededAt === null)
			.toSorted((a, b) => b.effectiveFrom.localeCompare(a.effectiveFrom))
			.at(0)?.id;
	});

	function statusLabel(status: BoxPlanStatus | 'OPEN' | 'ACHIEVED' | 'MISSED'): string {
		if (status === 'ACTIVE') return m.box_plan_status_active();
		if (status === 'READY_TO_COMPLETE') return m.box_plan_status_ready();
		if (status === 'OVERDUE') return m.box_plan_status_overdue();
		if (status === 'COMPLETED') return m.box_plan_status_completed();
		if (status === 'ABANDONED') return m.box_plan_status_abandoned();
		if (status === 'ENDED') return m.box_plan_status_ended();
		if (status === 'ACHIEVED') return m.box_plan_period_achieved();
		if (status === 'MISSED') return m.box_plan_period_missed();
		return m.box_plan_period_open();
	}

	function statusVariant(
		status: BoxPlanStatus | 'OPEN' | 'ACHIEVED' | 'MISSED',
	): 'default' | 'secondary' | 'destructive' | 'outline' | 'success' | 'warning' | 'info' {
		if (status === 'COMPLETED' || status === 'ACHIEVED' || status === 'READY_TO_COMPLETE') return 'success';
		if (status === 'OVERDUE' || status === 'MISSED') return 'destructive';
		if (status === 'ACTIVE' || status === 'OPEN') return 'info';
		return 'secondary';
	}

	function cadenceLabel(cadence: PlanCadence): string {
		if (cadence === 'DAILY') return m.box_plan_daily();
		if (cadence === 'WEEKLY') return m.box_plan_weekly();
		if (cadence === 'BIWEEKLY') return m.box_plan_biweekly();
		return m.box_plan_monthly();
	}

	function date(value: string | null): string {
		return value ? dateFmt.format(new Date(`${value}T00:00:00Z`)) : m.box_plan_none_value();
	}

	function timestamp(value: string | null): string {
		return value ? timestampFmt.format(new Date(value)) : m.box_plan_none_value();
	}

	function goalPeriodRange(period: SavingGoalPeriod): string {
		return m.box_plan_period_range({ start: date(period.startDate), end: date(period.endDate) });
	}

	function budgetPeriodRange(period: SpendingBudgetPeriod): string {
		return m.box_plan_period_range({ start: date(period.periodStart), end: date(period.periodEnd) });
	}

	function budgetFunded(period: SpendingBudgetPeriod): number {
		return period.deposits + period.transfersIn;
	}

	async function lifecycle(
		action: 'complete' | 'abandon' | 'end',
		title: string,
		description: string,
		confirmLabel: string,
	) {
		if (!(await adaptiveConfirm({ title, description, confirmLabel, cancelLabel: m.common_cancel() }))) return;
		lifecycleLoading = true;
		try {
			const path = plan.type === 'SAVING_GOAL' ? 'saving-goal' : 'spending-budget';
			const response = await fetch(`/api/boxes/${plan.boxId}/plans/${path}/${plan.id}/${action}`, {
				method: 'POST',
			});
			if (!response.ok) throw new Error(String(response.status));
			const updated = (await response.json()) as BoxPlan;
			toast.success(m.box_plan_closed_success());
			await onChanged(updated);
		} catch {
			toast.error(m.box_plan_action_error());
		} finally {
			lifecycleLoading = false;
		}
	}
</script>

<Card.Root>
	<Card.Header class="gap-3">
		<div class="flex flex-wrap items-start justify-between gap-3">
			<div class="flex items-start gap-3">
				<div class="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
					{#if plan.type === 'SAVING_GOAL'}<Target class="size-5" aria-hidden="true" />{:else}<WalletCards class="size-5" aria-hidden="true" />{/if}
				</div>
				<div>
					<Card.Title>{plan.type === 'SAVING_GOAL' ? m.box_plan_saving_goal() : m.box_plan_spending_budget()}</Card.Title>
					<Card.Description>
						{cadenceLabel(plan.cadence)} · {m.box_plan_created_on({ date: timestamp(plan.createdAt) })}
					</Card.Description>
				</div>
			</div>
			<div class="flex flex-wrap items-center gap-2">
				{#if viewingHistorical}<Badge variant="outline">{m.box_plan_historical()}</Badge>{/if}
				<Badge variant={statusVariant(plan.status)}>{statusLabel(plan.status)}</Badge>
			</div>
		</div>

		{#if active}
			<div class="flex flex-wrap gap-2">
				<Button variant="outline" size="sm" onclick={() => (revisionOpen = true)}>
					<Pencil data-icon="inline-start" />{m.box_plan_edit()}
				</Button>
				{#if plan.type === 'SAVING_GOAL'}
					{#if plan.status === 'READY_TO_COMPLETE'}
						<Button
							size="sm"
							disabled={lifecycleLoading}
							onclick={() => lifecycle('complete', m.box_plan_complete_title(), m.box_plan_complete_description(), m.box_plan_complete())}
						><CheckCircle2 data-icon="inline-start" />{m.box_plan_complete()}</Button>
					{/if}
					<Button
						variant="destructive"
						size="sm"
						disabled={lifecycleLoading}
						onclick={() => lifecycle('abandon', m.box_plan_abandon_title(), m.box_plan_abandon_description(), m.box_plan_abandon())}
					>{m.box_plan_abandon()}</Button>
				{:else}
					<Button
						variant="destructive"
						size="sm"
						disabled={lifecycleLoading}
						onclick={() => lifecycle('end', m.box_plan_end_budget_title(), m.box_plan_end_budget_description(), m.box_plan_end_budget())}
					>{m.box_plan_end_budget()}</Button>
				{/if}
			</div>
		{/if}
	</Card.Header>

	<Card.Content>
		<Tabs.Root bind:value={selectedTab}>
			<Tabs.List class="w-full justify-start overflow-x-auto">
				<Tabs.Trigger value="overview"><CircleDollarSign data-icon="inline-start" />{m.common_progress()}</Tabs.Trigger>
				<Tabs.Trigger value="calendar"><CalendarDays data-icon="inline-start" />{m.box_plan_calendar_title()}</Tabs.Trigger>
				<Tabs.Trigger value="history"><History data-icon="inline-start" />{m.box_plan_history_title()}</Tabs.Trigger>
			</Tabs.List>

			<Tabs.Content value="overview" class="mt-4 space-y-4">
				{#if plan.type === 'SAVING_GOAL'}
					<div class="rounded-xl bg-linear-to-br from-primary/10 via-primary/5 to-transparent p-4 sm:p-5">
						<div class="flex flex-wrap items-end justify-between gap-2">
							<div><p class="text-sm text-muted-foreground">{m.box_plan_saved()}</p><p class="text-3xl font-semibold tabular-nums">{fmt.format(goalDisplayBalance)}</p></div>
							<p class="text-sm font-medium tabular-nums">{Math.round(goalProgress)}%</p>
						</div>
						<Progress class="mt-3 h-2" value={goalProgress} max={100} aria-label={m.box_plan_progress_label({ saved: fmt.format(goalDisplayBalance), target: fmt.format(plan.targetAmount), percent: Math.round(goalProgress) })} />
						<p class="mt-2 text-sm text-muted-foreground">{m.box_plan_progress_label({ saved: fmt.format(goalDisplayBalance), target: fmt.format(plan.targetAmount), percent: Math.round(goalProgress) })}</p>
					</div>

					{#if plan.status === 'READY_TO_COMPLETE'}
						<Alert.Root><CheckCircle2 aria-hidden="true" /><Alert.Title>{m.box_plan_status_ready()}</Alert.Title><Alert.Description>{m.box_plan_goal_ready_description()}</Alert.Description></Alert.Root>
					{:else if plan.status === 'OVERDUE'}
						<Alert.Root variant="destructive"><TriangleAlert aria-hidden="true" /><Alert.Title>{m.box_plan_status_overdue()}</Alert.Title><Alert.Description>{m.box_plan_goal_overdue_description()}</Alert.Description></Alert.Root>
					{/if}

					<div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
						<div class="rounded-lg border p-3"><p class="text-xs text-muted-foreground">{m.box_plan_target()}</p><p class="font-semibold tabular-nums">{fmt.format(plan.targetAmount)}</p><p class="mt-1 text-xs text-muted-foreground">{date(plan.targetDate)}</p></div>
						<div class="rounded-lg border p-3"><p class="text-xs text-muted-foreground">{m.box_plan_remaining()}</p><p class="font-semibold tabular-nums">{fmt.format(active ? plan.remainingAmount : Math.max(plan.targetAmount - goalDisplayBalance, 0))}</p></div>
						<div class="rounded-lg border p-3"><p class="text-xs text-muted-foreground">{m.box_plan_current_commitment()}</p><p class="font-semibold tabular-nums">{fmt.format(active ? plan.currentCommitment : plan.regularCommitment)}</p></div>
						<div class="rounded-lg border p-3"><p class="text-xs text-muted-foreground">{m.box_plan_arrears()}</p><p class="font-semibold tabular-nums {plan.arrears > 0 ? 'text-destructive' : ''}">{fmt.format(plan.arrears)}</p></div>
					</div>

					<div class="grid gap-3 sm:grid-cols-2">
						<div class="rounded-lg border p-3"><p class="text-xs text-muted-foreground">{m.box_plan_projected_date()}</p><p class="font-medium">{date(plan.projectedCompletionDate)}</p></div>
						<div class="rounded-lg border p-3"><p class="text-xs text-muted-foreground">{m.box_plan_suggested_extension()}</p><p class="font-medium">{date(plan.suggestedExtensionDate)}</p></div>
					</div>

					{#if plan.currentPeriod}
						<section class="rounded-lg border p-4" aria-labelledby="goal-current-period">
							<div class="flex flex-wrap items-center justify-between gap-2"><div><h3 id="goal-current-period" class="font-semibold">{m.box_plan_period_title()}</h3><p class="text-sm text-muted-foreground">{goalPeriodRange(plan.currentPeriod)}</p></div><Badge variant={statusVariant(plan.currentPeriod.status)}>{statusLabel(plan.currentPeriod.status)}</Badge></div>
							<div class="mt-4 grid gap-3 sm:grid-cols-3"><div><p class="text-xs text-muted-foreground">{m.box_plan_required()}</p><p class="font-medium tabular-nums">{fmt.format(plan.currentPeriod.requiredAmount)}</p></div><div><p class="text-xs text-muted-foreground">{m.box_plan_net_progress()}</p><p class="font-medium tabular-nums">{fmt.format(plan.currentPeriod.netProgress)}</p></div><div><p class="text-xs text-muted-foreground">{m.box_plan_shortfall()}</p><p class="font-medium tabular-nums">{fmt.format(plan.currentPeriod.shortfall)}</p></div></div>
						</section>
					{/if}
				{:else}
					<div class="rounded-xl bg-linear-to-br from-primary/10 via-primary/5 to-transparent p-4 sm:p-5">
						<div class="flex flex-wrap items-end justify-between gap-2"><div><p class="text-sm text-muted-foreground">{m.box_plan_current_balance()}</p><p class="text-3xl font-semibold tabular-nums">{fmt.format(plan.boxBalance)}</p></div><p class="text-sm font-medium tabular-nums">{Math.round(budgetProgress)}%</p></div>
						<Progress class="mt-3 h-2" value={budgetProgress} max={100} aria-label={`${fmt.format(plan.boxBalance)} / ${fmt.format(plan.desiredBalance)}`} />
					</div>

					<div class="grid gap-3 sm:grid-cols-3">
						<div class="rounded-lg border p-3"><p class="text-xs text-muted-foreground">{m.box_plan_desired_balance()}</p><p class="font-semibold tabular-nums">{fmt.format(plan.desiredBalance)}</p></div>
						<div class="rounded-lg border p-3"><p class="text-xs text-muted-foreground">{m.box_plan_current_balance()}</p><p class="font-semibold tabular-nums">{fmt.format(plan.boxBalance)}</p></div>
						<div class="rounded-lg border p-3"><p class="text-xs text-muted-foreground">{m.box_plan_suggested_top_up()}</p><p class="font-semibold tabular-nums">{fmt.format(plan.suggestedTopUp)}</p></div>
					</div>
					{#if active && plan.suggestedTopUp > 0}
						<div class="flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-muted/30 p-3"><p class="max-w-lg text-sm text-muted-foreground">{m.box_plan_top_up_description()}</p><Button onclick={() => onTopUp(plan.suggestedTopUp)} disabled={topUpDisabled} title={topUpDisabled ? m.boxes_deposits_blocked() : undefined}><CircleDollarSign data-icon="inline-start" />{m.box_plan_top_up()}</Button></div>
					{/if}

					<section class="rounded-lg border p-4" aria-labelledby="budget-current-period">
						<div><h3 id="budget-current-period" class="font-semibold">{m.box_plan_period_title()}</h3><p class="text-sm text-muted-foreground">{budgetPeriodRange(plan.currentPeriod)}</p></div>
						<div class="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4"><div><p class="text-xs text-muted-foreground">{m.box_plan_deposits()}</p><p class="font-medium tabular-nums">{fmt.format(budgetFunded(plan.currentPeriod))}</p></div><div><p class="text-xs text-muted-foreground">{m.box_plan_funded_spending()}</p><p class="font-medium tabular-nums">{fmt.format(plan.currentPeriod.fundedSpending)}</p></div><div><p class="text-xs text-muted-foreground">{m.box_plan_withdrawals()}</p><p class="font-medium tabular-nums">{fmt.format(plan.currentPeriod.withdrawals + plan.currentPeriod.transfersOut)}</p></div><div><p class="text-xs text-muted-foreground">{m.box_plan_remaining()}</p><p class="font-medium tabular-nums">{fmt.format(plan.currentPeriod.closingBalance)}</p></div></div>
					</section>
				{/if}
			</Tabs.Content>

			<Tabs.Content value="calendar" class="mt-4">
				<PlanCalendar {plan} {movements} {locale} />
			</Tabs.Content>

			<Tabs.Content value="history" class="mt-4 space-y-6">
				<section class="space-y-3" aria-labelledby="plan-period-history">
					<div><h3 id="plan-period-history" class="font-semibold">{m.box_plan_periods_title()}</h3><p class="text-sm text-muted-foreground">{m.box_plan_periods_description()}</p></div>
					<Table.Root>
						<Table.Caption class="sr-only">{m.box_plan_periods_description()}</Table.Caption>
						<Table.Header><Table.Row>
							<Table.Head>{m.common_date()}</Table.Head>
							<Table.Head>{m.box_plan_revisions_title()}</Table.Head>
							{#if plan.type === 'SAVING_GOAL'}<Table.Head>{m.common_status()}</Table.Head><Table.Head class="text-right">{m.box_plan_required()}</Table.Head><Table.Head class="text-right">{m.box_plan_net_progress()}</Table.Head><Table.Head class="text-right">{m.box_plan_shortfall()}</Table.Head>{:else}<Table.Head class="text-right">{m.box_plan_deposits()}</Table.Head><Table.Head class="text-right">{m.box_plan_funded_spending()}</Table.Head><Table.Head class="text-right">{m.box_plan_remaining()}</Table.Head>{/if}
						</Table.Row></Table.Header>
						<Table.Body>
							{#if plan.type === 'SAVING_GOAL'}
								{@const goalPeriods = plan.currentPeriod ? [...plan.periods.filter((period) => period.id !== plan.currentPeriod?.id), plan.currentPeriod] : plan.periods}
								{#each goalPeriods.toReversed() as period (period.id)}
									<Table.Row><Table.Cell class="whitespace-nowrap">{goalPeriodRange(period)}</Table.Cell><Table.Cell class="whitespace-nowrap">{m.box_plan_revision_number({ id: period.revisionId })}</Table.Cell><Table.Cell><Badge variant={statusVariant(period.status)}>{statusLabel(period.status)}</Badge></Table.Cell><Table.Cell class="text-right tabular-nums">{fmt.format(period.requiredAmount)}</Table.Cell><Table.Cell class="text-right tabular-nums">{fmt.format(period.netProgress)}</Table.Cell><Table.Cell class="text-right tabular-nums">{fmt.format(period.shortfall)}</Table.Cell></Table.Row>
								{/each}
							{:else}
								{@const budgetPeriods = [...plan.periods.filter((period) => period.id !== plan.currentPeriod.id), plan.currentPeriod]}
								{#each budgetPeriods.toReversed() as period (period.id)}
									<Table.Row><Table.Cell class="whitespace-nowrap">{budgetPeriodRange(period)}</Table.Cell><Table.Cell class="whitespace-nowrap">{m.box_plan_revision_number({ id: period.revisionId })}</Table.Cell><Table.Cell class="text-right tabular-nums">{fmt.format(budgetFunded(period))}</Table.Cell><Table.Cell class="text-right tabular-nums">{fmt.format(period.fundedSpending)}</Table.Cell><Table.Cell class="text-right tabular-nums">{fmt.format(period.closingBalance)}</Table.Cell></Table.Row>
								{/each}
							{/if}
						</Table.Body>
					</Table.Root>
				</section>

				<section class="space-y-3" aria-labelledby="plan-revisions-history">
					<div><h3 id="plan-revisions-history" class="font-semibold">{m.box_plan_revisions_title()}</h3><p class="text-sm text-muted-foreground">{m.box_plan_revisions_description()}</p></div>
					<Table.Root>
						<Table.Caption class="sr-only">{m.box_plan_revisions_description()}</Table.Caption>
						<Table.Header><Table.Row><Table.Head>{m.box_plan_effective_from()}</Table.Head><Table.Head>{m.box_plan_cadence()}</Table.Head><Table.Head class="text-right">{plan.type === 'SAVING_GOAL' ? m.box_plan_target_amount() : m.box_plan_desired_balance()}</Table.Head>{#if plan.type === 'SAVING_GOAL'}<Table.Head class="text-right">{m.box_plan_regular_commitment()}</Table.Head>{/if}<Table.Head>{m.common_status()}</Table.Head></Table.Row></Table.Header>
						<Table.Body>
							{#if plan.type === 'SAVING_GOAL'}
								{#each plan.revisions.toReversed() as revision (revision.id)}
									<Table.Row><Table.Cell class="whitespace-nowrap">{date(revision.effectiveFrom)}</Table.Cell><Table.Cell>{cadenceLabel(revision.cadence)}</Table.Cell><Table.Cell class="text-right tabular-nums">{fmt.format(revision.targetAmount)}</Table.Cell><Table.Cell class="text-right tabular-nums">{fmt.format(revision.regularCommitment)}</Table.Cell><Table.Cell>{#if revision.scheduled}<Badge variant="warning">{m.box_plan_revision_scheduled()}</Badge>{:else if revision.id === currentRevisionId}<Badge variant="info">{m.box_plan_revision_current()}</Badge>{:else if revision.supersededAt !== null}<Badge variant="outline">{m.box_plan_revision_replaced()}</Badge>{:else}<Badge variant="secondary">{m.common_applied()}</Badge>{/if}</Table.Cell></Table.Row>
								{/each}
							{:else}
								{#each plan.revisions.toReversed() as revision (revision.id)}
									<Table.Row><Table.Cell class="whitespace-nowrap">{date(revision.effectiveFrom)}</Table.Cell><Table.Cell>{cadenceLabel(revision.cadence)}</Table.Cell><Table.Cell class="text-right tabular-nums">{fmt.format(revision.desiredBalance)}</Table.Cell><Table.Cell>{#if revision.scheduled}<Badge variant="warning">{m.box_plan_revision_scheduled()}</Badge>{:else if revision.id === currentRevisionId}<Badge variant="info">{m.box_plan_revision_current()}</Badge>{:else if revision.supersededAt !== null}<Badge variant="outline">{m.box_plan_revision_replaced()}</Badge>{:else}<Badge variant="secondary">{m.common_applied()}</Badge>{/if}</Table.Cell></Table.Row>
								{/each}
							{/if}
						</Table.Body>
					</Table.Root>
				</section>
			</Tabs.Content>
		</Tabs.Root>

		{#if !active && plan.closedAt}
			<p class="mt-4 text-xs text-muted-foreground">{m.box_plan_closed_on({ date: timestamp(plan.closedAt) })}{#if plan.completionAmount !== null} · {m.box_plan_completion_balance({ amount: fmt.format(plan.completionAmount) })}{/if}</p>
		{/if}
	</Card.Content>
</Card.Root>

{#if active}
	<PlanRevisionDialog bind:open={revisionOpen} {plan} {locale} onApplied={onChanged} />
{/if}
