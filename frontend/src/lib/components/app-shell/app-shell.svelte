<script lang="ts">
	import Dock from './dock.svelte';
	import { AdaptiveConfirm } from '$lib/components/adaptive-confirm';
	import { Toaster } from '$lib/components/ui/sonner';

	const { children } = $props();
</script>

<div class="flex min-h-dvh bg-background">
	<Toaster />
	<AdaptiveConfirm />

	<main class="min-h-0 flex-1 overflow-y-auto">
		<div class="mx-auto w-full max-w-7xl p-4 pb-28 sm:p-6 sm:pb-28 lg:p-8 lg:pb-28">
			{@render children()}
		</div>
	</main>

	<Dock />
</div>
