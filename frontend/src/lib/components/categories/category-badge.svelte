<script lang="ts">
	import { cn } from '$lib/utils';

	interface Props {
		hue: number | null;
		name: string;
		direction?: string;
		forceTheme?: 'light' | 'dark';
	}

	let { hue, name, direction: _direction, forceTheme }: Props = $props();

	const colorClass = $derived(
		forceTheme === 'light'
			? 'bg-[oklch(0.92_0.05_var(--badge-hue))] text-[oklch(0.2_0_0)]'
			: forceTheme === 'dark'
				? 'bg-[oklch(0.3_0.08_var(--badge-hue))] text-[oklch(0.9_0_0)]'
				: 'bg-[oklch(0.92_0.05_var(--badge-hue))] text-[oklch(0.2_0_0)] dark:bg-[oklch(0.3_0.08_var(--badge-hue))] dark:text-[oklch(0.9_0_0)]',
	);
</script>

{#if hue !== null && hue !== undefined}
	<span
		class={cn('inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium', colorClass)}
		style:--badge-hue={String(hue)}
	>
		{name}
	</span>
{:else}
	<span class="inline-flex items-center rounded-full bg-muted px-2.5 py-0.5 text-xs font-medium text-muted-foreground">
		{name}
	</span>
{/if}
