<script lang="ts">
	import * as Popover from '$lib/components/ui/popover';
	import { Calendar } from '$lib/components/ui/calendar';
	import { Input } from '$lib/components/ui/input';
	import { parseDate } from '@internationalized/date';
	import CalendarIcon from '@lucide/svelte/icons/calendar';
	import { useIsMobile } from '$lib/use-mobile.svelte';
	import { cn } from '$lib/utils';
	import { formatDateOnly } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
	import { getLocale } from '$lib/paraglide/runtime';

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

	const isMobile = useIsMobile();

	// The trigger is the only place the chosen date is shown on desktop — the
	// mobile branch hands display off to the browser's own date input. Format it
	// the way the rest of the app formats dates; the hidden input below keeps
	// submitting the ISO value, so nothing downstream sees this.
	const label = $derived(value ? formatDateOnly(value, getLocale()) : m.common_pick_date());

	const calDate = $derived.by(() => {
		try {
			return value ? parseDate(value) : undefined;
		} catch {
			return undefined;
		}
	});
</script>

{#if isMobile.current}
	<Input
		type="date"
		{name}
		{value}
		class={cn('h-9', className)}
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
			{label}
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
