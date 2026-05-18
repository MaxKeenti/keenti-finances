<script lang="ts">
	import { toast } from 'svelte-sonner';
	import { enhance as kitEnhance } from '$app/forms';
	import * as Card from '$lib/components/ui/card';
	import * as Dialog from '$lib/components/ui/dialog';
	import { Button } from '$lib/components/ui/button';
	import { Badge } from '$lib/components/ui/badge';
	import type { PageData } from './$types';

	type MemberResponse = {
		id: number;
		subscriptionId: number;
		contactId: number | null;
		contactName: string | null;
		shareAmount: number | null;
		createdAt: string;
	};

	type PaymentRecord = {
		id: number;
		subscriptionId: number;
		memberId: number | null;
		billingDate: string;
		amount: number;
		status: string;
		paidDate: string | null;
		createdAt: string;
	};

	type TransactionResponse = {
		id: number;
		amount: number;
		direction: string;
		description: string;
		transactionDate: string;
		categoryId: number | null;
		categoryName: string | null;
		categoryColor: string | null;
		contactId: number | null;
		contactName: string | null;
		subscriptionId: number | null;
	};

	let { data }: { data: PageData } = $props();

	const fmt = new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' });

	const cycleBadgeVariant: Record<string, 'info' | 'purple'> = {
		MONTHLY: 'info',
		YEARLY: 'purple',
	};

	const typeBadgeVariant: Record<string, 'secondary' | 'warning'> = {
		PERSONAL: 'secondary',
		SHARED: 'warning',
	};

	const statusBadgeVariant: Record<string, 'warning' | 'success'> = {
		PENDING: 'warning',
		PAID: 'success',
	};

	function memberName(memberId: number | null): string {
		if (memberId === null) return 'Owner';
		const m = data.members.find((x: MemberResponse) => x.id === memberId);
		return m?.contactName ?? `Member #${memberId}`;
	}

	// Group payments by billingDate
	const paymentsByDate = $derived(() => {
		const groups = new Map<string, PaymentRecord[]>();
		for (const p of data.payments) {
			const key = p.billingDate;
			if (!groups.has(key)) groups.set(key, []);
			groups.get(key)!.push(p);
		}
		// Sort dates descending
		return Array.from(groups.entries()).sort(([a], [b]) => b.localeCompare(a));
	});

	let copyFeedback = $state(false);
	let linkDialogOpen = $state(false);
	let selectedTxIds = $state<Set<number>>(new Set());

	function toggleTx(id: number) {
		const next = new Set(selectedTxIds);
		if (next.has(id)) next.delete(id);
		else next.add(id);
		selectedTxIds = next;
	}

	async function copyShareLink() {
		if (!data.subscription.tokenUuid) return;
		const url = `${window.location.origin}/public/subscription/${data.subscription.tokenUuid}`;
		await navigator.clipboard.writeText(url);
		copyFeedback = true;
		setTimeout(() => (copyFeedback = false), 1500);
	}
</script>

<div class="space-y-6 max-w-3xl">
	<!-- Back link -->
	<Button variant="link" href="/subscriptions" class="h-auto p-0 text-muted-foreground hover:text-foreground">
		← Back to Subscriptions
	</Button>

	<!-- Header -->
	<Card.Root>
		<Card.Content class="space-y-4">
			<div class="flex flex-wrap items-start justify-between gap-3">
				<div class="min-w-0">
					<h1 class="text-2xl font-semibold tracking-tight truncate">{data.subscription.name}</h1>
					<p class="text-3xl font-bold text-foreground mt-1">{fmt.format(data.subscription.cost)}</p>
				</div>
				<div class="flex flex-wrap gap-2 shrink-0">
					<Badge variant={typeBadgeVariant[data.subscription.type]}>
						{data.subscription.type === 'PERSONAL' ? 'Personal' : 'Shared'}
					</Badge>
					<Badge variant={cycleBadgeVariant[data.subscription.billingCycle]}>
						{data.subscription.billingCycle === 'MONTHLY' ? 'Monthly' : 'Yearly'}
					</Badge>
				</div>
			</div>

			<div class="grid gap-1 text-sm">
				<p class="text-muted-foreground">
					Next billing: <span class="font-medium text-foreground">{data.subscription.nextBillingDate}</span>
				</p>
				{#if data.subscription.type === 'SHARED'}
					<p class="text-muted-foreground">
						Owner participates: <span class="font-medium text-foreground">
							{data.subscription.ownerParticipates === false ? 'No' : 'Yes'}
						</span>
					</p>
				{/if}
			</div>

			{#if data.subscription.type === 'SHARED' && data.subscription.tokenUuid}
				<div class="flex items-center gap-2 rounded-md bg-muted px-3 py-2">
					<span class="text-xs text-muted-foreground font-mono flex-1 truncate">
						/public/subscription/{data.subscription.tokenUuid}
					</span>
					<Button variant="ghost" size="sm" onclick={copyShareLink} class="shrink-0 text-xs h-7 px-2">
						{copyFeedback ? 'Copied!' : 'Copy link'}
					</Button>
					<Button variant="ghost" size="sm" href="/public/subscription/{data.subscription.tokenUuid}" target="_blank" class="shrink-0 text-xs h-7 px-2">
						Preview
					</Button>
				</div>
			{/if}
		</Card.Content>
	</Card.Root>

	<!-- Members (SHARED only) -->
	{#if data.subscription.type === 'SHARED'}
		<Card.Root>
			<Card.Content class="space-y-3">
				<h2 class="font-semibold text-base">Members</h2>
				{#if data.members.length === 0}
					<p class="text-sm text-muted-foreground">No members assigned yet.</p>
				{:else}
					<ul class="divide-y">
						{#each data.members as member (member.id)}
							<li class="flex items-center justify-between py-2">
								<span class="text-sm">{member.contactName ?? `Contact #${member.contactId}`}</span>
								{#if member.shareAmount != null}
									<span class="text-sm font-medium">{fmt.format(member.shareAmount)}</span>
								{/if}
							</li>
						{/each}
					</ul>
				{/if}
			</Card.Content>
		</Card.Root>
	{/if}

	<!-- Linked Transactions -->
	<Card.Root>
		<Card.Content class="space-y-3">
			<div class="flex items-center justify-between">
				<h2 class="font-semibold text-base">Linked Transactions</h2>
				{#if data.unlinkedTransactions.length > 0}
					<Button variant="outline" size="sm" onclick={() => { selectedTxIds = new Set(); linkDialogOpen = true; }}>
						Link Transactions
					</Button>
				{/if}
			</div>
			{#if data.linkedTransactions.length === 0}
				<p class="text-sm text-muted-foreground">No transactions linked yet.</p>
			{:else}
				<ul class="divide-y rounded-md border">
					{#each data.linkedTransactions as tx (tx.id)}
						<li class="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
							<div class="min-w-0 space-y-0.5">
								<p class="text-sm font-medium truncate">{tx.description}</p>
								<p class="text-xs text-muted-foreground">{tx.transactionDate}</p>
							</div>
							<div class="flex items-center gap-2 shrink-0">
								{#if tx.categoryName}
									<Badge variant="secondary">{tx.categoryName}</Badge>
								{/if}
								<span class="text-sm font-medium">{fmt.format(tx.amount)}</span>
							</div>
						</li>
					{/each}
				</ul>
			{/if}
		</Card.Content>
	</Card.Root>

	<!-- Payment Records -->
	<Card.Root>
		<Card.Content class="space-y-4">
			<h2 class="font-semibold text-base">Payment Records</h2>

			{#if data.payments.length === 0}
				<p class="text-sm text-muted-foreground">
					No payment records yet. The scheduler generates upcoming records daily.
				</p>
			{:else}
				{#each paymentsByDate() as [date, records] (date)}
					<div class="space-y-2">
						<p class="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
							Billing period: {date}
						</p>
						<ul class="divide-y rounded-md border">
							{#each records as payment (payment.id)}
								<li class="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
									<div class="min-w-0 space-y-0.5">
										<p class="text-sm font-medium">{memberName(payment.memberId)}</p>
										<p class="text-sm text-muted-foreground">{fmt.format(payment.amount)}</p>
										{#if payment.paidDate}
											<p class="text-xs text-muted-foreground">Paid: {payment.paidDate}</p>
										{/if}
									</div>
									<div class="flex items-center gap-3 shrink-0">
										<Badge variant={statusBadgeVariant[payment.status]}>
											{payment.status}
										</Badge>
										{#if payment.status === 'PENDING'}
											<form
												method="POST"
												action="?/recordPayment"
												use:kitEnhance={async () => {
													return async ({ result, update }) => {
														if (result.type === 'success') {
															toast.success('Payment recorded.');
															await update();
														} else {
															const msg =
																(result as { data?: { message?: string } }).data?.message ??
																'Failed to record payment.';
															toast.error(msg);
														}
													};
												}}
											>
												<input type="hidden" name="paymentId" value={payment.id} />
												<Button type="submit" size="sm" variant="outline" class="h-7 text-xs px-3">
													Record Payment
												</Button>
											</form>
										{/if}
									</div>
								</li>
							{/each}
						</ul>
					</div>
				{/each}
			{/if}
		</Card.Content>
	</Card.Root>
</div>

<!-- Link Transactions dialog -->
<Dialog.Root bind:open={linkDialogOpen}>
	<Dialog.Content class="sm:max-w-lg">
		<Dialog.Header>
			<Dialog.Title>Link Transactions</Dialog.Title>
			<Dialog.Description>Select transactions to link to this subscription.</Dialog.Description>
		</Dialog.Header>

		<form
			method="POST"
			action="?/linkTransactions"
			use:kitEnhance={async () => {
				return async ({ result, update }) => {
					if (result.type === 'success') {
						linkDialogOpen = false;
						selectedTxIds = new Set();
						toast.success('Transactions linked.');
						await update();
					} else {
						const msg =
							(result as { data?: { message?: string } }).data?.message ??
							'Failed to link transactions.';
						toast.error(msg);
					}
				};
			}}
			class="space-y-4"
		>
			{#each selectedTxIds as txId}
				<input type="hidden" name="transactionId" value={txId} />
			{/each}

			{#if data.unlinkedTransactions.length === 0}
				<p class="text-sm text-muted-foreground">No unlinked transactions available.</p>
			{:else}
				<ul class="divide-y rounded-md border max-h-72 overflow-y-auto">
					{#each data.unlinkedTransactions as tx (tx.id)}
						<li class="flex items-center gap-3 px-4 py-3 cursor-pointer hover:bg-muted/50" onclick={() => toggleTx(tx.id)}>
							<input
								type="checkbox"
								class="h-4 w-4 rounded border border-input shrink-0"
								checked={selectedTxIds.has(tx.id)}
								onchange={() => toggleTx(tx.id)}
							/>
							<div class="min-w-0 flex-1">
								<p class="text-sm font-medium truncate">{tx.description}</p>
								<p class="text-xs text-muted-foreground">{tx.transactionDate}</p>
							</div>
							<div class="flex items-center gap-2 shrink-0">
								{#if tx.categoryName}
									<Badge variant="secondary">{tx.categoryName}</Badge>
								{/if}
								<span class="text-sm font-medium">{fmt.format(tx.amount)}</span>
							</div>
						</li>
					{/each}
				</ul>
			{/if}

			<Dialog.Footer>
				<Button type="button" variant="outline" onclick={() => (linkDialogOpen = false)}>Cancel</Button>
				<Button type="submit" disabled={selectedTxIds.size === 0}>
					Link {selectedTxIds.size > 0 ? `(${selectedTxIds.size})` : ''}
				</Button>
			</Dialog.Footer>
		</form>
	</Dialog.Content>
</Dialog.Root>
