<script lang="ts">
	import { Select as SelectPrimitive } from "bits-ui";

	let { placeholder, ...restProps }: SelectPrimitive.ValueProps = $props();
</script>

<SelectPrimitive.Value {placeholder} {...restProps} />
