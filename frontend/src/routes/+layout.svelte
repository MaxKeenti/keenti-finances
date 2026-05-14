<script lang="ts">
	import './layout.css';
	import AppShell from '$lib/components/app-shell/app-shell.svelte';
	import type { LayoutData } from './$types';

	const { children, data }: { children: any; data: LayoutData } = $props();
</script>

{#if data.session}
	<AppShell>
		{@render children()}
	</AppShell>
{:else}
	{@render children()}
{/if}
