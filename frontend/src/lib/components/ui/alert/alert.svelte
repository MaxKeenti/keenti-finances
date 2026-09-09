<script lang="ts" module>
	import { type VariantProps, tv } from "tailwind-variants";

	export const alertVariants = tv({
		// The action participates in the grid rather than being positioned over
		// the text: on narrow screens it takes its own row underneath, and from
		// `sm` up it gets a real trailing column whose width the title and
		// description columns account for. A fixed right padding could not,
		// because localized action labels vary in width.
		base: "grid gap-0.5 rounded-lg border px-2.5 py-2 text-left text-sm has-[>svg]:grid-cols-[auto_1fr] has-[>svg]:gap-x-2 has-data-[slot=alert-action]:sm:grid-cols-[minmax(0,1fr)_auto] has-data-[slot=alert-action]:sm:gap-x-3 has-[>svg]:has-data-[slot=alert-action]:sm:grid-cols-[auto_minmax(0,1fr)_auto] *:[svg]:row-span-2 *:[svg]:translate-y-0.5 *:[svg]:text-current *:[svg:not([class*='size-'])]:size-4 group/alert relative w-full",
		variants: {
			variant: {
				default: "bg-card text-card-foreground",
				destructive: "text-destructive bg-card *:data-[slot=alert-description]:text-destructive/90 *:[svg]:text-current",
			},
		},
		defaultVariants: {
			variant: "default",
		},
	});

	export type AlertVariant = VariantProps<typeof alertVariants>["variant"];
</script>

<script lang="ts">
	import type { HTMLAttributes } from "svelte/elements";
	import { cn, type WithElementRef } from "$lib/utils.js";

	let {
		ref = $bindable(null),
		class: className,
		variant = "default",
		children,
		...restProps
	}: WithElementRef<HTMLAttributes<HTMLDivElement>> & {
		variant?: AlertVariant;
	} = $props();
</script>

<div
	bind:this={ref}
	data-slot="alert"
	role="alert"
	class={cn(alertVariants({ variant }), className)}
	{...restProps}
>
	{@render children?.()}
</div>
