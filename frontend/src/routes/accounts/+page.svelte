<script lang="ts">
	import { enhance } from '$app/forms';
	import { Plus, ArrowLeftRight } from '@lucide/svelte';
	import * as Card from '$lib/components/ui/card';
	import * as Dialog from '$lib/components/ui/dialog';
	import * as Alert from '$lib/components/ui/alert';
	import { Button } from '$lib/components/ui/button';
	import { Input } from '$lib/components/ui/input';
	import { mxnFormatter } from '$lib/formatting';
	import type { PageData } from './$types';

	let { data }: { data: PageData } = $props();
	type Account = { id: number; name: string; kind: string; balance: number };
	type CreditDetail = { settings: { creditLimit: number; statementClosingDay: number; paymentDueDay: number } | null; statements: Array<{ id: number; dueDate: string; officialBalance: number; officialMinimumPayment: number; officialAvoidInterest: number; paidAmount: number; outstandingBalance: number }> };
	const accounts = $derived(data.accounts as Account[]);
	const creditDetails = $derived(data.creditDetails as Record<number, CreditDetail>);
	let setupAccounts = $state([{ name: '', kind: 'DEBIT', openingBalance: 0 }]);
	let addAccountOpen = $state(false);
	let addAccountError = $state('');
	const fmt = $derived(mxnFormatter(data.preferences.locale));
	const today = new Date().toISOString().slice(0, 10);
	const kinds = [
		{ value: 'DEBIT', label: 'Debit' }, { value: 'CHECKING', label: 'Checking' },
		{ value: 'SAVINGS', label: 'Savings' }, { value: 'CASH', label: 'Cash' }, { value: 'CREDIT', label: 'Credit' },
	];
	const setupTotal = $derived(setupAccounts.reduce((sum, account) => sum + Number(account.openingBalance || 0), 0));

	function openAddAccount() {
		addAccountError = '';
		addAccountOpen = true;
	}

	function enhanceCreateAccount({ formElement }: { formElement: HTMLFormElement }) {
		return async ({ result, update }: { result: { type: string; data?: { message?: string } }; update: () => Promise<void> }) => {
			if (result.type === 'success') {
				addAccountOpen = false;
				addAccountError = '';
				formElement.reset();
			} else if (result.type === 'failure') {
				addAccountError = result.data?.message ?? 'The account could not be created.';
			}
			await update();
		};
	}
</script>

<div class="space-y-6">
	<div class="flex flex-wrap items-start justify-between gap-3">
		<div>
		<h1 class="text-2xl font-semibold tracking-tight">Accounts</h1>
		<p class="text-sm text-muted-foreground">Track real money and credit debt without changing Net Balance when you transfer between accounts.</p>
		</div>
		{#if data.status.active}
			<Button onclick={openAddAccount}><Plus /> Add account</Button>
		{/if}
	</div>

	{#if !data.status.active}
		<Card.Root>
			<Card.Header><Card.Title>Set up account tracking</Card.Title><Card.Description>Your opening balances must equal the Net Balance already recorded in Keenti: {fmt.format(data.status.transactionNetBalance)}.</Card.Description></Card.Header>
			<Card.Content>
				<form method="POST" action="?/activate" use:enhance class="space-y-4">
					<input type="hidden" name="activationDate" value={today} />
					<input type="hidden" name="accounts" value={JSON.stringify(setupAccounts)} />
					{#each setupAccounts as account, index}
						<div class="grid gap-3 sm:grid-cols-3">
							<Input bind:value={account.name} placeholder="Account name, e.g. BBVA" />
							<select class="border-input bg-background h-9 rounded-md border px-3 text-sm" bind:value={account.kind}>{#each kinds as kind}<option value={kind.value}>{kind.label}</option>{/each}</select>
							<Input type="number" step="0.01" bind:value={account.openingBalance} placeholder="Opening balance" />
						</div>
					{/each}
					<div class="flex flex-wrap items-center gap-3"><Button type="button" variant="outline" onclick={() => setupAccounts = [...setupAccounts, { name: '', kind: 'DEBIT', openingBalance: 0 }]}><Plus /> Add account</Button><span class="text-sm text-muted-foreground">Entered: {fmt.format(setupTotal)}</span><Button type="submit">Activate tracking</Button></div>
				</form>
			</Card.Content>
		</Card.Root>
	{:else}
		<section class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
			{#each accounts as account}
				<Card.Root><Card.Header><Card.Description>{account.kind}</Card.Description><Card.Title>{account.name}</Card.Title><p class:text-destructive={account.kind === 'CREDIT' && account.balance < 0} class="text-xl tabular-nums">{account.kind === 'CREDIT' && account.balance < 0 ? `${fmt.format(Math.abs(account.balance))} owed` : fmt.format(account.balance)}</p></Card.Header></Card.Root>
			{/each}
		</section>

		<Card.Root>
			<Card.Header><Card.Title>Transfer</Card.Title><Card.Description>Transfers move money between Accounts and never affect Net Balance or Boxes.</Card.Description></Card.Header>
			<Card.Content><form method="POST" action="?/transfer" use:enhance class="grid gap-3 sm:grid-cols-4">
				<select class="border-input bg-background h-9 rounded-md border px-3 text-sm" name="sourceAccountId"><option value="">From</option>{#each accounts as account}<option value={account.id}>{account.name}</option>{/each}</select>
				<select class="border-input bg-background h-9 rounded-md border px-3 text-sm" name="destinationAccountId"><option value="">To</option>{#each accounts as account}<option value={account.id}>{account.name}</option>{/each}</select>
				<Input name="amount" type="number" step="0.01" min="0.01" placeholder="Amount" />
				<Button type="submit"><ArrowLeftRight /> Transfer</Button>
				<input type="hidden" name="transferDate" value={today} /><input type="hidden" name="notes" value="" />
			</form></Card.Content>
		</Card.Root>

		{#each accounts.filter((account) => account.kind === 'CREDIT') as account}
			{@const detail = creditDetails[account.id]}
			<Card.Root>
				<Card.Header><Card.Title>{account.name} credit payment</Card.Title><Card.Description>Confirm bank statements here. Transfers into this account remain payments and never affect Net Balance.</Card.Description></Card.Header>
				<Card.Content class="space-y-5">
					<details>
						<summary class="cursor-pointer text-sm font-medium">Credit account settings</summary>
						<form method="POST" action="?/saveCreditSettings" use:enhance class="mt-3 grid gap-3 sm:grid-cols-4">
							<input type="hidden" name="accountId" value={account.id} />
							<Input name="creditLimit" type="number" min="0.01" step="0.01" required value={detail?.settings?.creditLimit ?? ''} placeholder="Credit limit" />
							<Input name="statementClosingDay" type="number" min="1" max="31" required value={detail?.settings?.statementClosingDay ?? ''} placeholder="Closing day" />
							<Input name="paymentDueDay" type="number" min="1" max="31" required value={detail?.settings?.paymentDueDay ?? ''} placeholder="Due day" />
							<Button type="submit">Save settings</Button>
						</form>
					</details>

					<form method="POST" action="?/confirmCreditStatement" use:enhance class="grid gap-3 sm:grid-cols-3">
						<input type="hidden" name="accountId" value={account.id} />
						<Input name="periodStart" type="date" required aria-label="Statement period start" />
						<Input name="periodEnd" type="date" required aria-label="Statement period end" />
						<Input name="dueDate" type="date" required aria-label="Statement due date" />
						<Input name="officialBalance" type="number" min="0" step="0.01" required placeholder="Official balance" />
						<Input name="officialMinimumPayment" type="number" min="0" step="0.01" required placeholder="Minimum payment" />
						<Input name="officialAvoidInterest" type="number" min="0" step="0.01" required placeholder="Avoid-interest payment" />
						<Input name="officialNote" class="sm:col-span-2" placeholder="Optional note" />
						<Button type="submit">Confirm statement</Button>
					</form>

					{#if detail?.statements?.length}
						<div class="space-y-2 text-sm">
							<p class="font-medium">Confirmed statements</p>
							{#each detail.statements as statement}
								<div class="flex flex-wrap justify-between gap-2 rounded-md border p-3"><span>Due {statement.dueDate}</span><span>{fmt.format(statement.outstandingBalance)} remaining · {fmt.format(statement.officialAvoidInterest)} to avoid interest</span></div>
							{/each}
						</div>
					{/if}
				</Card.Content>
			</Card.Root>
		{/each}
	{/if}
</div>

{#if data.status.active}
	<Dialog.Root bind:open={addAccountOpen}>
		<Dialog.Content class="sm:max-w-md">
			<Dialog.Header>
				<Dialog.Title>Add account</Dialog.Title>
				<Dialog.Description>New Financial Accounts start at a zero opening balance. Record a Transfer or Transaction to reflect money already in the account.</Dialog.Description>
			</Dialog.Header>

			{#if addAccountError}
				<Alert.Root variant="destructive"><Alert.Description>{addAccountError}</Alert.Description></Alert.Root>
			{/if}

			<form method="POST" action="?/create" use:enhance={enhanceCreateAccount} class="grid gap-4">
				<div class="grid gap-2">
					<label class="text-sm font-medium" for="account-name">Account name</label>
					<Input id="account-name" name="name" required maxlength={100} placeholder="e.g. BBVA Savings" />
				</div>
				<div class="grid gap-2">
					<label class="text-sm font-medium" for="account-kind">Account kind</label>
					<select id="account-kind" class="border-input bg-background h-9 rounded-md border px-3 text-sm" name="kind">
						{#each kinds as kind}<option value={kind.value}>{kind.label}</option>{/each}
					</select>
				</div>
				<input type="hidden" name="openingBalance" value="0" />
				<div class="flex justify-end gap-2">
					<Button type="button" variant="outline" onclick={() => addAccountOpen = false}>Cancel</Button>
					<Button type="submit">Add account</Button>
				</div>
			</form>
		</Dialog.Content>
	</Dialog.Root>
{/if}
