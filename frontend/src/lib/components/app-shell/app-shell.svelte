<script lang="ts">
	import Dock from './dock.svelte';
	import FloatingActionBar from './floating-action-bar.svelte';
	import { dockActionStore } from './dock-action.svelte';
	import { AdaptiveConfirm } from '$lib/components/adaptive-confirm';
	import { Toaster } from '$lib/components/ui/sonner';
	import { AlertTriangle, WalletCards } from '@lucide/svelte';
	import { mxnFormatter } from '$lib/formatting';
	import { page } from '$app/stores';
	import { m } from '$lib/paraglide/messages.js';
	import type { BalanceSummary } from '$lib/types/boxes';

	const {
		children,
		balanceSummary,
		locale,
	}: {
		children: any;
		balanceSummary: BalanceSummary;
		locale: string;
	} = $props();

	// While a page registers a contextual bulk action (e.g. trash selection),
	// the dock is swapped for its action bar.
	const dockAction = $derived(dockActionStore.current);
	const fmt = $derived(mxnFormatter(locale));
	const isUnreconciled = $derived(balanceSummary.availableToSpend < 0);
	// The dashboard already states this twice — a banner that explains it and
	// offers the action, and the Disponible para gastar card that carries the
	// figure itself. A third copy in the header was pure repetition, so the
	// chip stands down there and keeps doing its job everywhere else.
	const showBalanceChip = $derived($page.url.pathname !== '/');
</script>

<div class="flex min-h-dvh bg-background">
	<Toaster />
	<AdaptiveConfirm />

	<main class="min-h-0 flex-1 overflow-y-auto">
		{#if showBalanceChip}
			<header class="sticky top-0 z-30 border-b bg-background/90 backdrop-blur-xl">
				<div class="mx-auto flex w-full max-w-7xl items-center justify-end px-4 py-2 sm:px-6 lg:px-8">
					<a
					href="/boxes"
					class="group flex min-w-0 items-center gap-2 rounded-lg px-2 py-1.5 text-sm transition-colors hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring {isUnreconciled ? 'text-destructive' : ''}"
					aria-label={`${m.balance_available_to_spend()}: ${fmt.format(balanceSummary.availableToSpend)}`}
				>
					{#if isUnreconciled}
						<AlertTriangle class="size-4 shrink-0" aria-hidden="true" />
					{:else}
						<WalletCards class="size-4 shrink-0 text-muted-foreground group-hover:text-foreground" aria-hidden="true" />
					{/if}
					<span class="hidden text-muted-foreground sm:inline">{m.balance_available_to_spend()}</span>
					<strong class="truncate tabular-nums">{fmt.format(balanceSummary.availableToSpend)}</strong>
					{#if isUnreconciled}
						<span class="hidden text-xs font-medium md:inline">{m.balance_reconcile_short()}</span>
					{/if}
					</a>
				</div>
			</header>
		{/if}
		<div class="mx-auto w-full max-w-7xl p-4 pb-28 sm:p-6 sm:pb-28 lg:p-8 lg:pb-28">
			{@render children()}
		</div>
	</main>

	{#if dockAction}
		<FloatingActionBar bar={dockAction} />
	{:else}
		<Dock />
	{/if}
</div>
