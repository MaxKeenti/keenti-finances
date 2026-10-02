<script lang="ts">
	import { X } from '@lucide/svelte';
	import { Button } from '$lib/components/ui/button';
	import { m } from '$lib/paraglide/messages.js';
	import { cn } from '$lib/utils';
	import { DOCK_SURFACE } from './dock-surface';
	import type { DockActionBar } from './dock-action.svelte';

	let { bar }: { bar: DockActionBar } = $props();
</script>

<!-- Occupies the same fixed slot as the dock so the swap feels in-place. -->
<div
	class="fixed inset-x-3 bottom-[calc(env(safe-area-inset-bottom)+0.75rem)] z-40 flex justify-center sm:inset-x-auto sm:left-1/2 sm:-translate-x-1/2"
>
	<div
		role="toolbar"
		aria-label={m.common_selected_count({ count: bar.count })}
		class={cn(
			DOCK_SURFACE,
			'flex w-full max-w-md flex-wrap items-center gap-2 rounded-3xl px-3 py-2 sm:w-auto',
		)}
	>
		<span class="whitespace-nowrap pl-1 text-sm font-medium text-foreground">
			{m.common_selected_count({ count: bar.count })}
		</span>

		<div class="ml-auto flex max-w-full flex-wrap items-center justify-end gap-2">
			{#each bar.actions as action (action.label)}
				<Button
					variant={action.variant ?? 'default'}
					size="sm"
					disabled={action.disabled}
					onclick={action.onClick}
					class="h-auto min-h-8 max-w-full whitespace-normal text-center"
				>
					{#if action.icon}
						<action.icon class="size-4" />
					{/if}
					{action.label}
				</Button>
			{/each}

			<Button
				variant="ghost"
				size="icon-sm"
				onclick={bar.onCancel}
				aria-label={m.common_clear()}
			>
				<X />
			</Button>
		</div>
	</div>
</div>
