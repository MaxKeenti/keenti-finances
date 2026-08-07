<script lang="ts">
	import { enhance } from '$app/forms';
	import Plus from '@lucide/svelte/icons/plus';
	import Trash2 from '@lucide/svelte/icons/trash-2';
	import { AccountCard, AccountSetupForm, CreditAccountPanel, TransferForm, type Account, type CreditDetail, type Transfer } from '$lib/components/accounts';
	import { CurrencyInput } from '$lib/components/currency-input';
	import { NativeSelect } from '$lib/components/native-select';
	import * as Accordion from '$lib/components/ui/accordion';
	import * as Alert from '$lib/components/ui/alert';
	import { Button } from '$lib/components/ui/button';
	import * as Card from '$lib/components/ui/card';
	import * as Dialog from '$lib/components/ui/dialog';
	import { Input } from '$lib/components/ui/input';
	import { Label } from '$lib/components/ui/label';
	import { mxnFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
	import type { PageData } from './$types';

	let { data }: { data: PageData } = $props();
	const accounts = $derived(data.accounts as Account[]);
	const archivedAccounts = $derived(data.archivedAccounts as Account[]);
	const transfers = $derived(data.transfers as Transfer[]);
	const creditDetails = $derived(data.creditDetails as Record<number, CreditDetail>);
	const fmt = $derived(mxnFormatter(data.preferences.locale));
	const today = new Date().toISOString().slice(0, 10);
	const kindItems = $derived([
		{ value: 'DEBIT', label: m.account_kind_debit() },
		{ value: 'CHECKING', label: m.account_kind_checking() },
		{ value: 'SAVINGS', label: m.account_kind_savings() },
		{ value: 'CASH', label: m.account_kind_cash() },
		{ value: 'CREDIT', label: m.account_kind_credit() },
	]);

	let addAccountOpen = $state(false);
	let addAccountError = $state('');
	let newAccountKind = $state('DEBIT');
	let newOpeningBalance = $state<string | number>(0);

	function kindLabel(kind: string) {
		return kindItems.find((item) => item.value === kind)?.label ?? kind;
	}

	function accountBalance(account: Account) {
		return account.kind === 'CREDIT' && account.balance < 0
			? fmt.format(Math.abs(account.balance))
			: fmt.format(account.balance);
	}

	function openAddAccount() {
		addAccountError = '';
		newAccountKind = 'DEBIT';
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
		{#if data.status.active}<Button onclick={openAddAccount}><Plus />{m.accounts_add()}</Button>{/if}
	</header>

	{#if !data.status.active}
		<AccountSetupForm transactionNetBalance={data.status.transactionNetBalance} locale={data.preferences.locale} {today} />
	{:else}
		<section class="grid grid-cols-[repeat(auto-fit,minmax(min(100%,16rem),1fr))] gap-4" aria-label={m.accounts_title()}>
			{#each accounts as account (account.id)}
				<AccountCard {account} kindLabel={kindLabel(account.kind)} balanceLabel={accountBalance(account)} />
			{/each}
		</section>

		<Card.Root>
			<Card.Header><Card.Title>{m.transfer_title()}</Card.Title><Card.Description>{m.transfer_description()}</Card.Description></Card.Header>
			<Card.Content><TransferForm {accounts} action="?/transfer" locale={data.preferences.locale} primary /></Card.Content>
		</Card.Root>

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
										<div class="min-w-0 text-left"><p class="truncate">{transfer.sourceAccountName ?? m.transfer_archived_account()} → {transfer.destinationAccountName ?? m.transfer_archived_account()}</p><p class="font-normal text-muted-foreground">{transfer.transferDate}{transfer.notes ? ` · ${transfer.notes}` : ''}</p></div>
										<span class="tabular-nums">{fmt.format(transfer.amount)}</span>
									</div>
								</Accordion.Trigger>
								<Accordion.Content class="space-y-3 border-t pt-4">
									<TransferForm {accounts} action="?/updateTransfer" locale={data.preferences.locale} initial={transfer} />
									<form method="POST" action="?/deleteTransfer" use:enhance class="flex justify-end"><input type="hidden" name="id" value={transfer.id} /><Button type="submit" size="sm" variant="destructive"><Trash2 />{m.transfer_delete()}</Button></form>
								</Accordion.Content>
							</Accordion.Item>
						{/each}
					</Accordion.Root>
				{/if}
			</Card.Content>
		</Card.Root>

		{#each accounts.filter((account) => account.kind === 'CREDIT') as account (account.id)}
			<CreditAccountPanel {account} detail={creditDetails[account.id]} locale={data.preferences.locale} />
		{/each}

		{#if archivedAccounts.length}
			<section class="space-y-4">
				<div class="space-y-1"><h2 class="text-lg font-semibold">{m.accounts_archived_title()}</h2><p class="text-sm text-muted-foreground">{m.accounts_archived_description()}</p></div>
				<div class="grid grid-cols-[repeat(auto-fit,minmax(min(100%,16rem),1fr))] gap-4">
					{#each archivedAccounts as account (account.id)}<AccountCard {account} kindLabel={kindLabel(account.kind)} balanceLabel={accountBalance(account)} archived />{/each}
				</div>
			</section>
		{/if}
	{/if}
</div>

{#if data.status.active}
	<Dialog.Root bind:open={addAccountOpen}>
		<Dialog.Content class="sm:max-w-md">
			<Dialog.Header><Dialog.Title>{m.accounts_add()}</Dialog.Title><Dialog.Description>{m.account_add_description()}</Dialog.Description></Dialog.Header>
			{#if addAccountError}<Alert.Root variant="destructive"><Alert.Description>{addAccountError}</Alert.Description></Alert.Root>{/if}
			<form method="POST" action="?/create" use:enhance={enhanceCreateAccount} class="grid gap-4">
				<div class="grid gap-2"><Label for="account-name">{m.account_name()}</Label><Input id="account-name" name="name" required maxlength={100} placeholder={m.account_name_placeholder()} /></div>
				<div class="grid gap-2"><Label for="account-kind">{m.account_kind()}</Label><NativeSelect id="account-kind" name="kind" value={newAccountKind} onValueChange={(value) => (newAccountKind = value)} items={kindItems} /></div>
				<div class="grid gap-2"><Label for="account-opening-balance">{m.account_opening_balance()}</Label><CurrencyInput id="account-opening-balance" name="openingBalance" bind:value={newOpeningBalance} locale={data.preferences.locale} /></div>
				<Dialog.Footer><Button type="button" variant="outline" onclick={() => (addAccountOpen = false)}>{m.common_cancel()}</Button><Button type="submit">{m.accounts_add()}</Button></Dialog.Footer>
			</form>
		</Dialog.Content>
	</Dialog.Root>
{/if}
