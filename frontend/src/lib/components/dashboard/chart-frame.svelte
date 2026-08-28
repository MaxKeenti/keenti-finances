<script lang="ts">
	/**
	 * Shared plot frame for the dashboard charts. Owns the two things both
	 * charts previously got wrong on their own: a fixed 560px width that left
	 * half of every card empty, and a hover layer that did not exist.
	 *
	 * Children render into the plot area via the `plot` snippet, which receives
	 * the measured inner box. The parent reports hover through `activeIndex` so
	 * the tooltip content stays with the chart that knows what a column means.
	 */
	import type { Snippet } from 'svelte';

	type Padding = { top: number; right: number; bottom: number; left: number };

	let {
		height,
		padding,
		columns,
		label,
		activeIndex = $bindable(null),
		plot,
		tooltip,
	}: {
		height: number;
		padding: Padding;
		/** Number of hover bands across the plot — one per month. */
		columns: number;
		label: string;
		activeIndex?: number | null;
		plot: Snippet<[{ innerWidth: number; innerHeight: number }]>;
		tooltip?: Snippet<[{ index: number }]>;
	} = $props();

	let width = $state(0);

	const innerWidth = $derived(Math.max(width - padding.left - padding.right, 0));
	const innerHeight = $derived(height - padding.top - padding.bottom);
	const bandWidth = $derived(columns > 0 ? innerWidth / columns : 0);

	// One pointer handler on the <svg> rather than a grid of invisible hit
	// rects: same column resolution, no interactive elements to give a role to.
	function trackPointer(event: PointerEvent) {
		if (bandWidth <= 0) return;
		const x = event.offsetX - padding.left;
		if (x < 0 || x > innerWidth) {
			activeIndex = null;
			return;
		}
		activeIndex = Math.min(Math.floor(x / bandWidth), columns - 1);
	}

	// Keep the tooltip inside the card instead of letting it clip at the edges.
	const tooltipLeft = $derived(
		activeIndex === null
			? 0
			: Math.min(Math.max(padding.left + bandWidth * (activeIndex + 0.5), 80), Math.max(width - 80, 80)),
	);
</script>

<div class="relative" bind:clientWidth={width}>
	{#if width > 0}
		<svg
			{width}
			{height}
			role="img"
			aria-label={label}
			onpointermove={trackPointer}
			onpointerleave={() => (activeIndex = null)}
		>
			<g transform="translate({padding.left},{padding.top})">
				{@render plot({ innerWidth, innerHeight })}
			</g>
		</svg>

		{#if tooltip && activeIndex !== null}
			<div
				class="pointer-events-none absolute -translate-x-1/2 rounded-lg border bg-popover/95 px-3 py-2 text-xs shadow-lg backdrop-blur-md"
				style="left: {tooltipLeft}px; top: {padding.top}px;"
				role="status"
			>
				{@render tooltip({ index: activeIndex })}
			</div>
		{/if}
	{/if}
</div>
