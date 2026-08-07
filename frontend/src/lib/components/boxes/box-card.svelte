<script lang="ts">
	import History from '@lucide/svelte/icons/history';
	import type { Snippet } from 'svelte';
	import * as Card from '$lib/components/ui/card';
	import { Badge } from '$lib/components/ui/badge';
	import { Button } from '$lib/components/ui/button';
	import { m } from '$lib/paraglide/messages.js';
	import type { BoxDto } from '$lib/types/boxes';

	let {
		box,
		formattedBalance,
		archived = false,
		actions,
	}: {
		box: BoxDto;
		formattedBalance: string;
		archived?: boolean;
		actions?: Snippet;
	} = $props();
</script>

<Card.Root
	class="relative bg-gradient-to-br from-[oklch(0.97_0.025_var(--box-hue))] to-card dark:from-[oklch(0.27_0.035_var(--box-hue))]"
	style={`--box-hue: ${box.hue}`}
>
	<Card.Header>
		<div class="flex min-w-0 items-start gap-3">
			<div
				class="flex size-11 shrink-0 items-center justify-center rounded-xl bg-[oklch(0.88_0.12_var(--box-hue))] text-xl text-[oklch(0.32_0.08_var(--box-hue))] shadow-sm ring-1 ring-black/5"
				aria-hidden="true"
			>
				{box.icon || '□'}
			</div>
			<div class="min-w-0 flex-1">
				<Card.Title class="truncate">{box.name}</Card.Title>
				<Card.Description class="line-clamp-2 min-h-8">{box.description || m.boxes_no_description()}</Card.Description>
			</div>
		</div>
	</Card.Header>
	<Card.Content class="space-y-4">
		<div>
			<p class="text-xs text-muted-foreground">{m.boxes_balance()}</p>
			<p class="text-2xl font-semibold tabular-nums">{formattedBalance}</p>
		</div>
		<Button href={`/boxes/${box.id}`} variant="outline" class="w-full">
			<History data-icon="inline-start" />
			{m.boxes_view_history()}
		</Button>
	</Card.Content>
	<Card.Footer class="flex flex-wrap items-center justify-between gap-2 bg-background/45 px-4 py-3">
		{#if archived}<Badge variant="secondary">{m.boxes_archived()}</Badge>{/if}
		{@render actions?.()}
	</Card.Footer>
</Card.Root>
