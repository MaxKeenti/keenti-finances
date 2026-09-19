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
	import { dateInTimeZone } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
	import type { Account, Transfer } from './types';

	let {
		accounts,
		action,
		locale,
		initial,
		prefill,
		primary = false,
		timeZone,
		onSuccess,
	}: {
		accounts: Account[];
		action: string;
		locale: string;
		initial?: Transfer;
		/**
		 * Starting values for a contextual card payment.
		 *
		 * A prefill is a suggestion, not a record: every field stays editable,
		 * and closing the dialog without submitting writes nothing.
		 */
		prefill?: { destinationAccountId?: number | null; amount?: number | null };
		primary?: boolean;
		/** IANA zone the User's "today" is resolved in. */
		timeZone: string;
		/** Called after a successful submit — lets a hosting dialog close itself. */
		onSuccess?: () => void;
	} = $props();

	const initialTransfer = untrack(() => initial);
	const fieldPrefix = initialTransfer ? `transfer-${initialTransfer.id}` : 'transfer-new';
	const accountItems = $derived(accounts.map((account) => ({ value: String(account.id), label: account.name })));
	const initialPrefill = untrack(() => prefill);
	let sourceAccountId = $state(initialTransfer ? String(initialTransfer.sourceAccountId) : '');
	let destinationAccountId = $state(
		initialTransfer
			? String(initialTransfer.destinationAccountId)
			: initialPrefill?.destinationAccountId
				? String(initialPrefill.destinationAccountId)
				: '',
	);
	// The source is deliberately left empty: only the User knows which account
	// the money actually came from, and guessing one would be a claim about a
	// movement that has not happened.
	let amount = $state<string | number>(initialTransfer?.amount ?? initialPrefill?.amount ?? '');
	// `toISOString()` yields the UTC date. For a User at UTC-6 that is already
	// tomorrow after 18:00 local, and the backend rejects future-dated
	// Transfers — so every evening Transfer failed with a generic error.
	let transferDate = $state(initialTransfer?.transferDate ?? untrack(() => dateInTimeZone(timeZone)));
	let notes = $state(initialTransfer?.notes ?? '');
</script>

<form
	method="POST"
	{action}
	use:enhance={() => async ({ result, update }) => {
		if (result.type === 'success' && !initialTransfer) {
			// Only the create form resets; the inline edit forms keep their values.
			sourceAccountId = '';
			destinationAccountId = '';
			amount = '';
			notes = '';
			onSuccess?.();
		}
		await update();
	}}
	class="grid gap-4 md:grid-cols-2"
>
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
