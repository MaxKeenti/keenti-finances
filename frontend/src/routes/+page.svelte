<script lang="ts">
	import * as Card from '$lib/components/ui/card';
	import * as Alert from '$lib/components/ui/alert';
	import { Button } from '$lib/components/ui/button';
	import { MonthlyBarChart, NetTrendChart } from '$lib/components/dashboard';
	import { AlertTriangle, ArrowRight, ChevronLeft, ChevronRight } from '@lucide/svelte';
	import { SectionUnavailable } from '$lib/components/section-status';
	import { NetBalanceNote, ShortfallAlert } from '$lib/components/balance';
	import { mxnFormatter } from '$lib/formatting';
	import { sectionValue } from '$lib/types/section';
	import { accountStatementPaymentStatus } from '$lib/obligation-status';
	import { statementAmountLabel, statementStateLabel } from '$lib/statement-labels';
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
	// The tracking read is independent of the balance read: either can fail
	// alone, and neither failure may produce a zero or a guessed formula.
	const tracking = $derived(sectionValue(data.accountTracking));
	const isUnreconciled = $derived(summary !== null && summary.availableToSpend < 0);
	const accountWarnings = $derived(warnings?.items ?? []);

	// One captured instant per render, resolved in the User's own time zone:
	// the same moment is 7 September in Mexico City and 8 September in Tokyo,
	// so a due date compared against the browser's day can read "past due" a
	// day early (decision D2).
	const today = $derived(data.obligationToday);
	// Each Credit Financial Account contributes the one statement that most
	// needs attention, plus every statement flagged for reconciliation review.
	const statementAccounts = $derived(
		(warnings?.statementAccounts ?? []).map((entry) => ({
			entry,
			...accountStatementPaymentStatus({
				statements: entry.statements,
				today,
				read: (statement) => statement,
			}),
		})),
	);
	// A covered statement stays out of the attention list rather than being
	// reported as an obligation.
	const statementRows = $derived(
		statementAccounts.filter(
			({ entry, status }) => entry.statements.length > 0 && status.state !== 'covered',
		),
	);
	// Reconciliation mismatch is an independent review notice (D2), so it is
	// listed from every flagged statement rather than from the one the attention
	// slot happened to select. Filtering covered statements out first — or
	// reading the mismatch flag off the selected statement only — silently drops
	// the notice for a paid-but-mismatched statement and for any statement that
	// lost the priority sort.
	const mismatchRows = $derived(
		statementAccounts.flatMap(({ entry, mismatches }) =>
			mismatches.map(({ statement, status }) => ({ entry, statement, status })),
		),
	);
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

	{#if isUnreconciled || accountWarnings.length > 0 || statementRows.length > 0 || mismatchRows.length > 0}
		<div class="space-y-3">
			<!-- Over-reserving and a negative Net Balance are told apart here, and
			     a withdrawal is only suggested when a Box actually holds money. -->
			<ShortfallAlert totals={summary} format={mxn} />

			<!-- Confirmed statement payments are their own obligation. They are
			     never folded into Available to Spend: the purchases behind them
			     already moved Net Balance and are not subtracted twice. -->
			{#each statementRows as { entry, status } (entry.accountId)}
				<Alert.Root variant={status.state === 'outstanding-upcoming' ? 'default' : 'destructive'}>
					<AlertTriangle aria-hidden="true" />
					<Alert.Title>{entry.accountName} · {statementStateLabel(status.state)}</Alert.Title>
					<Alert.Description class="space-y-1">
						{#if statementAmountLabel(status, mxn, data.preferences.locale)}
							<p>{statementAmountLabel(status, mxn, data.preferences.locale)}</p>
						{/if}
						<p>{m.statement_not_subtracted_note()}</p>
					</Alert.Description>
					<Alert.Action>
						<Button href={entry.href} size="sm" variant="outline">{m.statement_action_review()}</Button>
					</Alert.Action>
				</Alert.Root>
			{/each}

			<!-- Independent review notices: they neither replace a payment state
			     above nor declare the bank's snapshot wrong, so they are listed
			     for every flagged statement — including covered ones. -->
			{#each mismatchRows as { entry, statement, status } (statement.id)}
				<Alert.Root>
					<AlertTriangle aria-hidden="true" />
					<Alert.Title>{m.balance_mismatch_title({ name: entry.accountName })}</Alert.Title>
					<Alert.Description>{m.balance_mismatch_description({ amount: mxn(Math.abs(status.mismatchAmount ?? 0)) })}</Alert.Description>
					<Alert.Action>
						<Button href={entry.href} size="sm" variant="outline">{m.balance_mismatch_action()}</Button>
					</Alert.Action>
				</Alert.Root>
			{/each}

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
				<!-- Which formula produced this total is a fact about the User's
				     setup, not something to infer from the total itself. -->
				<NetBalanceNote {tracking} />
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
					<!-- Explicitly not a cash forecast: unrecorded future essentials,
					     subscriptions and expected receipts are not subtracted here,
					     and an already-recorded card purchase is not subtracted a
					     second time by an unpaid statement. -->
					<p class="mt-1 text-xs text-muted-foreground">{m.balance_available_note()}</p>
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
