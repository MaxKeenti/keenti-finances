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

<!--
	The scrolling region of a tall Dialog. Wrapping the body in this keeps
	Dialog.Header and Dialog.Footer pinned: the title, the description and the
	action buttons stay on screen while only the fields scroll. Dialogs short
	enough never to overflow can skip it — Dialog.Content still scrolls as a
	whole in that case.

	`min-h-0` is what makes it work: a flex child defaults to min-height:auto,
	which refuses to shrink below its content and pushes the footer off instead
	of scrolling.
-->
<div
	bind:this={ref}
	data-slot="dialog-body"
	class={cn("-mx-4 min-h-0 flex-1 overflow-y-auto px-4 flex flex-col gap-4", className)}
	{...restProps}
>
	{@render children?.()}
</div>
