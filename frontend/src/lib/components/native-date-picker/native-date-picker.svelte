<script lang="ts">
	import * as Popover from '$lib/components/ui/popover';
	import { Calendar } from '$lib/components/ui/calendar';
	import { parseDate } from '@internationalized/date';
	import CalendarIcon from '@lucide/svelte/icons/calendar';
	import { cn } from '$lib/utils';

	let {
		name,
		value = '',
		onValueChange,
		class: className,
		...rest
	}: {
		name: string;
		value?: string;
		onValueChange: (v: string) => void;
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

	const calDate = $derived.by(() => {
		try {
			return value ? parseDate(value) : undefined;
		} catch {
			return undefined;
		}
	});
</script>

{#if isMobile}
	<input
		type="date"
		{name}
		{value}
		class={cn(
			'flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50',
			className,
		)}
		onchange={(e) => onValueChange((e.target as HTMLInputElement).value)}
		{...rest}
	/>
{:else}
	<Popover.Root>
		<Popover.Trigger
			class={cn(
				'flex h-9 w-full items-center gap-2 rounded-md border border-input bg-transparent px-3 text-sm shadow-sm transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50',
				!value && 'text-muted-foreground',
				className,
			)}
			{...rest}
		>
			<CalendarIcon class="size-4 shrink-0 text-muted-foreground" />
			{value || 'Pick a date'}
		</Popover.Trigger>
		<Popover.Content class="w-auto p-0" align="start">
			<Calendar
				type="single"
				value={calDate}
				onValueChange={(v) => {
					if (v) onValueChange(v.toString());
				}}
			/>
		</Popover.Content>
	</Popover.Root>
	<input type="hidden" {name} {value} />
{/if}
