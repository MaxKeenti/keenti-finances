<script lang="ts">
	import { Select as SelectPrimitive } from "bits-ui";

	type SingleProps = Omit<SelectPrimitive.RootProps, "value" | "onValueChange" | "type"> & {
		value?: string;
		onValueChange?: (value: string) => void;
		type?: "single";
	};

	let {
		open = $bindable(false),
		value = $bindable<string>(),
		...restProps
	}: SingleProps = $props();
</script>

<SelectPrimitive.Root type="single" bind:open bind:value={value as never} {...restProps} />
