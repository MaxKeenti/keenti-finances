<script lang="ts">
	import * as Select from '$lib/components/ui/select';
	import * as NativeSelectPrimitive from '$lib/components/ui/native-select';
	import { useIsMobile } from '$lib/use-mobile.svelte';
	import { cn } from '$lib/utils';

	type Item = { value: string; label: string };

	let {
		name,
		value = '',
		onValueChange,
		placeholder = 'Select…',
		items,
		class: className,
		...rest
	}: {
		name: string;
		value?: string;
		onValueChange: (v: string) => void;
		placeholder?: string;
		items: Item[];
		class?: string;
		[key: string]: unknown;
	} = $props();

	const isMobile = useIsMobile();
</script>

{#if isMobile.current}
	<NativeSelectPrimitive.Root
		{name}
		value={value}
		class={cn('w-full', className)}
		onchange={(e) => onValueChange((e.target as HTMLSelectElement).value)}
		{...rest}
	>
		<NativeSelectPrimitive.Option value="" disabled>{placeholder}</NativeSelectPrimitive.Option>
		{#each items as item (item.value)}
			<NativeSelectPrimitive.Option value={item.value}>{item.label}</NativeSelectPrimitive.Option>
		{/each}
	</NativeSelectPrimitive.Root>
{:else}
	<Select.Root {name} {value} {onValueChange} {items}>
		<Select.Trigger class={className} {...rest}>
			<Select.Value {placeholder} />
		</Select.Trigger>
		<Select.Content>
			{#each items as item (item.value)}
				<Select.Item value={item.value} label={item.label}>{item.label}</Select.Item>
			{/each}
		</Select.Content>
	</Select.Root>
{/if}
