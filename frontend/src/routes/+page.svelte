<script lang="ts">
	import * as Card from '$lib/components/ui/card';
	import * as Alert from '$lib/components/ui/alert';
	import { Button } from '$lib/components/ui/button';
	import { MonthlyBarChart, NetTrendChart } from '$lib/components/dashboard';
	import { AlertTriangle, ArrowRight, ChevronLeft, ChevronRight } from '@lucide/svelte';
	import { SectionUnavailable } from '$lib/components/section-status';
	import { mxnFormatter } from '$lib/formatting';
	import { sectionValue } from '$lib/types/section';
	import { m } from '$lib/paraglide/messages.js';
	import type { PageData } from './$types';

	let { data }: { data: PageData } = $props();

	const fmt = $derived(mxnFormatter(data.preferences.locale));
	const mxn = (v: number) => fmt.format(v);

	const prevYear = $derived(data.year - 1);
	const nextYear = $derived(data.year + 1);
	const currentYear = new Date().getFullYear();
	// `null` means the figures could not be loaded — a different fact from a
	// zero balance, so nothing below renders a total from a missing summary.
	const summary = $derived(sectionValue(data.summary));
	const warnings = $derived(sectionValue(data.accountWarnings));
	const isUnreconciled = $derived(summary !== null && summary.availableToSpend < 0);
	const accountWarnings = $derived(warnings?.items ?? []);
	// "Partial" means some of this page loaded and some did not. When nothing
	// loaded the sections say so individually, and when only the warning list is
	// incomplete its own notice is more specific than this one.
	const partialData = $derived((summary === null) !== (warnings === null));
</script>

<svelte:head><title>{m.nav_dashboard()} · Keenti</title></svelte:head>

<!-- No page padding here: the app shell already pads its content well. The
     duplicated `p-6` this file used to carry stacked on top of it. -->
<div class="space-y-8">
	<!-- Every other route shows its name as a visible h1, but the dashboard is
	     the one page whose name adds nothing on screen — the dock already marks
	     it as current, and its first rows are the figures the page exists for.
	     Kept for the document outline and for screen readers only. -->
	<h1 class="sr-only">{m.nav_dashboard()}</h1>

	{#if partialData}
		<p class="text-sm text-muted-foreground">{m.section_partial_notice()}</p>
	{/if}

	{#if data.accountWarnings.status === 'unavailable'}
		<SectionUnavailable title={m.section_warnings_unavailable()} />
	{:else if warnings?.partial}
		<p class="text-sm text-muted-foreground">{m.section_warnings_partial()}</p>
	{/if}

	{#if isUnreconciled || accountWarnings.length > 0}
		<div class="space-y-3">
			{#if isUnreconciled && summary}
				<Alert.Root variant="destructive">
					<AlertTriangle aria-hidden="true" />
					<Alert.Title>{m.balance_reconciliation_required()}</Alert.Title>
					<Alert.Description>
							{m.balance_reconciliation_description({ amount: mxn(Math.abs(summary.availableToSpend)) })}
					</Alert.Description>
					<Alert.Action>
						<Button href="/boxes" size="sm" variant="outline">{m.balance_reconcile_action()}</Button>
					</Alert.Action>
				</Alert.Root>
			{/if}

			{#each accountWarnings as warning}
				<Alert.Root variant="destructive">
					<AlertTriangle aria-hidden="true" />
					<Alert.Title>{warning.title}</Alert.Title>
					<Alert.Description>{warning.description}</Alert.Description>
					<Alert.Action>
						<Button href={warning.href} size="sm" variant="outline">{m.dashboard_review_account()}</Button>
					</Alert.Action>
				</Alert.Root>
			{/each}
		</div>
	{/if}

	<!-- All-time position. Net Balance is the headline figure, so it gets the
	     hero treatment and the two derived figures sit beside it as a pair
	     rather than competing at equal weight in a flat three-up grid. -->
	{#if summary === null}
		<SectionUnavailable title={m.section_balance_unavailable()} />
	{:else}
	<section class="grid grid-cols-1 gap-4 lg:grid-cols-3">
		<Card.Root class="lg:col-span-1">
			<Card.Header class="gap-1">
				<Card.Description>{m.dashboard_net_balance()}</Card.Description>
				<Card.Title class="font-heading text-4xl font-bold tabular-nums tracking-tight">
					{mxn(summary.netBalance)}
				</Card.Title>
			</Card.Header>
			<Card.Content>
				<p class="text-xs text-muted-foreground">{m.dashboard_net_balance_description()}</p>
			</Card.Content>
		</Card.Root>

		<div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:col-span-2">
			<a
				href="/boxes"
				class="rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
			>
				<Card.Root class="h-full transition-colors hover:bg-muted/40">
					<Card.Header class="gap-1">
						<Card.Description>{m.balance_in_boxes()}</Card.Description>
						<Card.Title class="text-2xl font-bold tabular-nums">{mxn(summary.inBoxes)}</Card.Title>
					</Card.Header>
					<Card.Content class="flex items-center justify-between gap-2">
						<p class="text-xs text-muted-foreground">{m.dashboard_in_boxes_description()}</p>
						<ArrowRight class="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
					</Card.Content>
				</Card.Root>
			</a>

			<Card.Root class={isUnreconciled ? 'ring-destructive/60' : ''}>
				<Card.Header class="gap-1">
					<Card.Description>{m.balance_available_to_spend()}</Card.Description>
					<Card.Title class="text-2xl font-bold tabular-nums {isUnreconciled ? 'text-destructive' : ''}">
						{mxn(summary.availableToSpend)}
					</Card.Title>
				</Card.Header>
				<Card.Content>
					<p class="text-xs text-muted-foreground">{m.dashboard_available_to_spend_description()}</p>
				</Card.Content>
			</Card.Root>
		</div>
	</section>
	{/if}

	<!-- Everything below is scoped to the selected year, so the year control
	     heads the section instead of floating between the figures it filters
	     and the charts it also filters. -->
	<section class="space-y-4">
		<div class="flex items-center justify-between gap-3">
			<h2 class="font-heading text-lg font-semibold">{m.dashboard_year_summary()}</h2>
			<div class="flex items-center gap-1 rounded-lg border p-0.5">
				<Button
					variant="ghost"
					size="sm"
					href="?year={prevYear}"
					aria-label={m.dashboard_previous_year({ year: prevYear })}
					class="size-8 p-0"
				>
					<ChevronLeft class="size-4" aria-hidden="true" />
				</Button>
				<span class="min-w-12 text-center text-sm font-semibold tabular-nums">{data.year}</span>
				<Button
					variant="ghost"
					size="sm"
					href={nextYear > currentYear ? undefined : `?year=${nextYear}`}
					aria-label={m.dashboard_next_year({ year: nextYear })}
					disabled={nextYear > currentYear}
					class="size-8 p-0 {nextYear > currentYear ? 'pointer-events-none opacity-40' : ''}"
				>
					<ChevronRight class="size-4" aria-hidden="true" />
				</Button>
			</div>
		</div>

		<!-- The year control above stays usable even when the figures it filters
		     could not be loaded, so the User can move to another year. -->
		{#if summary === null}
			<SectionUnavailable title={m.section_year_summary_unavailable()} />
		{:else}
		<div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
			<Card.Root>
				<Card.Header class="gap-1">
					<Card.Description>{m.dashboard_total_income({ year: data.year })}</Card.Description>
					<Card.Title class="text-2xl font-bold tabular-nums text-money-positive">
						{mxn(summary.totalIngress)}
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
						{mxn(summary.totalEgress)}
					</Card.Title>
				</Card.Header>
				<Card.Content>
					<p class="text-xs text-muted-foreground">{m.dashboard_total_expenses_description()}</p>
				</Card.Content>
			</Card.Root>
		</div>

		<div class="grid grid-cols-1 gap-4 xl:grid-cols-2">
			<MonthlyBarChart monthly={summary.monthly} year={data.year} locale={data.preferences.locale} />
			<NetTrendChart monthly={summary.monthly} year={data.year} locale={data.preferences.locale} />
		</div>
		{/if}
	</section>
</div>
