<script lang="ts">
	import { enhance } from '$app/forms';
	import { Plus, ArrowLeftRight } from '@lucide/svelte';
	import * as Card from '$lib/components/ui/card';
	import { Button } from '$lib/components/ui/button';
	import { Input } from '$lib/components/ui/input';
	import { mxnFormatter } from '$lib/formatting';
	import type { PageData } from './$types';

	let { data }: { data: PageData } = $props();
	type Account = { id: number; name: string; kind: string; balance: number };
	const accounts = $derived(data.accounts as Account[]);
	let setupAccounts = $state([{ name: '', kind: 'DEBIT', openingBalance: 0 }]);
	const fmt = $derived(mxnFormatter(data.preferences.locale));
	const today = new Date().toISOString().slice(0, 10);
	const kinds = [
		{ value: 'DEBIT', label: 'Debit' }, { value: 'CHECKING', label: 'Checking' },
		{ value: 'SAVINGS', label: 'Savings' }, { value: 'CASH', label: 'Cash' }, { value: 'CREDIT', label: 'Credit' },
	];
	const setupTotal = $derived(setupAccounts.reduce((sum, account) => sum + Number(account.openingBalance || 0), 0));
</script>

<div class="space-y-6">
	<div>
		<h1 class="text-2xl font-semibold tracking-tight">Accounts</h1>
		<p class="text-sm text-muted-foreground">Track real money and credit debt without changing Net Balance when you transfer between accounts.</p>
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
	{/if}
</div>
