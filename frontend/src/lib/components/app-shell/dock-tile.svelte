<script lang="ts">
	import type { Snippet } from 'svelte';

	/**
	 * One magnifiable dock icon. Renders as a link when given `href`, otherwise
	 * as a button, so the areas and the More trigger share one appearance.
	 */
	let {
		label,
		href,
		active = false,
		running = false,
		onclick,
		children,
	}: {
		/** Accessible name, also shown above the tile on hover and keyboard focus. */
		label: string;
		/** Destination for a navigation tile. */
		href?: string;
		/** The current page belongs to this tile's area. */
		active?: boolean;
		/** Shows the running dot: the area was opened this session. */
		running?: boolean;
		/** Action for a non-navigating tile, such as opening More. */
		onclick?: () => void;
		/** The tile's icon. */
		children: Snippet;
	} = $props();

	// Width, not transform, carries the magnification so neighbours displace.
	const TILE =
		'group relative flex w-[calc(var(--scale,1)*44px)] shrink-0 flex-col items-center justify-end outline-none transition-[width] duration-150 ease-out will-change-[width] md:w-[calc(var(--scale,1)*52px)]';
</script>

{#snippet body()}
	<!-- macOS-style name label above the tile. The tile's accessible name is
	     its aria-label, so this copy is hidden from assistive technology. -->
	<span
		aria-hidden="true"
		class="pointer-events-none absolute bottom-full left-1/2 mb-2.5 -translate-x-1/2 scale-90 whitespace-nowrap rounded-lg border border-border/50 bg-popover/90 px-2.5 py-1 text-xs font-medium text-popover-foreground opacity-0 shadow-lg transition-all duration-150 group-hover:scale-100 group-hover:opacity-100 group-focus-visible:scale-100 group-focus-visible:opacity-100"
	>
		{label}
		<span
			class="absolute left-1/2 top-full -mt-1 size-2 -translate-x-1/2 rotate-45 rounded-xs border-b border-r border-border/50 bg-popover/90"
		></span>
	</span>
	<div
		class="flex aspect-square w-full items-center justify-center rounded-[22%] border transition-shadow group-hover:shadow-md group-active:brightness-90 group-focus-visible:ring-2 group-focus-visible:ring-ring
			{active
			? 'border-primary/50 bg-linear-to-b from-primary to-primary/85 text-primary-foreground shadow-md'
			: 'border-border/50 bg-linear-to-b from-card to-muted text-muted-foreground shadow-sm group-hover:text-foreground'}"
	>
		{@render children()}
	</div>
	{#if running}
		<!-- Running dot, neutral like macOS -->
		<span class="absolute -bottom-1.25 left-1/2 size-1 -translate-x-1/2 rounded-full bg-foreground/60"></span>
	{/if}
{/snippet}

{#if href}
	<a data-dock-icon {href} aria-label={label} aria-current={active ? 'page' : undefined} class={TILE}>
		{@render body()}
	</a>
{:else}
	<button data-dock-icon type="button" {onclick} aria-label={label} class={TILE}>
		{@render body()}
	</button>
{/if}
