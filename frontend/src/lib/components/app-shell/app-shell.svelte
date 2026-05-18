<script lang="ts">
	import Dock from './dock.svelte';
	import { Toaster } from '$lib/components/ui/sonner';

	const { children } = $props();
</script>

<div class="flex flex-col bg-background h-dvh">
	<Toaster />

	<main class="flex-1 overflow-y-auto">
		<div class="p-4 sm:p-6">
			{@render children()}
		</div>
	</main>

	<Dock />
</div>
