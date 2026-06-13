<script lang="ts">
	import Dock from './dock.svelte';
	import FloatingActionBar from './floating-action-bar.svelte';
	import { dockActionStore } from './dock-action.svelte';
	import { AdaptiveConfirm } from '$lib/components/adaptive-confirm';
	import { Toaster } from '$lib/components/ui/sonner';

	const { children } = $props();

	// While a page registers a contextual bulk action (e.g. trash selection),
	// the dock is swapped for its action bar.
	const dockAction = $derived(dockActionStore.current);
</script>

<div class="flex min-h-dvh bg-background">
	<Toaster />
	<AdaptiveConfirm />

	<main class="min-h-0 flex-1 overflow-y-auto">
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
