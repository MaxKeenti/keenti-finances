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
		"absolute top-2 right-2 [&_a]:min-h-8 [&_button]:min-h-8",
		className,
	)}
	{...restProps}
>
	{@render children?.()}
</div>
