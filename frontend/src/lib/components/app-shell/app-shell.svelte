<script lang="ts">
	import Dock from './dock.svelte';
	import { Toaster } from '$lib/components/ui/sonner';

	const { children } = $props();
</script>

<div class="min-h-screen bg-background">
	<Dock />
	<Toaster />

	<main class="pb-20">
		<div class="p-4 sm:p-6">
			{@render children()}
		</div>
	</main>
</div>
