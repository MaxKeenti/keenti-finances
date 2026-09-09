<script lang="ts">
	import { Button } from '$lib/components/ui/button';
	import { invalidateAll } from '$app/navigation';
	import { CircleAlert, RefreshCw } from '@lucide/svelte';
	import { m } from '$lib/paraglide/messages.js';

	// Retry re-runs the page's loaders. The section stays mounted while that
	// happens, so `retried` survives and a second failure can say so instead of
	// looking like nothing happened.
	let {
		title,
		description = m.section_unavailable_description(),
		compact = false,
	}: { title: string; description?: string; compact?: boolean } = $props();

	let pending = $state(false);
	let retried = $state(false);
	let retryFailed = $state(false);

	async function retry() {
		pending = true;
		retryFailed = false;
		try {
			await invalidateAll();
			retried = true;
		} catch {
			retryFailed = true;
		} finally {
			pending = false;
		}
	}
</script>

<div
	class="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-md border border-dashed px-3 py-2 text-sm {compact
		? ''
		: 'py-3'}"
	role="status"
>
	<CircleAlert class="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
	<div class="min-w-0 flex-1 space-y-0.5">
		<p class="font-medium">{title}</p>
		<p class="text-xs text-muted-foreground">{description}</p>
		{#if retryFailed || retried}
			<p class="text-xs text-muted-foreground">{m.section_retry_failed()}</p>
		{/if}
	</div>
	<Button type="button" variant="outline" size="sm" class="h-7 shrink-0 px-3 text-xs" disabled={pending} onclick={retry}>
		<RefreshCw class="size-3.5 {pending ? 'animate-spin' : ''}" aria-hidden="true" />
		{pending ? m.section_retrying() : m.section_retry()}
	</Button>
</div>
