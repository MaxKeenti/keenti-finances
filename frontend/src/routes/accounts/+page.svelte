<script lang="ts">
	import { replaceState } from '$app/navigation';
	import { page } from '$app/state';
	import { enhance } from '$app/forms';
	import { untrack } from 'svelte';
	import ArrowLeftRight from '@lucide/svelte/icons/arrow-left-right';
	import Plus from '@lucide/svelte/icons/plus';
	import Trash2 from '@lucide/svelte/icons/trash-2';
	import { AccountCard, AccountSetupForm, TransferForm, type Account, type Transfer } from '$lib/components/accounts';
	import { ColorPicker } from '$lib/components/color-picker';
	import { CurrencyInput } from '$lib/components/currency-input';
	import { NativeSelect } from '$lib/components/native-select';
	import * as Accordion from '$lib/components/ui/accordion';
	import * as Alert from '$lib/components/ui/alert';
	import { Button } from '$lib/components/ui/button';
	import * as Card from '$lib/components/ui/card';
	import * as Dialog from '$lib/components/ui/dialog';
	import { Input } from '$lib/components/ui/input';
	import { Label } from '$lib/components/ui/label';
	import { SectionUnavailable } from '$lib/components/section-status';
	import { dateInTimeZone, formatDateOnly, moneyFormatter } from '$lib/formatting';
	import { currencyItems } from '$lib/currency-labels';
	import { groupByCurrency } from '$lib/currency-totals';
	import { creditPosition } from '$lib/balance-presentation';
	import { sectionValue } from '$lib/types/section';
	import { m } from '$lib/paraglide/messages.js';
	import type { PageData } from './$types';

	let { data }: { data: PageData } = $props();
	const accounts = $derived(data.accounts as Account[]);
	const archivedAccounts = $derived(data.archivedAccounts as Account[]);
	const transfers = $derived(data.transfers as Transfer[]);
	const locale = $derived(data.preferences.locale);
	const primaryCurrency = $derived(data.preferences.primaryCurrency);
	const money = (amount: number, currency: string) => moneyFormatter(locale, currency).format(amount);
	const today = $derived(dateInTimeZone(data.preferences.timeZone));
	const kindItems = $derived([
		{ value: 'DEBIT', label: m.account_kind_debit() },
		{ value: 'CHECKING', label: m.account_kind_checking() },
		{ value: 'SAVINGS', label: m.account_kind_savings() },
		{ value: 'CASH', label: m.account_kind_cash() },
		{ value: 'CREDIT', label: m.account_kind_credit() },
	]);

	// A Credit Financial Account is a liability: it must not sit in the same
	// visual run as the asset accounts, and it must not be summed with them.
	const ASSET_KINDS = ['CASH', 'DEBIT', 'CHECKING', 'SAVINGS'];
	const assetAccounts = $derived(accounts.filter((a) => ASSET_KINDS.includes(a.kind)));
	const creditAccounts = $derived(accounts.filter((a) => a.kind === 'CREDIT'));
	// One summary row per currency: pesos and euros are never added together
	// (ADR-0024). Within a currency, the signed Credit position — negative is
	// debt, positive is a credit in the User's favour — sits beside the assets
	// so held, owed and net visibly add up.
	const summaries = $derived(
		groupByCurrency(accounts, primaryCurrency).map(({ currency, items }) => {
			const heldTotal = items.filter((a) => ASSET_KINDS.includes(a.kind)).reduce((sum, a) => sum + a.balance, 0);
			const creditTotal = items.filter((a) => a.kind === 'CREDIT').reduce((sum, a) => sum + a.balance, 0);
			return { currency, heldTotal, creditTotal, netTotal: heldTotal + creditTotal };
		}),
	);

	// The authoritative tracking status. An unreadable one is unavailable, not
	// "not activated": offering the activation flow off a failed read would ask
	// the User to match a pre-activation Net Balance nobody computed.
	const status = $derived(sectionValue(data.status));
	const payCardAccount = $derived(
		data.payCard.accountId === null
			? null
			: (accounts.find((account) => account.id === data.payCard.accountId && account.kind === 'CREDIT') ?? null),
	);

	let transferOpen = $state(false);
	let consumedPaymentPrefill = $state('');
	// A contextual card payment opens this dialog prefilled. Opening a form is
	// not recording anything: dismissing it leaves the ledger untouched.
	$effect(() => {
		const key = `${data.payCard.accountId}:${data.payCard.amount}`;
		if (payCardAccount && consumedPaymentPrefill !== key) {
			consumedPaymentPrefill = key;
			transferOpen = true;
			const url = new URL(page.url);
			url.searchParams.delete('payCard');
			url.searchParams.delete('amount');
			replaceState(url, page.state);
		}
	});
	let addAccountOpen = $state(false);
	let addAccountError = $state('');
	let newAccountName = $state('');
	let newAccountKind = $state('DEBIT');
	let newAccountCurrency = $state(untrack(() => data.preferences.primaryCurrency));
	let newAccountHue = $state(220);
	let newOpeningBalance = $state<string | number>(0);

	function kindLabel(kind: string) {
		return kindItems.find((item) => item.value === kind)?.label ?? kind;
	}

	// Credit debt is shown as a magnitude under its own label, so the minus
	// sign is not repeated on a figure already labeled as owed.
	function accountBalance(account: Account) {
		return account.kind === 'CREDIT' && creditPosition(account.balance) === 'debt'
			? money(Math.abs(account.balance), account.currency)
			: money(account.balance, account.currency);
	}

	function openAddAccount() {
		addAccountError = '';
		newAccountName = '';
		newAccountKind = 'DEBIT';
		newAccountCurrency = primaryCurrency;
		newAccountHue = (220 + accounts.length * 137) % 360;
		newOpeningBalance = 0;
		addAccountOpen = true;
	}

	function enhanceCreateAccount({ formElement }: { formElement: HTMLFormElement }) {
		return async ({ result, update }: { result: { type: string; data?: { message?: string } }; update: () => Promise<void> }) => {
			if (result.type === 'success') {
				addAccountOpen = false;
				addAccountError = '';
				formElement.reset();
			} else if (result.type === 'failure') {
				addAccountError = result.data?.message ?? m.account_create_error();
			}
			await update();
		};
	}
</script>

<svelte:head><title>{m.accounts_title()} · Keenti</title></svelte:head>

<div class="space-y-8">
	<header class="flex flex-wrap items-start justify-between gap-4">
		<div class="max-w-2xl space-y-1">
			<h1 class="text-2xl font-semibold tracking-tight">{m.accounts_title()}</h1>
			<p class="text-sm text-muted-foreground">{m.accounts_description()}</p>
		</div>
		{#if status?.active}
			<div class="flex flex-wrap gap-2">
				<Button variant="outline" onclick={() => (transferOpen = true)}>
					<ArrowLeftRight />{m.transfer_title()}
				</Button>
				<Button onclick={openAddAccount}><Plus />{m.accounts_add()}</Button>
			</div>
		{/if}
	</header>

	{#if status === null}
		<SectionUnavailable title={m.section_tracking_unavailable()} description={m.balance_source_unknown()} />
	{:else if !status.active}
		{#if status.transactionNetBalance === null}
			<!-- Activation asks the User to declare opening balances that sum to
			     their pre-activation Net Balance. Without that figure the form
			     cannot state the target, and a zero would be a fabricated one. -->
			<SectionUnavailable title={m.section_balance_unavailable()} />
		{:else}
			<AccountSetupForm transactionNetBalance={status.transactionNetBalance} locale={data.preferences.locale} {today} {primaryCurrency} />
		{/if}
	{:else}
		<!-- The page's whole job is "where is my money", and it never answered
		     it: five cards, no total anywhere. -->
		<Card.Root>
			<Card.Content class="divide-y">
				{#each summaries as summary (summary.currency)}
					<div class="grid gap-4 py-4 first:pt-0 last:pb-0 sm:grid-cols-3">
						{#if summaries.length > 1}
							<p class="text-sm font-medium sm:col-span-3">{summary.currency}</p>
						{/if}
						<div>
							<p class="text-xs text-muted-foreground">{m.accounts_summary_assets()}</p>
							<p class="text-2xl font-semibold tabular-nums">{money(summary.heldTotal, summary.currency)}</p>
						</div>
						<div>
							<p class="text-xs text-muted-foreground">
								{summary.creditTotal > 0
									? m.credit_label_in_favor()
									: summary.creditTotal === 0
										? m.credit_label_settled()
										: m.credit_label_debt()}
							</p>
							<p class="text-2xl font-semibold tabular-nums {summary.creditTotal < 0 ? 'text-money-negative' : ''}">
								{money(Math.abs(summary.creditTotal), summary.currency)}
							</p>
						</div>
						<div class="sm:border-l sm:pl-4">
							<p class="text-xs text-muted-foreground">{m.accounts_summary_net()}</p>
							<p class="text-2xl font-semibold tabular-nums">{money(summary.netTotal, summary.currency)}</p>
						</div>
					</div>
				{/each}
			</Card.Content>
		</Card.Root>

		<section class="space-y-3" aria-label={m.accounts_group_assets()}>
			<h2 class="text-sm font-medium text-muted-foreground">{m.accounts_group_assets()}</h2>
			<div class="grid grid-cols-[repeat(auto-fill,minmax(min(100%,17rem),1fr))] gap-4">
				{#each assetAccounts as account (account.id)}
					<AccountCard {account} kindLabel={kindLabel(account.kind)} balanceLabel={accountBalance(account)} />
				{/each}
			</div>
		</section>

		{#if creditAccounts.length}
			<section class="space-y-3" aria-label={m.accounts_group_credit()}>
				<h2 class="text-sm font-medium text-muted-foreground">{m.accounts_group_credit()}</h2>
				<div class="grid grid-cols-[repeat(auto-fill,minmax(min(100%,17rem),1fr))] gap-4">
					{#each creditAccounts as account (account.id)}
						<AccountCard {account} kindLabel={kindLabel(account.kind)} balanceLabel={accountBalance(account)} />
					{/each}
				</div>
			</section>
		{/if}

		<Card.Root>
			<Card.Header><Card.Title>{m.transfer_history()}</Card.Title><Card.Description>{m.transfer_history_description()}</Card.Description></Card.Header>
			<Card.Content>
				{#if transfers.length === 0}
					<p class="text-sm text-muted-foreground">{m.transfer_empty()}</p>
				{:else}
					<Accordion.Root type="multiple" class="rounded-lg border px-4">
						{#each transfers as transfer (transfer.id)}
							<Accordion.Item value={String(transfer.id)}>
								<Accordion.Trigger class="no-underline hover:no-underline">
									<div class="flex min-w-0 flex-1 flex-wrap items-center justify-between gap-3 pr-3">
										<div class="min-w-0 text-left"><p class="truncate">{transfer.sourceAccountName ?? m.transfer_archived_account()} → {transfer.destinationAccountName ?? m.transfer_archived_account()}</p><p class="font-normal text-muted-foreground">{formatDateOnly(transfer.transferDate, data.preferences.locale)}{transfer.notes ? ` · ${transfer.notes}` : ''}</p></div>
										{#if transfer.sourceCurrency !== transfer.destinationCurrency}
											<!-- An exchange: what left and what arrived, each in its own currency. -->
											<span class="tabular-nums">{money(transfer.amount, transfer.sourceCurrency)} → {money(transfer.destinationAmount, transfer.destinationCurrency)}</span>
										{:else}
											<span class="tabular-nums">{money(transfer.amount, transfer.sourceCurrency)}</span>
										{/if}
									</div>
								</Accordion.Trigger>
								<Accordion.Content class="space-y-3 border-t pt-4">
									<TransferForm {accounts} action="?/updateTransfer" locale={data.preferences.locale} timeZone={data.preferences.timeZone} initial={transfer} />
									<form method="POST" action="?/deleteTransfer" use:enhance class="flex justify-end"><input type="hidden" name="id" value={transfer.id} /><Button type="submit" size="sm" variant="destructive"><Trash2 />{m.transfer_delete()}</Button></form>
								</Accordion.Content>
							</Accordion.Item>
						{/each}
					</Accordion.Root>
				{/if}
			</Card.Content>
		</Card.Root>

		{#if archivedAccounts.length}
			<section class="space-y-4">
				<div class="space-y-1"><h2 class="text-lg font-semibold">{m.accounts_archived_title()}</h2><p class="text-sm text-muted-foreground">{m.accounts_archived_description()}</p></div>
				<div class="grid grid-cols-[repeat(auto-fill,minmax(min(100%,17rem),1fr))] gap-4">
					{#each archivedAccounts as account (account.id)}<AccountCard {account} kindLabel={kindLabel(account.kind)} balanceLabel={accountBalance(account)} archived />{/each}
				</div>
			</section>
		{/if}
	{/if}
</div>

{#if status?.active}
	<!-- Transferring is an occasional action. It previously sat permanently
	     expanded as a five-field form between the accounts and their history,
	     pushing the history off-screen. -->
	<Dialog.Root bind:open={transferOpen}>
		<Dialog.Content class="max-h-[90dvh] overflow-y-auto sm:max-w-2xl">
			<Dialog.Header>
				<Dialog.Title>{payCardAccount ? m.credit_pay_card() : m.transfer_title()}</Dialog.Title>
				<Dialog.Description>
					{payCardAccount ? m.credit_pay_card_description() : m.transfer_description()}
				</Dialog.Description>
			</Dialog.Header>
			{#if payCardAccount}
				<p class="text-sm text-muted-foreground">
					{m.transfer_prefilled_notice({ name: payCardAccount.name })}
				</p>
			{/if}
			<TransferForm
				{accounts}
				action="?/transfer"
				locale={data.preferences.locale}
				timeZone={data.preferences.timeZone}
				prefill={payCardAccount
					? { destinationAccountId: payCardAccount.id, amount: data.payCard.amount }
					: undefined}
				primary
				onSuccess={() => (transferOpen = false)}
			/>
		</Dialog.Content>
	</Dialog.Root>

	<Dialog.Root bind:open={addAccountOpen}>
		<Dialog.Content class="max-h-[90dvh] overflow-y-auto sm:max-w-md">
			<Dialog.Header><Dialog.Title>{m.accounts_add()}</Dialog.Title><Dialog.Description>{m.account_add_description()}</Dialog.Description></Dialog.Header>
			{#if addAccountError}<Alert.Root variant="destructive"><Alert.Description>{addAccountError}</Alert.Description></Alert.Root>{/if}
			<form method="POST" action="?/create" use:enhance={enhanceCreateAccount} class="grid gap-4">
				<div class="grid gap-2"><Label for="account-name">{m.account_name()}</Label><Input id="account-name" name="name" bind:value={newAccountName} required maxlength={100} placeholder={m.account_name_placeholder()} /></div>
				<div class="grid gap-2"><Label for="account-kind">{m.account_kind()}</Label><NativeSelect id="account-kind" name="kind" value={newAccountKind} onValueChange={(value) => (newAccountKind = value)} items={kindItems} /></div>
				<div class="grid gap-2">
					<Label for="account-currency">{m.currency_label()}</Label>
					<NativeSelect id="account-currency" name="currency" value={newAccountCurrency} onValueChange={(value) => (newAccountCurrency = value)} items={currencyItems(data.preferences.supportedCurrencies)} />
					<p class="text-xs text-muted-foreground">{m.currency_fixed_hint()}</p>
				</div>
				<div class="grid gap-2"><Label for="account-opening-balance">{m.account_opening_balance()}</Label><CurrencyInput id="account-opening-balance" name="openingBalance" bind:value={newOpeningBalance} locale={data.preferences.locale} currency={newAccountCurrency} /></div>
				<div class="grid gap-1.5"><span class="text-sm font-medium">{m.common_colour()}</span><input type="hidden" name="hue" value={newAccountHue} /><ColorPicker name={newAccountName || m.common_sample()} hue={newAccountHue} onchange={(hue) => (newAccountHue = hue)} /></div>
				<Dialog.Footer><Button type="button" variant="outline" onclick={() => (addAccountOpen = false)}>{m.common_cancel()}</Button><Button type="submit">{m.accounts_add()}</Button></Dialog.Footer>
			</form>
		</Dialog.Content>
	</Dialog.Root>
{/if}
