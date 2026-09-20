<script lang="ts">
	/**
	 * The dashboard (Phase 4).
	 *
	 * Five sections in the order a User asks their questions: where do I stand,
	 * what needs me, what am I working toward, what is coming in, and what has
	 * the year looked like. Each one comes from the composed read model and each
	 * one is independently available — a section that failed says so with a
	 * Retry while its neighbours keep their figures.
	 *
	 * Three rules this page keeps:
	 *
	 * - A failed read is never a zero. Nothing below substitutes `$0.00`, an
	 *   empty list, or a reassuring status for data that did not arrive.
	 * - Reading changes nothing. No section generates billing, advances a
	 *   generation cursor, or moves money; the actions link out to the existing
	 *   workflows where those decisions are made deliberately.
	 * - The year control scopes the history section only. Moving between years
	 *   never changes the current position, which is all-time by definition.
	 */
	import { Button } from '$lib/components/ui/button';
	import * as Card from '$lib/components/ui/card';
	import {
		AttentionSection,
		ExpectedSection,
		MonthlyBarChart,
		NetTrendChart,
		PlansSection,
		PositionSection,
		SetupSection,
	} from '$lib/components/dashboard';
	import { ChevronLeft, ChevronRight } from '@lucide/svelte';
	import { SectionUnavailable } from '$lib/components/section-status';
	import { mxnFormatter } from '$lib/formatting';
	import { sectionValue } from '$lib/types/section';
	import { m } from '$lib/paraglide/messages.js';
	import type { PageData } from './$types';

	let { data }: { data: PageData } = $props();

	const fmt = $derived(mxnFormatter(data.preferences.locale));
	const mxn = (value: number) => fmt.format(value);

	const prevYear = $derived(data.year - 1);
	const nextYear = $derived(data.year + 1);
	const currentYear = $derived(data.obligationToday.status === 'ok'
		? Number(data.obligationToday.day.slice(0, 4)) : null);
	const nextYearDisabled = $derived(currentYear === null || nextYear > currentYear);

	// `null` here means the whole overview could not be loaded or parsed, which
	// is different from any individual section being unavailable.
	const overview = $derived(sectionValue(data.overview));
	const position = $derived(overview === null ? null : sectionValue(overview.position));
	const attention = $derived(overview === null ? null : sectionValue(overview.attention));
	const plans = $derived(overview === null ? null : sectionValue(overview.plans));
	const expected = $derived(overview === null ? null : sectionValue(overview.expected));
	const history = $derived(overview === null ? null : sectionValue(overview.history));

	// The authoritative tracking read, which decides how the Net Balance is
	// explained (decision D1). It comes from the layout and fails independently,
	// so a total can be shown while its formula stays unexplained.
	const tracking = $derived(sectionValue(data.accountTracking));

	// One captured instant per render, resolved in the User's own time zone. The
	// backend reports the day it resolved too, but the labels are derived from
	// this one so the dashboard and the account pages cannot disagree about
	// which calendar day it is.
	const today = $derived(data.obligationToday);

	// A new User whose position loaded and says nothing is set up yet. This is a
	// fact about their setup, not a failure, and it never replaces an
	// unavailable section.
	const needsSetup = $derived(position !== null && position.setupRequired);

	const loadedCount = $derived(
		[position, attention, plans, expected, history].filter((section) => section !== null).length,
	);
	const partialData = $derived(overview !== null && loadedCount > 0 && loadedCount < 5);
</script>

<svelte:head><title>{m.nav_dashboard()} · Keenti</title></svelte:head>

<!-- No page padding: the app shell already pads its content. -->
<div class="space-y-8">
	<!-- The dock already marks the dashboard as current and the first rows are
	     the figures the page exists for, so its name is for the document outline
	     and screen readers only. -->
	<h1 class="sr-only">{m.nav_dashboard()}</h1>

	{#if partialData}
		<p class="text-sm text-muted-foreground">{m.section_partial_notice()}</p>
	{/if}

	{#if overview === null}
		<SectionUnavailable title={m.section_overview_unavailable()} />
	{:else}
		{#if needsSetup}
			<SetupSection />
		{/if}

		<!-- 1 — Current position -->
		{#if position === null}
			<SectionUnavailable title={m.section_position_unavailable()} />
		{:else}
			<PositionSection {position} {tracking} format={mxn} />
		{/if}

		<!-- 2 — Needs attention -->
		{#if attention === null}
			<SectionUnavailable title={m.section_attention_unavailable()} />
		{:else}
			<AttentionSection
				{attention}
				{position}
				{today}
				format={mxn}
				locale={data.preferences.locale}
			/>
		{/if}

		<!-- 3 — Your plans -->
		{#if plans === null}
			<SectionUnavailable title={m.section_plans_unavailable()} />
		{:else}
			<PlansSection {plans} format={mxn} />
		{/if}

		<!-- 4 — Money expected -->
		{#if expected === null}
			<SectionUnavailable title={m.section_expected_unavailable()} />
		{:else}
			<ExpectedSection {expected} format={mxn} locale={data.preferences.locale} />
		{/if}
	{/if}

	<!-- 5 — History. The year control heads the section it filters and stays
	     usable even when the figures below it could not be loaded. -->
	<section class="space-y-4" aria-labelledby="dashboard-history-heading">
		<div class="flex items-center justify-between gap-3">
			<h2 id="dashboard-history-heading" class="font-heading text-lg font-semibold">
				{m.dashboard_year_summary()}
			</h2>
			<div class="flex items-center gap-1 rounded-lg border p-0.5">
				<Button
					variant="ghost"
					size="sm"
					href={prevYear < 1900 ? undefined : `?year=${prevYear}`}
					disabled={prevYear < 1900}
					aria-label={m.dashboard_previous_year({ year: prevYear })}
					class="size-8 p-0"
				>
					<ChevronLeft class="size-4" aria-hidden="true" />
				</Button>
				<span class="min-w-12 text-center text-sm font-semibold tabular-nums">{data.year}</span>
				<Button
					variant="ghost"
					size="sm"
					href={nextYearDisabled ? undefined : `?year=${nextYear}`}
					aria-label={m.dashboard_next_year({ year: nextYear })}
					disabled={nextYearDisabled}
					class="size-8 p-0 {nextYearDisabled ? 'pointer-events-none opacity-40' : ''}"
				>
					<ChevronRight class="size-4" aria-hidden="true" />
				</Button>
			</div>
		</div>

		<p class="text-xs text-muted-foreground">
			{m.dashboard_history_scope_note({ year: data.year })}
		</p>

		{#if history === null}
			<SectionUnavailable title={m.section_history_unavailable()} />
		{:else}
			<div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
				<Card.Root>
					<Card.Header class="gap-1">
						<Card.Description>{m.dashboard_total_income({ year: data.year })}</Card.Description>
						<Card.Title class="text-2xl font-bold tabular-nums text-money-positive">
							{mxn(history.totalIngress)}
						</Card.Title>
					</Card.Header>
					<Card.Content>
						<p class="text-xs text-muted-foreground">{m.dashboard_total_income_description()}</p>
					</Card.Content>
				</Card.Root>
				<Card.Root>
					<Card.Header class="gap-1">
						<Card.Description>{m.dashboard_total_expenses({ year: data.year })}</Card.Description>
						<Card.Title class="text-2xl font-bold tabular-nums text-money-negative">
							{mxn(history.totalEgress)}
						</Card.Title>
					</Card.Header>
					<Card.Content>
						<p class="text-xs text-muted-foreground">{m.dashboard_total_expenses_description()}</p>
					</Card.Content>
				</Card.Root>
			</div>

			<div class="grid grid-cols-1 gap-4 xl:grid-cols-2">
				<MonthlyBarChart
					monthly={history.monthly}
					year={data.year}
					locale={data.preferences.locale}
				/>
				<NetTrendChart monthly={history.monthly} year={data.year} locale={data.preferences.locale} />
			</div>
		{/if}
	</section>
</div>
