<script lang="ts">
	import * as Select from '$lib/components/ui/select';
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

	let isMobile = $state(false);

	$effect(() => {
		const mq = window.matchMedia('(hover: none) and (pointer: coarse)');
		isMobile = mq.matches;
		const handler = (e: MediaQueryListEvent) => {
			isMobile = e.matches;
		};
		mq.addEventListener('change', handler);
		return () => mq.removeEventListener('change', handler);
	});
</script>

{#if isMobile}
	<select
		{name}
		class={cn(
			'flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50',
			className,
		)}
		onchange={(e) => onValueChange((e.target as HTMLSelectElement).value)}
		{...rest}
	>
		<option value="" disabled selected={!value}>{placeholder}</option>
		{#each items as item (item.value)}
			<option value={item.value} selected={item.value === value}>{item.label}</option>
		{/each}
	</select>
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
