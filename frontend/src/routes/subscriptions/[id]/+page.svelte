<script lang="ts">
	import { toast } from 'svelte-sonner';
	import { enhance as kitEnhance } from '$app/forms';
	import * as Card from '$lib/components/ui/card';
	import * as Dialog from '$lib/components/ui/dialog';
	import * as Tabs from '$lib/components/ui/tabs';
	import { Button } from '$lib/components/ui/button';
	import { Badge } from '$lib/components/ui/badge';
	import { Checkbox } from '$lib/components/ui/checkbox';
	import * as RadioGroup from '$lib/components/ui/radio-group';
	import * as ScrollArea from '$lib/components/ui/scroll-area';
	import { formatDateOnly, monthYearFormatter, mxnFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
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
		transactionId: number | null;
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
		categoryHue: number | null;
		contactId: number | null;
		contactName: string | null;
		subscriptionId: number | null;
	};

	let { data }: { data: PageData } = $props();

	const fmt = $derived(mxnFormatter(data.preferences.locale));
	const monthFmt = $derived(monthYearFormatter(data.preferences.locale));

	function periodLabel(billingDate: string): string {
		return monthFmt.format(new Date(`${billingDate}T00:00:00`));
	}

	function transactionAmount(tx: TransactionResponse): string {
		return `${tx.direction === 'EGRESS' ? '-' : '+'}${fmt.format(tx.amount)}`;
	}

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
		if (memberId === null) return m.common_owner();
		const member = data.members.find((x: MemberResponse) => x.id === memberId);
		return member?.contactName ?? m.member_number({ id: memberId });
	}

	function statusLabel(status: string): string {
		if (status === 'PAID') return m.status_paid();
		if (status === 'PENDING') return m.status_pending();
		return status;
	}

	// Distinct billing periods, newest first — one tab per period.
	const periods = $derived.by(() => {
		const seen = new Set<string>();
		for (const p of data.payments) seen.add(p.billingDate);
		return Array.from(seen).sort((a, b) => b.localeCompare(a));
	});

	// Records for one period.
	function recordsForPeriod(billingDate: string): PaymentRecord[] {
		return data.payments.filter((p: PaymentRecord) => p.billingDate === billingDate);
	}

	// Find the transaction that settled a PAID record (for the "paid via" hint).
	function linkedTransaction(transactionId: number | null): TransactionResponse | undefined {
		if (transactionId === null) return undefined;
		return data.linkedTransactions.find((t: TransactionResponse) => t.id === transactionId);
	}

	// Eager init (so SSR has an active tab); the effect re-points it when the set
	// of periods changes, e.g. after generating billing or linking a payment.
	let selectedPeriod = $state(
		[...new Set(data.payments.map((p: PaymentRecord) => p.billingDate))].sort((a, b) =>
			b.localeCompare(a),
		)[0] ?? '',
	);
	$effect(() => {
		if (periods.length > 0 && !periods.includes(selectedPeriod)) {
			selectedPeriod = periods[0];
		}
	});

	let copyFeedback = $state(false);
	let linkDialogOpen = $state(false);
	let selectedTxIds = $state<Set<number>>(new Set());

	// Link a single transaction to a specific Payment Record (marks it PAID).
	let payLinkDialogOpen = $state(false);
	let payLinkPaymentId = $state<number | null>(null);
	let payLinkTxId = $state('');
	let deleteBillingPeriodDialogOpen = $state(false);
	let billingPeriodToDelete = $state('');

	function openPayLink(paymentId: number) {
		payLinkPaymentId = paymentId;
		payLinkTxId = '';
		payLinkDialogOpen = true;
	}

	function openDeleteBillingPeriod(billingDate: string) {
		billingPeriodToDelete = billingDate;
		deleteBillingPeriodDialogOpen = true;
	}

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

<svelte:head><title>{data.subscription.name} · {m.subscriptions_title()} · Keenti</title></svelte:head>

<div class="space-y-6 max-w-3xl">
	<!-- Back link -->
	<Button variant="link" href="/subscriptions" class="h-auto p-0 text-muted-foreground hover:text-foreground">
		{m.common_back_to_subscriptions()}
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
						{data.subscription.type === 'PERSONAL' ? m.subscription_personal() : m.subscription_shared()}
					</Badge>
					<Badge variant={cycleBadgeVariant[data.subscription.billingCycle]}>
						{data.subscription.billingCycle === 'MONTHLY' ? m.billing_monthly() : m.billing_yearly()}
					</Badge>
				</div>
			</div>

			<div class="grid gap-1 text-sm">
				<p class="text-muted-foreground">
					{m.subscriptions_next_billing()} <span class="font-medium text-foreground">{formatDateOnly(data.subscription.nextBillingDate, data.preferences.locale)}</span>
				</p>
				{#if data.subscription.type === 'SHARED'}
					<p class="text-muted-foreground">
						{m.subscriptions_owner_participates_label()} <span class="font-medium text-foreground">
							{data.subscription.ownerParticipates === false ? m.common_no() : m.common_yes()}
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
						{copyFeedback ? m.subscriptions_share_copied() : m.subscriptions_copy_link()}
					</Button>
					<Button variant="ghost" size="sm" href="/public/subscription/{data.subscription.tokenUuid}" target="_blank" class="shrink-0 text-xs h-7 px-2">
						{m.common_preview()}
					</Button>
				</div>
			{/if}
		</Card.Content>
	</Card.Root>

	<!-- Members (SHARED only) -->
	{#if data.subscription.type === 'SHARED'}
		<Card.Root>
			<Card.Content class="space-y-3">
				<h2 class="font-semibold text-base">{m.subscriptions_members()}</h2>
				{#if data.members.length === 0}
					<p class="text-sm text-muted-foreground">{m.subscriptions_no_members_assigned()}</p>
				{:else}
					<ul class="divide-y">
						{#each data.members as member (member.id)}
							<li class="flex items-center justify-between py-2">
								<span class="text-sm">{member.contactName ?? m.contact_number({ id: member.contactId ?? member.id })}</span>
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
				<h2 class="font-semibold text-base">{m.subscriptions_linked_transactions()}</h2>
				{#if data.unlinkedTransactions.length > 0}
					<Button variant="outline" size="sm" onclick={() => { selectedTxIds = new Set(); linkDialogOpen = true; }}>
						{m.subscriptions_link_transactions()}
					</Button>
				{/if}
			</div>
			{#if data.linkedTransactions.length === 0}
				<p class="text-sm text-muted-foreground">{m.subscriptions_no_linked_transactions()}</p>
			{:else}
				<ul class="divide-y rounded-md border">
					{#each data.linkedTransactions as tx (tx.id)}
						<li class="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
							<div class="min-w-0 space-y-0.5">
								<p class="text-sm font-medium truncate">{tx.description}</p>
								<p class="text-xs text-muted-foreground">{formatDateOnly(tx.transactionDate, data.preferences.locale)}</p>
							</div>
							<div class="flex items-center gap-2 shrink-0">
								{#if tx.categoryName}
									<Badge variant="secondary">{tx.categoryName}</Badge>
								{/if}
								<span class="text-sm font-medium {tx.direction === 'EGRESS' ? 'text-money-negative' : 'text-money-positive'}">{transactionAmount(tx)}</span>
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
			<div class="flex items-center justify-between">
				<h2 class="font-semibold text-base">{m.subscriptions_payment_records()}</h2>
				<form
					method="POST"
					action="?/generateBilling"
					use:kitEnhance={async () => {
						return async ({ result, update }) => {
							if (result.type === 'success') {
								const count = (result.data as { generated?: number })?.generated ?? 0;
								toast.success(
									count > 0
										? count === 1
											? m.subscriptions_billing_generated_one()
											: m.subscriptions_billing_generated_many({ count })
										: m.subscriptions_billing_up_to_date(),
								);
								await update();
							} else {
								const msg =
									(result as { data?: { message?: string } }).data?.message ??
									m.subscriptions_billing_failed();
								toast.error(msg);
							}
						};
					}}
				>
					<Button type="submit" variant="outline" size="sm" class="h-7 text-xs px-3">
						{m.subscriptions_generate_billing()}
					</Button>
				</form>
			</div>

			{#if data.payments.length === 0}
				<p class="text-sm text-muted-foreground">
					{m.subscriptions_no_payment_records()}
				</p>
			{:else}
				<Tabs.Root bind:value={selectedPeriod} class="w-full">
					<div class="overflow-x-auto pb-1">
						<Tabs.List>
							{#each periods as period (period)}
								<Tabs.Trigger value={period}>{periodLabel(period)}</Tabs.Trigger>
							{/each}
						</Tabs.List>
					</div>

					{#each periods as period (period)}
						<Tabs.Content value={period}>
							{@const periodRecords = recordsForPeriod(period)}
							{#if periodRecords.every((payment) => payment.transactionId === null)}
								<div class="mb-3 flex justify-end">
									<Button
										type="button"
										variant="outline"
										size="sm"
										class="h-7 px-3 text-xs text-destructive hover:text-destructive"
										onclick={() => openDeleteBillingPeriod(period)}
									>
										{m.subscriptions_billing_delete()}
									</Button>
								</div>
							{/if}
							<ul class="divide-y rounded-md border">
								{#each periodRecords as payment (payment.id)}
									{@const tx = linkedTransaction(payment.transactionId)}
									<li class="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
										<div class="min-w-0 space-y-0.5">
											<p class="text-sm font-medium">{memberName(payment.memberId)}</p>
											<p class="text-sm text-muted-foreground">{fmt.format(payment.amount)}</p>
											{#if payment.paidDate}
												<p class="text-xs text-muted-foreground">{m.subscriptions_paid({ date: formatDateOnly(payment.paidDate, data.preferences.locale) })}</p>
											{/if}
											{#if tx}
												<p class="text-xs text-muted-foreground truncate">
													{m.subscriptions_paid_via_transaction({ description: tx.description })}
												</p>
											{/if}
										</div>
										<div class="flex items-center gap-2 shrink-0">
											<Badge variant={statusBadgeVariant[payment.status]}>
												{statusLabel(payment.status)}
											</Badge>
											{#if payment.status === 'PENDING'}
												<Button
													type="button"
													size="sm"
													variant="outline"
													class="h-7 text-xs px-3"
													onclick={() => openPayLink(payment.id)}
												>
													{m.subscriptions_link_transaction()}
												</Button>
												<form
													method="POST"
													action="?/recordPayment"
													use:kitEnhance={async () => {
														return async ({ result, update }) => {
															if (result.type === 'success') {
																toast.success(m.subscriptions_payment_recorded());
																await update();
															} else {
																const msg =
																	(result as { data?: { message?: string } }).data?.message ??
																	m.subscriptions_payment_record_failed();
																toast.error(msg);
															}
														};
													}}
												>
													<input type="hidden" name="paymentId" value={payment.id} />
													<Button type="submit" size="sm" variant="outline" class="h-7 text-xs px-3">
														{m.subscriptions_record_payment()}
													</Button>
												</form>
											{/if}
										</div>
									</li>
								{/each}
							</ul>
						</Tabs.Content>
					{/each}
				</Tabs.Root>
			{/if}
		</Card.Content>
	</Card.Root>
</div>

<!-- Link Transactions dialog -->
<Dialog.Root bind:open={linkDialogOpen}>
	<Dialog.Content class="max-h-[calc(100dvh-1rem)] w-[calc(100%-1rem)] p-3 sm:max-w-lg sm:p-4">
		<Dialog.Header>
			<Dialog.Title>{m.subscriptions_link_transactions_title()}</Dialog.Title>
			<Dialog.Description>{m.subscriptions_link_transactions_description()}</Dialog.Description>
		</Dialog.Header>

		<form
			method="POST"
			action="?/linkTransactions"
			use:kitEnhance={async () => {
				return async ({ result, update }) => {
					if (result.type === 'success') {
						linkDialogOpen = false;
						selectedTxIds = new Set();
						toast.success(m.subscriptions_transactions_linked());
						await update();
					} else {
						const msg =
							(result as { data?: { message?: string } }).data?.message ??
							m.subscriptions_transactions_link_failed();
						toast.error(msg);
					}
				};
			}}
			class="space-y-3"
		>
			{#each selectedTxIds as txId}
				<input type="hidden" name="transactionId" value={txId} />
			{/each}

			{#if data.unlinkedTransactions.length === 0}
				<p class="text-sm text-muted-foreground">{m.subscriptions_no_unlinked_transactions()}</p>
			{:else}
				<ScrollArea.Root class="h-48 rounded-md border sm:h-72">
					<ul class="divide-y">
					{#each data.unlinkedTransactions as tx (tx.id)}
						<li class="flex items-center gap-2 px-3 py-2 hover:bg-muted/50">
							<Checkbox
								checked={selectedTxIds.has(tx.id)}
								onclick={() => toggleTx(tx.id)}
								aria-label={tx.description}
							/>
							<button
								type="button"
								class="flex min-w-0 flex-1 items-center justify-between gap-2 text-left"
								aria-pressed={selectedTxIds.has(tx.id)}
								onclick={() => toggleTx(tx.id)}
							>
								<span class="min-w-0 flex-1 space-y-0.5">
									<span class="block truncate text-sm font-medium">{tx.description}</span>
									<span class="block text-xs text-muted-foreground">{formatDateOnly(tx.transactionDate, data.preferences.locale)}</span>
								</span>
								<span class="flex shrink-0 items-center gap-2">
									{#if tx.categoryName}
										<Badge variant="secondary" class="hidden sm:inline-flex">{tx.categoryName}</Badge>
									{/if}
									<span class="text-sm font-medium text-money-positive">{transactionAmount(tx)}</span>
								</span>
							</button>
						</li>
					{/each}
					</ul>
				</ScrollArea.Root>
			{/if}

			<Dialog.Footer class="flex-row justify-end">
				<Button type="button" variant="outline" onclick={() => (linkDialogOpen = false)}>{m.common_cancel()}</Button>
				<Button type="submit" disabled={selectedTxIds.size === 0}>
					{selectedTxIds.size > 0
						? m.subscriptions_link_selected({ count: `(${selectedTxIds.size})` })
						: m.subscriptions_link_selected({ count: '' })}
				</Button>
			</Dialog.Footer>
		</form>
	</Dialog.Content>
</Dialog.Root>

<!-- Delete an entire billing period only when it has no linked transactions. -->
<Dialog.Root bind:open={deleteBillingPeriodDialogOpen}>
	<Dialog.Content class="sm:max-w-md">
		<Dialog.Header>
			<Dialog.Title>{m.subscriptions_billing_delete_title()}</Dialog.Title>
			<Dialog.Description>
				{m.subscriptions_billing_delete_description({ date: periodLabel(billingPeriodToDelete) })}
			</Dialog.Description>
		</Dialog.Header>
		<form
			method="POST"
			action="?/deleteBillingPeriod"
			use:kitEnhance={async () => {
				return async ({ result, update }) => {
					if (result.type === 'success') {
						deleteBillingPeriodDialogOpen = false;
						billingPeriodToDelete = '';
						toast.success(m.subscriptions_billing_delete_success());
						await update();
					} else {
						const msg =
							(result as { data?: { message?: string } }).data?.message ??
							m.subscriptions_billing_delete_failed();
						toast.error(msg);
					}
				};
			}}
		>
			<input type="hidden" name="billingDate" value={billingPeriodToDelete} />
			<Dialog.Footer>
				<Button type="button" variant="outline" onclick={() => (deleteBillingPeriodDialogOpen = false)}>{m.common_cancel()}</Button>
				<Button type="submit" variant="destructive">{m.subscriptions_billing_delete()}</Button>
			</Dialog.Footer>
		</form>
	</Dialog.Content>
</Dialog.Root>

<!-- Link a single transaction to a Payment Record (marks it paid) -->
<Dialog.Root bind:open={payLinkDialogOpen}>
	<Dialog.Content class="max-h-[calc(100dvh-1rem)] w-[calc(100%-1rem)] p-3 sm:max-w-lg sm:p-4">
		<Dialog.Header>
			<Dialog.Title>{m.subscriptions_link_one_payment_title()}</Dialog.Title>
			<Dialog.Description>{m.subscriptions_link_one_payment_description()}</Dialog.Description>
		</Dialog.Header>

		<form
			method="POST"
			action="?/linkTransactionToPayment"
			use:kitEnhance={async () => {
				return async ({ result, update }) => {
					if (result.type === 'success') {
						payLinkDialogOpen = false;
						payLinkPaymentId = null;
						payLinkTxId = '';
						toast.success(m.subscriptions_transaction_linked_payment_recorded());
						await update();
					} else {
						const msg =
							(result as { data?: { message?: string } }).data?.message ??
							m.subscriptions_transaction_link_failed();
						toast.error(msg);
					}
				};
			}}
			class="space-y-3"
		>
			<input type="hidden" name="paymentId" value={payLinkPaymentId} />
			<input type="hidden" name="transactionId" value={payLinkTxId} />

			{#if data.unlinkedTransactions.length === 0}
				<p class="text-sm text-muted-foreground">{m.subscriptions_no_unlinked_transactions()}</p>
			{:else}
				<ScrollArea.Root class="h-48 rounded-md border sm:h-72">
					<RadioGroup.Root bind:value={payLinkTxId} class="gap-0">
						{#each data.unlinkedTransactions as tx (tx.id)}
							<div class="flex items-center gap-2 border-b px-3 py-2 last:border-b-0 hover:bg-muted/50">
								<RadioGroup.Item value={String(tx.id)} aria-label={tx.description} />
								<button
									type="button"
									class="flex min-w-0 flex-1 items-center justify-between gap-2 text-left"
									aria-pressed={payLinkTxId === String(tx.id)}
									onclick={() => (payLinkTxId = String(tx.id))}
								>
									<span class="min-w-0 flex-1 space-y-0.5">
										<span class="block truncate text-sm font-medium">{tx.description}</span>
										<span class="block text-xs text-muted-foreground">{formatDateOnly(tx.transactionDate, data.preferences.locale)}</span>
									</span>
									<span class="flex shrink-0 items-center gap-2">
										{#if tx.categoryName}
											<Badge variant="secondary" class="hidden sm:inline-flex">{tx.categoryName}</Badge>
										{/if}
										<span class="text-sm font-medium text-money-positive">{transactionAmount(tx)}</span>
									</span>
								</button>
							</div>
						{/each}
					</RadioGroup.Root>
				</ScrollArea.Root>
			{/if}

			<Dialog.Footer class="flex-row justify-end">
				<Button type="button" variant="outline" onclick={() => (payLinkDialogOpen = false)}>{m.common_cancel()}</Button>
				<Button type="submit" disabled={!payLinkTxId}>{m.subscriptions_link_mark_paid()}</Button>
			</Dialog.Footer>
		</form>
	</Dialog.Content>
</Dialog.Root>
