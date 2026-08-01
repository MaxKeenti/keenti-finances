<script lang="ts">
	import { parseDate } from '@internationalized/date';
	import * as CalendarUI from '$lib/components/ui/calendar';
	import { formatLocale } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
	import type { BoxMovementDto } from '$lib/types/boxes';
	import type { BoxPlan, SavingGoalPeriod, SpendingBudgetPeriod } from '$lib/types/box-plans';
	import { cn } from '$lib/utils';

	let {
		plan,
		movements,
		locale,
	}: { plan: BoxPlan; movements: BoxMovementDto[]; locale: string } = $props();

	type CalendarPeriod = {
		start: string;
		end: string;
		status: 'OPEN' | 'ACHIEVED' | 'MISSED' | 'BUDGET';
	};

	const periods = $derived.by((): CalendarPeriod[] => {
		if (plan.type === 'SAVING_GOAL') {
			const all = plan.currentPeriod
				? [...plan.periods.filter((period) => period.id !== plan.currentPeriod?.id), plan.currentPeriod]
				: plan.periods;
			return all.map((period: SavingGoalPeriod) => ({
				start: period.startDate,
				end: period.endDate,
				status: period.status,
			}));
		}
		const all = [
			...plan.periods.filter((period) => period.id !== plan.currentPeriod.id),
			plan.currentPeriod,
		];
		return all.map((period: SpendingBudgetPeriod) => ({
			start: period.periodStart,
			end: period.periodEnd,
			status: 'BUDGET',
		}));
	});
	const revisionDates = $derived(new Set(plan.revisions.map((revision) => revision.effectiveFrom)));
	const fundedTransactionDates = $derived(
		new Set(
			movements
				.filter((movement) => movement.type === 'SPENDING')
				.map((movement) => movement.effectiveDate),
		),
	);
	const movementDates = $derived(
		new Set(
			movements
				.filter((movement) => movement.type !== 'SPENDING')
				.map((movement) => movement.effectiveDate),
		),
	);
	const focusDate = $derived(
		plan.type === 'SAVING_GOAL'
			? (plan.currentPeriod?.startDate ?? plan.targetDate)
			: plan.currentPeriod.periodStart,
	);
	const placeholder = $derived(parseDate(focusDate));
	const fullDateFmt = $derived(
		new Intl.DateTimeFormat(formatLocale(locale), { dateStyle: 'full', timeZone: 'UTC' }),
	);

	function metadata(date: string): {
		classes: string;
		labels: string[];
	} {
		const period = periods.find((candidate) => date >= candidate.start && date <= candidate.end);
		const labels: string[] = [
			fullDateFmt.format(new Date(`${date}T00:00:00Z`)),
		];
		let classes = '';
		if (period?.status === 'ACHIEVED') {
			classes = 'border-b-2 border-green-600 bg-green-500/15 font-semibold text-green-900 dark:text-green-200';
			labels.push(m.box_plan_calendar_achieved());
		} else if (period?.status === 'MISSED') {
			classes = 'outline outline-1 outline-dashed outline-destructive bg-destructive/15 text-destructive';
			labels.push(m.box_plan_calendar_missed());
		} else if (period) {
			classes = 'ring-1 ring-primary/60 ring-inset';
			labels.push(m.box_plan_calendar_current());
		}
		if (period && (date === period.start || date === period.end)) {
			classes += ' underline decoration-2 underline-offset-2';
			labels.push(m.box_plan_calendar_boundary());
		}
		if (plan.type === 'SAVING_GOAL' && plan.status === 'OVERDUE' && date === plan.targetDate) {
			classes += ' ring-2 ring-destructive ring-offset-1';
			labels.push(m.box_plan_status_overdue());
		}
		if (revisionDates.has(date)) {
			classes += ' relative after:absolute after:right-1 after:top-1 after:size-1.5 after:rounded-full after:bg-blue-500';
			labels.push(m.box_plan_calendar_revision());
		}
		if (movementDates.has(date)) {
			classes += ' relative before:absolute before:bottom-1 before:left-1/2 before:size-1 before:-translate-x-1/2 before:rounded-full before:bg-amber-500';
			labels.push(m.box_plan_calendar_movement());
		}
		if (fundedTransactionDates.has(date)) {
			classes += ' border-l-2 border-l-purple-500';
			labels.push(m.box_plan_calendar_transaction());
		}
		return { classes, labels };
	}
</script>

<section class="space-y-3" aria-labelledby="plan-calendar-title">
	<div>
		<h3 id="plan-calendar-title" class="font-semibold">{m.box_plan_calendar_title()}</h3>
		<p class="text-sm text-muted-foreground">{m.box_plan_calendar_description()}</p>
	</div>
	<div class="grid items-start gap-4 lg:grid-cols-[auto_1fr]">
		<div class="w-fit max-w-full overflow-x-auto rounded-lg border bg-card p-1">
			<CalendarUI.Calendar type="single" {placeholder} readonly locale={formatLocale(locale)}>
				{#snippet day({ day, outsideMonth })}
					{@const meta = metadata(day.toString())}
					<CalendarUI.Day
						class={cn(meta.classes, outsideMonth && 'opacity-35')}
						title={meta.labels.join(' · ')}
						aria-label={meta.labels.join(', ')}
					/>
				{/snippet}
			</CalendarUI.Calendar>
		</div>
		<ul class="grid gap-2 text-sm sm:grid-cols-2 lg:grid-cols-1" aria-label={m.box_plan_calendar_title()}>
			<li class="flex items-center gap-2"><span class="flex size-5 items-center justify-center rounded bg-green-500/15 text-xs text-green-800 dark:text-green-300" aria-hidden="true">✓</span>{m.box_plan_calendar_achieved()}</li>
			<li class="flex items-center gap-2"><span class="flex size-5 items-center justify-center rounded bg-destructive/15 text-xs text-destructive" aria-hidden="true">!</span>{m.box_plan_calendar_missed()}</li>
			<li class="flex items-center gap-2"><span class="size-5 rounded ring-1 ring-primary/60 ring-inset" aria-hidden="true"></span>{m.box_plan_calendar_current()}</li>
			<li class="flex items-center gap-2"><span class="size-2 rounded-full bg-blue-500" aria-hidden="true"></span>{m.box_plan_calendar_revision()}</li>
			<li class="flex items-center gap-2"><span class="size-2 rounded-full bg-amber-500" aria-hidden="true"></span>{m.box_plan_calendar_movement()}</li>
			<li class="flex items-center gap-2"><span class="h-5 border-l-2 border-purple-500" aria-hidden="true"></span>{m.box_plan_calendar_transaction()}</li>
			<li class="flex items-center gap-2"><span class="text-sm font-semibold underline decoration-2 underline-offset-2" aria-hidden="true">1</span>{m.box_plan_calendar_boundary()}</li>
		</ul>
	</div>
</section>
