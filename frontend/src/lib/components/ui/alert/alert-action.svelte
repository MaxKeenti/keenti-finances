<script lang="ts">
	import type { HTMLAttributes } from "svelte/elements";
	import { cn, type WithElementRef } from "$lib/utils.js";

	let {
		ref = $bindable(null),
		class: className,
		children,
		...restProps
	}: WithElementRef<HTMLAttributes<HTMLDivElement>> = $props();
</script>

<div
	bind:this={ref}
	data-slot="alert-action"
	class={cn(
		// The `sm` Button inside an alert renders at 28px, under the
		// comfortable minimum. Raise the floor here rather than resizing
		// every small button in the app.
		"[&_a]:min-h-8 [&_button]:min-h-8",
		// Below `sm`, own row under the text, spanning any icon column.
		// From `sm` up, the alert's last column — `col-start-[-2]` resolves to
		// the final column whether or not an icon added one in front.
		"col-span-full mt-2 flex flex-wrap items-center gap-2",
		"sm:col-span-1 sm:col-start-[-2] sm:row-start-1 sm:mt-0 sm:justify-self-end",
		className,
	)}
	{...restProps}
>
	{@render children?.()}
</div>
