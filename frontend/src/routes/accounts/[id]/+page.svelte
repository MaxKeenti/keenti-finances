<script lang="ts">
	import { enhance } from '$app/forms';
	import { ArrowLeft, Archive, RotateCcw } from '@lucide/svelte';
	import * as Alert from '$lib/components/ui/alert';
	import * as Card from '$lib/components/ui/card';
	import { Button } from '$lib/components/ui/button';
	import { mxnFormatter } from '$lib/formatting';
	import type { PageData } from './$types';

	let { data }: { data: PageData } = $props();
	let lifecycleError = $state('');
	const fmt = $derived(mxnFormatter(data.preferences.locale));
	const balanceLabel = $derived(
		data.account.kind === 'CREDIT' && data.account.balance < 0
			? `${fmt.format(Math.abs(data.account.balance))} owed`
			: fmt.format(data.account.balance),
	);
	const accountKindLabel = $derived(({ CASH: 'Cash', DEBIT: 'Debit', CHECKING: 'Checking', SAVINGS: 'Savings', CREDIT: 'Credit' } as Record<string, string>)[data.account.kind] ?? data.account.kind);
	const availableCredit = $derived(data.credit?.settings ? Math.max(data.credit.settings.creditLimit + data.account.balance, 0) : 0);

	function enhanceLifecycle() {
		return async ({ result, update }: { result: { type: string; data?: { message?: string } }; update: () => Promise<void> }) => {
			lifecycleError = result.type === 'failure'
				? result.data?.message ?? 'The account could not be updated.'
				: '';
			await update();
		};
	}
</script>

<svelte:head><title>{data.account.name} · Accounts · Keenti</title></svelte:head>

<div class="space-y-6">
	<div class="flex flex-wrap items-start justify-between gap-3">
		<div class="space-y-2">
			<Button href="/accounts" variant="ghost" size="sm"><ArrowLeft /> Accounts</Button>
			<div><p class="text-sm text-muted-foreground">{accountKindLabel}{data.account.archived ? ' · Archived' : ''}</p><h1 class="text-2xl font-semibold tracking-tight">{data.account.name}</h1></div>
		</div>
		{#if data.account.archived}
			<form method="POST" action="?/restore" use:enhance={enhanceLifecycle}><Button type="submit"><RotateCcw /> Restore account</Button></form>
		{:else}
			<form method="POST" action="?/archive" use:enhance={enhanceLifecycle}><Button type="submit" variant="outline" disabled={data.account.balance !== 0}><Archive /> Archive account</Button></form>
		{/if}
	</div>

	{#if lifecycleError}
		<Alert.Root variant="destructive"><Alert.Description>{lifecycleError}</Alert.Description></Alert.Root>
	{/if}

	{#if !data.account.archived && data.account.balance !== 0}
		<Alert.Root><Alert.Description>Bring this Financial Account to a zero balance before archiving it.</Alert.Description></Alert.Root>
	{/if}

	<section class="grid gap-3 sm:grid-cols-3">
		<Card.Root><Card.Header><Card.Description>Current balance</Card.Description><Card.Title class="text-2xl tabular-nums"><span class:text-destructive={data.account.kind === 'CREDIT' && data.account.balance < 0}>{balanceLabel}</span></Card.Title></Card.Header></Card.Root>
		<Card.Root><Card.Header><Card.Description>{data.account.kind === 'CREDIT' ? 'Available credit' : 'Opening balance'}</Card.Description><Card.Title class="text-2xl tabular-nums">{data.account.kind === 'CREDIT' ? fmt.format(availableCredit) : fmt.format(data.account.openingBalance)}</Card.Title></Card.Header></Card.Root>
		<Card.Root><Card.Header><Card.Description>Tracking started</Card.Description><Card.Title class="text-2xl">{data.account.openingDate}</Card.Title></Card.Header></Card.Root>
	</section>

	{#if data.credit}
		<section class="grid gap-3 sm:grid-cols-2">
			<Card.Root><Card.Header><Card.Description>Credit limit</Card.Description><Card.Title class="text-2xl tabular-nums">{data.credit.settings ? fmt.format(data.credit.settings.creditLimit) : 'Set up credit settings'}</Card.Title></Card.Header></Card.Root>
			<Card.Root><Card.Header><Card.Description>Next payment due</Card.Description><Card.Title class="text-2xl">{data.credit.nextStatement ? data.credit.nextStatement.dueDate : 'No unpaid confirmed statement'}</Card.Title></Card.Header>{#if data.credit.nextStatement}<Card.Content><p class="text-sm text-muted-foreground">{fmt.format(data.credit.nextStatement.outstandingBalance)} remaining · {fmt.format(data.credit.nextStatement.officialAvoidInterest)} to avoid interest</p></Card.Content>{/if}</Card.Root>
		</section>
		<Card.Root>
			<Card.Header><Card.Title>Credit statements</Card.Title><Card.Description>Confirmed statements are preserved; payments from Transfers are allocated to the oldest outstanding statement.</Card.Description></Card.Header>
			<Card.Content>{#if data.credit.statements.length === 0}<p class="text-sm text-muted-foreground">No confirmed statements yet. Add one from the Accounts overview.</p>{:else}<div class="divide-y rounded-md border">{#each data.credit.statements as statement}<div class="flex flex-wrap items-center justify-between gap-2 p-3 text-sm"><span>Due {statement.dueDate}</span><span class="tabular-nums">{fmt.format(statement.outstandingBalance)} remaining</span></div>{/each}</div>{/if}</Card.Content>
		</Card.Root>
	{/if}

	<Card.Root>
		<Card.Header><Card.Title>Account activity</Card.Title><Card.Description>Transactions and Transfers affecting this Financial Account.</Card.Description></Card.Header>
		<Card.Content>
			{#if data.activity.length === 0}
				<p class="text-sm text-muted-foreground">No activity recorded yet.</p>
			{:else}
				<div class="divide-y rounded-md border">
					{#each data.activity as item (item.id)}
						<div class="flex flex-wrap items-center justify-between gap-3 p-3"><div><p class="font-medium">{item.title}</p><p class="text-sm text-muted-foreground">{item.type === 'TRANSFER' ? 'Transfer' : 'Transaction'} · {item.date}{item.detail ? ` · ${item.detail}` : ''}</p></div><span class:text-destructive={item.amount < 0} class="font-medium tabular-nums">{item.amount > 0 ? '+' : '−'}{fmt.format(Math.abs(item.amount))}</span></div>
					{/each}
				</div>
			{/if}
		</Card.Content>
	</Card.Root>
</div>
