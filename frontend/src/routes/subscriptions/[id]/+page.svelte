<script lang="ts">
	import { toast } from 'svelte-sonner';
	import { enhance as kitEnhance } from '$app/forms';
	import { Button } from '$lib/components/ui/button';
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

	let { data }: { data: PageData } = $props();

	const fmt = new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' });

	const cycleBadgeClass: Record<string, string> = {
		MONTHLY: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400',
		YEARLY: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400',
	};

	const typeBadgeClass: Record<string, string> = {
		PERSONAL: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
		SHARED: 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-400',
	};

	const statusBadgeClass: Record<string, string> = {
		PENDING: 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-400',
		PAID: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
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

	async function copyToken() {
		if (!data.subscription.tokenUuid) return;
		await navigator.clipboard.writeText(data.subscription.tokenUuid);
		copyFeedback = true;
		setTimeout(() => (copyFeedback = false), 1500);
	}
</script>

<div class="space-y-6 max-w-3xl">
	<!-- Back link -->
	<a
		href="/subscriptions"
		class="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
	>
		← Back to Subscriptions
	</a>

	<!-- Header -->
	<div class="rounded-lg border bg-card p-6 space-y-4">
		<div class="flex flex-wrap items-start justify-between gap-3">
			<div class="min-w-0">
				<h1 class="text-2xl font-semibold tracking-tight truncate">{data.subscription.name}</h1>
				<p class="text-3xl font-bold text-foreground mt-1">{fmt.format(data.subscription.cost)}</p>
			</div>
			<div class="flex flex-wrap gap-2 shrink-0">
				<span
					class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium {typeBadgeClass[data.subscription.type] ?? ''}"
				>
					{data.subscription.type === 'PERSONAL' ? 'Personal' : 'Shared'}
				</span>
				<span
					class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium {cycleBadgeClass[data.subscription.billingCycle] ?? ''}"
				>
					{data.subscription.billingCycle === 'MONTHLY' ? 'Monthly' : 'Yearly'}
				</span>
			</div>
		</div>

		<div class="grid gap-1 text-sm">
			<p class="text-muted-foreground">
				Next billing: <span class="font-medium text-foreground">{data.subscription.nextBillingDate}</span>
			</p>
		</div>

		{#if data.subscription.type === 'SHARED' && data.subscription.tokenUuid}
			<div class="flex items-center gap-2 rounded-md bg-muted px-3 py-2">
				<span class="text-xs text-muted-foreground font-mono flex-1 truncate">
					Token: {data.subscription.tokenUuid}
				</span>
				<Button variant="ghost" size="sm" onclick={copyToken} class="shrink-0 text-xs h-7 px-2">
					{copyFeedback ? 'Copied!' : 'Copy'}
				</Button>
			</div>
		{/if}
	</div>

	<!-- Members (SHARED only) -->
	{#if data.subscription.type === 'SHARED'}
		<div class="rounded-lg border bg-card p-5 space-y-3">
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
		</div>
	{/if}

	<!-- Payment Records -->
	<div class="rounded-lg border bg-card p-5 space-y-4">
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
									<span
										class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium {statusBadgeClass[payment.status] ?? ''}"
									>
										{payment.status}
									</span>
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
	</div>
</div>
