<script lang="ts">
	import * as Card from '$lib/components/ui/card';
	import * as Alert from '$lib/components/ui/alert';
	import { Button } from '$lib/components/ui/button';
	import { MonthlyBarChart, NetTrendChart } from '$lib/components/dashboard';
	import { AlertTriangle, ArrowRight } from '@lucide/svelte';
	import { mxnFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
	import type { PageData } from './$types';

	let { data }: { data: PageData } = $props();

	const fmt = $derived(mxnFormatter(data.preferences.locale));
	const mxn = (v: number) => fmt.format(v);

	const prevYear = $derived(data.year - 1);
	const nextYear = $derived(data.year + 1);
	const currentYear = new Date().getFullYear();
	const isUnreconciled = $derived(data.summary.availableToSpend < 0);
	const accountWarnings = $derived(data.accountWarnings);
</script>

<div class="space-y-6 p-6">
	{#if isUnreconciled}
		<Alert.Root variant="destructive">
			<AlertTriangle aria-hidden="true" />
			<Alert.Title>{m.balance_reconciliation_required()}</Alert.Title>
			<Alert.Description>
				{m.balance_reconciliation_description({ amount: mxn(Math.abs(data.summary.availableToSpend)) })}
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
			<Alert.Action><Button href={warning.href} size="sm" variant="outline">Review account</Button></Alert.Action>
		</Alert.Root>
	{/each}

	<!-- All-time balance cards -->
	<div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
		<Card.Root>
			<Card.Header class="pb-2">
				<Card.Description>{m.dashboard_net_balance()}</Card.Description>
				<Card.Title class="text-2xl font-bold">{mxn(data.summary.netBalance)}</Card.Title>
			</Card.Header>
			<Card.Content>
				<p class="text-muted-foreground text-xs">{m.dashboard_net_balance_description()}</p>
			</Card.Content>
		</Card.Root>
		<a
			href="/boxes"
			class="rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
		>
			<Card.Root class="h-full transition-colors hover:bg-muted/40">
				<Card.Header class="pb-2">
					<Card.Description>{m.balance_in_boxes()}</Card.Description>
					<Card.Title class="text-2xl font-bold">{mxn(data.summary.inBoxes)}</Card.Title>
				</Card.Header>
				<Card.Content class="flex items-center justify-between gap-2">
					<p class="text-muted-foreground text-xs">{m.dashboard_in_boxes_description()}</p>
					<ArrowRight class="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
				</Card.Content>
			</Card.Root>
		</a>
		<Card.Root class={isUnreconciled ? 'ring-destructive/60' : ''}>
			<Card.Header class="pb-2">
				<Card.Description>{m.balance_available_to_spend()}</Card.Description>
				<Card.Title class="text-2xl font-bold {isUnreconciled ? 'text-destructive' : ''}">
					{mxn(data.summary.availableToSpend)}
				</Card.Title>
			</Card.Header>
			<Card.Content>
				<p class="text-muted-foreground text-xs">{m.dashboard_available_to_spend_description()}</p>
			</Card.Content>
		</Card.Root>
	</div>

	<!-- Year-filtered totals -->
	<div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
		<Card.Root>
			<Card.Header class="pb-2">
				<Card.Description>{m.dashboard_total_income({ year: data.year })}</Card.Description>
				<Card.Title class="text-2xl font-bold text-green-600">{mxn(data.summary.totalIngress)}</Card.Title>
			</Card.Header>
			<Card.Content>
				<p class="text-muted-foreground text-xs">{m.dashboard_total_income_description()}</p>
			</Card.Content>
		</Card.Root>
		<Card.Root>
			<Card.Header class="pb-2">
				<Card.Description>{m.dashboard_total_expenses({ year: data.year })}</Card.Description>
				<Card.Title class="text-2xl font-bold text-red-600">{mxn(data.summary.totalEgress)}</Card.Title>
			</Card.Header>
			<Card.Content>
				<p class="text-muted-foreground text-xs">{m.dashboard_total_expenses_description()}</p>
			</Card.Content>
		</Card.Root>
	</div>

	<!-- Year Selector -->
	<div class="flex items-center gap-3">
		<Button variant="outline" size="sm" href="?year={prevYear}">← {prevYear}</Button>
		<span class="text-lg font-semibold">{data.year}</span>
		<Button
			variant="outline"
			size="sm"
			href={nextYear > currentYear ? undefined : `?year=${nextYear}`}
			class={nextYear > currentYear ? 'pointer-events-none opacity-40' : ''}
		>
			{nextYear} →
		</Button>
	</div>


	<MonthlyBarChart monthly={data.summary.monthly} year={data.year} locale={data.preferences.locale} />
	<NetTrendChart monthly={data.summary.monthly} year={data.year} locale={data.preferences.locale} />
</div>
