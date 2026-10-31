<script lang="ts">
	import { Input } from '$lib/components/ui/input';
	import { moneyFormatter } from '$lib/formatting';

	let {
		id,
		name,
		value = $bindable(''),
		locale = 'es',
		currency,
		placeholder,
		required = false,
		disabled = false,
		ariaLabel,
		class: className,
	}: {
		id?: string;
		name?: string;
		value?: string | number;
		locale?: string;
		/** The amount's currency: it decides the symbol shown around the value. */
		currency: string;
		placeholder?: string;
		required?: boolean;
		disabled?: boolean;
		ariaLabel?: string;
		class?: string;
	} = $props();

	let focused = $state(false);
	let displayValue = $state('');
	const formatter = $derived(moneyFormatter(locale, currency));
	const effectivePlaceholder = $derived(placeholder ?? formatter.format(0));
	const canonicalValue = $derived(value === '' || value === null || value === undefined ? '' : String(value));

	$effect(() => {
		if (focused) return;
		const numeric = Number(canonicalValue);
		displayValue = canonicalValue !== '' && Number.isFinite(numeric) ? formatter.format(numeric) : '';
	});

	function editValue() {
		focused = true;
		displayValue = canonicalValue;
	}

	function updateValue(event: Event) {
		const raw = (event.currentTarget as HTMLInputElement).value;
		displayValue = raw;
		const normalized = raw.replace(/[^0-9.-]/g, '');
		value = normalized;
	}

	function formatValue() {
		focused = false;
		const numeric = Number(canonicalValue);
		if (canonicalValue === '' || !Number.isFinite(numeric)) {
			value = '';
			displayValue = '';
			return;
		}
		value = Math.round(numeric * 100) / 100;
		displayValue = formatter.format(Number(value));
	}
</script>

<Input
	{id}
	type="text"
	inputmode="decimal"
	value={displayValue}
	placeholder={effectivePlaceholder}
	{required}
	{disabled}
	aria-label={ariaLabel}
	class={className}
	onfocus={editValue}
	oninput={updateValue}
	onblur={formatValue}
/>
{#if name}<input type="hidden" {name} value={canonicalValue} />{/if}
