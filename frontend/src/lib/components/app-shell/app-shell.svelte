<script lang="ts">
	import Sidebar from './sidebar.svelte';
	import BottomNav from './bottom-nav.svelte';
	import { Toaster } from '$lib/components/ui/sonner';

	const { children } = $props();
</script>

<div class="min-h-screen bg-background">
	<Sidebar />
	<BottomNav />
	<Toaster />

	<!-- Main content: offset for sidebar on sm+, bottom-nav padding on mobile -->
	<main class="sm:ml-60 pb-16 sm:pb-0">
		<div class="p-4 sm:p-6">
			{@render children()}
		</div>
	</main>
</div>
