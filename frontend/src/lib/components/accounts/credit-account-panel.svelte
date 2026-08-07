<script lang="ts">
	import { enhance } from '$app/forms';
	import { untrack } from 'svelte';
	import ChevronsUpDown from '@lucide/svelte/icons/chevrons-up-down';
	import { CurrencyInput } from '$lib/components/currency-input';
	import { NativeDatePicker } from '$lib/components/native-date-picker';
	import * as Alert from '$lib/components/ui/alert';
	import * as Card from '$lib/components/ui/card';
	import * as Collapsible from '$lib/components/ui/collapsible';
	import { Button, buttonVariants } from '$lib/components/ui/button';
	import { Input } from '$lib/components/ui/input';
	import { Label } from '$lib/components/ui/label';
	import { mxnFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
	import type { Account, CreditDetail } from './types';

	let { account, detail, locale }: { account: Account; detail?: CreditDetail; locale: string } = $props();
	const fmt = $derived(mxnFormatter(locale));
	const initialDetail = untrack(() => detail);
	let creditLimit = $state<string | number>(initialDetail?.settings?.creditLimit ?? '');
	let periodStart = $state('');
	let periodEnd = $state('');
	let dueDate = $state('');
	let officialBalance = $state<string | number>('');
	let officialMinimumPayment = $state<string | number>('');
	let officialAvoidInterest = $state<string | number>('');
	let reconfirmDueDates = $state<Record<number, string>>({});
</script>

<Card.Root>
	<Card.Header>
		<Card.Title>{m.credit_payment_title({ name: account.name })}</Card.Title>
		<Card.Description>{m.credit_payment_description()}</Card.Description>
	</Card.Header>
	<Card.Content class="space-y-6">
		{#if !account.archived}
		<Collapsible.Root>
			<Collapsible.Trigger class={buttonVariants({ variant: 'outline', class: 'w-full justify-between' })}>
				{m.account_credit_settings()}<ChevronsUpDown />
			</Collapsible.Trigger>
			<Collapsible.Content class="pt-4">
				<form method="POST" action="?/saveCreditSettings" use:enhance class="grid gap-4 rounded-lg bg-muted/30 p-4 md:grid-cols-2">
					<input type="hidden" name="accountId" value={account.id} />
					<div class="grid gap-2"><Label for={`credit-limit-${account.id}`}>{m.account_credit_limit()}</Label><CurrencyInput id={`credit-limit-${account.id}`} name="creditLimit" bind:value={creditLimit} {locale} required /></div>
					<div class="grid gap-2"><Label for={`closing-day-${account.id}`}>{m.account_statement_closing_day()}</Label><Input id={`closing-day-${account.id}`} name="statementClosingDay" type="number" min="1" max="31" required value={detail?.settings?.statementClosingDay ?? ''} /></div>
					<div class="grid gap-2"><Label for={`payment-day-${account.id}`}>{m.account_payment_due_day()}</Label><Input id={`payment-day-${account.id}`} name="paymentDueDay" type="number" min="1" max="31" required value={detail?.settings?.paymentDueDay ?? ''} /></div>
					<div class="flex items-end justify-end"><Button type="submit" variant="outline">{m.credit_save_settings()}</Button></div>
				</form>
			</Collapsible.Content>
		</Collapsible.Root>

		<section class="space-y-4" aria-labelledby={`confirm-statement-${account.id}`}>
			<div>
				<h3 id={`confirm-statement-${account.id}`} class="font-medium">{m.credit_confirm_title()}</h3>
				<p class="text-sm text-muted-foreground">{m.credit_confirm_description()}</p>
			</div>
			<form method="POST" action="?/confirmCreditStatement" use:enhance class="space-y-4">
				<input type="hidden" name="accountId" value={account.id} />
				<div class="rounded-lg bg-muted/30 p-4">
					<h4 class="mb-3 text-sm font-medium">{m.common_date()}</h4>
					<div class="grid gap-4 md:grid-cols-3">
						<div class="grid gap-2"><Label for={`period-start-${account.id}`}>{m.account_period_start()}</Label><NativeDatePicker id={`period-start-${account.id}`} name="periodStart" value={periodStart} onValueChange={(value) => (periodStart = value)} /></div>
						<div class="grid gap-2"><Label for={`period-end-${account.id}`}>{m.account_period_end()}</Label><NativeDatePicker id={`period-end-${account.id}`} name="periodEnd" value={periodEnd} onValueChange={(value) => (periodEnd = value)} /></div>
						<div class="grid gap-2"><Label for={`statement-due-${account.id}`}>{m.account_due_date()}</Label><NativeDatePicker id={`statement-due-${account.id}`} name="dueDate" value={dueDate} onValueChange={(value) => (dueDate = value)} /></div>
					</div>
				</div>
				<div class="rounded-lg bg-muted/30 p-4">
					<h4 class="mb-3 text-sm font-medium">{m.account_statement_figures()}</h4>
					<div class="grid gap-4 md:grid-cols-3">
						<div class="grid gap-2"><Label for={`official-balance-${account.id}`}>{m.account_official_balance()}</Label><CurrencyInput id={`official-balance-${account.id}`} name="officialBalance" bind:value={officialBalance} {locale} required /></div>
						<div class="grid gap-2"><Label for={`minimum-payment-${account.id}`}>{m.account_minimum_payment()}</Label><CurrencyInput id={`minimum-payment-${account.id}`} name="officialMinimumPayment" bind:value={officialMinimumPayment} {locale} required /></div>
						<div class="grid gap-2"><Label for={`avoid-interest-${account.id}`}>{m.account_avoid_interest()}</Label><CurrencyInput id={`avoid-interest-${account.id}`} name="officialAvoidInterest" bind:value={officialAvoidInterest} {locale} required /></div>
						<div class="grid gap-2 md:col-span-3"><Label for={`official-note-${account.id}`}>{m.common_notes()} {m.common_optional()}</Label><Input id={`official-note-${account.id}`} name="officialNote" /></div>
					</div>
				</div>
				<div class="flex justify-end"><Button type="submit" variant="outline">{m.credit_confirm()}</Button></div>
			</form>
				</section>
			{:else}
				<p class="border-t pt-6 text-sm text-muted-foreground">{m.account_no_statements()}</p>
			{/if}

		{#if detail?.statements?.length}
			<section class="space-y-3 border-t pt-6" aria-labelledby={`confirmed-statements-${account.id}`}>
				<h3 id={`confirmed-statements-${account.id}`} class="font-medium">{m.credit_confirmed_title()}</h3>
				{#each detail.statements as statement}
					<div class="space-y-3 rounded-lg border bg-background p-4">
						<div class="flex flex-wrap items-center justify-between gap-2 text-sm">
							<span>{m.account_statement_due_remaining({ date: statement.dueDate, amount: fmt.format(statement.outstandingBalance) })}</span>
							<span class="text-muted-foreground">{fmt.format(statement.officialAvoidInterest)} · {m.account_avoid_interest()}</span>
						</div>
						{#if statement.reconciliationMismatch}<Alert.Root><Alert.Description>{m.credit_mismatch({ amount: fmt.format(Math.abs(statement.mismatchAmount)) })}</Alert.Description></Alert.Root>{/if}
						{#if !account.archived}<Collapsible.Root>
							<Collapsible.Trigger class={buttonVariants({ variant: 'ghost', size: 'sm' })}>{m.credit_reconfirm()}<ChevronsUpDown /></Collapsible.Trigger>
							<Collapsible.Content class="pt-3">
								<form method="POST" action="?/reconfirmCreditStatement" use:enhance class="grid gap-4 rounded-lg bg-muted/30 p-4 md:grid-cols-2">
									<input type="hidden" name="accountId" value={account.id} /><input type="hidden" name="statementId" value={statement.id} /><input type="hidden" name="periodStart" value={statement.periodStart} /><input type="hidden" name="periodEnd" value={statement.periodEnd} />
									<div class="grid gap-2"><Label>{m.account_due_date()}</Label><NativeDatePicker name="dueDate" value={reconfirmDueDates[statement.id] ?? statement.dueDate} onValueChange={(value) => (reconfirmDueDates = { ...reconfirmDueDates, [statement.id]: value })} /></div>
									<div class="grid gap-2"><Label>{m.account_official_balance()}</Label><CurrencyInput name="officialBalance" value={statement.officialBalance} {locale} /></div>
									<div class="grid gap-2"><Label>{m.account_minimum_payment()}</Label><CurrencyInput name="officialMinimumPayment" value={statement.officialMinimumPayment} {locale} /></div>
									<div class="grid gap-2"><Label>{m.account_avoid_interest()}</Label><CurrencyInput name="officialAvoidInterest" value={statement.officialAvoidInterest} {locale} /></div>
									<div class="grid gap-2 md:col-span-2"><Label>{m.common_notes()} {m.common_optional()}</Label><Input name="officialNote" value={statement.officialNote ?? ''} /></div>
									<div class="flex justify-end md:col-span-2"><Button type="submit" variant="outline">{m.credit_reconfirm_submit()}</Button></div>
								</form>
							</Collapsible.Content>
						</Collapsible.Root>{/if}
					</div>
				{/each}
			</section>
		{/if}
	</Card.Content>
</Card.Root>
