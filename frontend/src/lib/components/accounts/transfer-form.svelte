<script lang="ts">
	import { enhance } from '$app/forms';
	import { untrack } from 'svelte';
	import ArrowLeftRight from '@lucide/svelte/icons/arrow-left-right';
	import { CurrencyInput } from '$lib/components/currency-input';
	import { NativeDatePicker } from '$lib/components/native-date-picker';
	import { NativeSelect } from '$lib/components/native-select';
	import { Button } from '$lib/components/ui/button';
	import { Input } from '$lib/components/ui/input';
	import { Label } from '$lib/components/ui/label';
	import { m } from '$lib/paraglide/messages.js';
	import type { Account, Transfer } from './types';

	let {
		accounts,
		action,
		locale,
		initial,
		primary = false,
	}: {
		accounts: Account[];
		action: string;
		locale: string;
		initial?: Transfer;
		primary?: boolean;
	} = $props();

	const initialTransfer = untrack(() => initial);
	const fieldPrefix = initialTransfer ? `transfer-${initialTransfer.id}` : 'transfer-new';
	const accountItems = $derived(accounts.map((account) => ({ value: String(account.id), label: account.name })));
	let sourceAccountId = $state(initialTransfer ? String(initialTransfer.sourceAccountId) : '');
	let destinationAccountId = $state(initialTransfer ? String(initialTransfer.destinationAccountId) : '');
	let amount = $state<string | number>(initialTransfer?.amount ?? '');
	let transferDate = $state(initialTransfer?.transferDate ?? new Date().toISOString().slice(0, 10));
	let notes = $state(initialTransfer?.notes ?? '');
</script>

<form method="POST" {action} use:enhance class="grid gap-4 md:grid-cols-2">
	{#if initialTransfer}<input type="hidden" name="id" value={initialTransfer.id} />{/if}
	<div class="grid gap-2">
		<Label for={`${fieldPrefix}-source`}>{m.transfer_source()}</Label>
		<NativeSelect
			id={`${fieldPrefix}-source`}
			name="sourceAccountId"
			value={sourceAccountId}
			onValueChange={(value) => (sourceAccountId = value)}
			placeholder={m.transfer_select_account()}
			items={accountItems}
			required
		/>
	</div>
	<div class="grid gap-2">
		<Label for={`${fieldPrefix}-destination`}>{m.transfer_destination()}</Label>
		<NativeSelect
			id={`${fieldPrefix}-destination`}
			name="destinationAccountId"
			value={destinationAccountId}
			onValueChange={(value) => (destinationAccountId = value)}
			placeholder={m.transfer_select_account()}
			items={accountItems}
			required
		/>
	</div>
	<div class="grid gap-2">
		<Label for={`${fieldPrefix}-amount`}>{m.common_amount_mxn()}</Label>
		<CurrencyInput id={`${fieldPrefix}-amount`} name="amount" bind:value={amount} {locale} required />
	</div>
	<div class="grid gap-2">
		<Label for={`${fieldPrefix}-date`}>{m.common_date()}</Label>
		<NativeDatePicker
			id={`${fieldPrefix}-date`}
			name="transferDate"
			value={transferDate}
			onValueChange={(value) => (transferDate = value)}
			aria-label={m.common_date()}
		/>
	</div>
	<div class="grid gap-2 md:col-span-2">
		<Label for={`${fieldPrefix}-notes`}>{m.common_notes()} {m.common_optional()}</Label>
		<Input id={`${fieldPrefix}-notes`} name="notes" bind:value={notes} placeholder={m.transfer_notes_placeholder()} />
	</div>
	<div class="flex justify-end md:col-span-2">
		<Button type="submit" variant={primary ? 'default' : 'outline'}>
			<ArrowLeftRight data-icon="inline-start" />
			{initialTransfer ? m.transfer_save() : m.transfer_submit()}
		</Button>
	</div>
</form>
