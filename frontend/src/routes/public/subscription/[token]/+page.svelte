<script lang="ts">
	import type { PageData } from './$types';

	let { data }: { data: PageData } = $props();

	const fmt = new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' });

	const cycleBadgeClass: Record<string, string> = {
		MONTHLY: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400',
		YEARLY: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400',
	};

	const statusBadgeClass: Record<string, string> = {
		PENDING: 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-400',
		PAID: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
	};

	type PaymentSummary = {
		paymentId: number;
		billingDate: string;
		amount: number;
		status: string;
		paidDate: string | null;
	};

	type MemberRow = {
		memberName: string;
		shareAmount: number | null;
		payment: PaymentSummary;
	};

	// Flatten members × payments into rows grouped by billingDate
	const rowsByDate = $derived(() => {
		const groups = new Map<string, MemberRow[]>();
		for (const member of data.subscription.members) {
			const name = member.contactName ?? `Member #${member.memberId}`;
			for (const payment of member.payments) {
				const key = payment.billingDate;
				if (!groups.has(key)) groups.set(key, []);
				groups.get(key)!.push({ memberName: name, shareAmount: member.shareAmount, payment });
			}
		}
		return Array.from(groups.entries()).sort(([a], [b]) => b.localeCompare(a));
	});

	const hasMembers = $derived(data.subscription.members.length > 0);
	const hasPayments = $derived(data.subscription.members.some((m) => m.payments.length > 0));
</script>

<div class="min-h-screen bg-background py-10 px-4">
	<div class="mx-auto max-w-3xl space-y-6">
		<!-- Header card -->
		<div class="rounded-lg border bg-card p-6 space-y-4">
			<div class="flex flex-wrap items-start justify-between gap-3">
				<div class="min-w-0">
					<h1 class="text-2xl font-semibold tracking-tight truncate">
						{data.subscription.subscriptionName}
					</h1>
					<p class="text-3xl font-bold text-foreground mt-1">
						{fmt.format(data.subscription.cost)}
					</p>
				</div>
				<span
					class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium {cycleBadgeClass[data.subscription.billingCycle] ?? ''}"
				>
					{data.subscription.billingCycle === 'MONTHLY' ? 'Monthly' : 'Yearly'}
				</span>
			</div>

			<div class="text-sm text-muted-foreground">
				Next billing:
				<span class="font-medium text-foreground">{data.subscription.nextBillingDate}</span>
			</div>

			<p class="text-xs text-muted-foreground">
				This is a read-only shared view. No account required.
			</p>
		</div>

		<!-- Members section -->
		<div class="rounded-lg border bg-card p-5 space-y-3">
			<h2 class="font-semibold text-base">Members</h2>
			{#if !hasMembers}
				<p class="text-sm text-muted-foreground">No members assigned yet.</p>
			{:else}
				<ul class="divide-y">
					{#each data.subscription.members as member (member.memberId)}
						<li class="flex items-center justify-between py-2">
							<span class="text-sm">{member.contactName ?? `Member #${member.memberId}`}</span>
							{#if member.shareAmount != null}
								<span class="text-sm font-medium">{fmt.format(member.shareAmount)}</span>
							{/if}
						</li>
					{/each}
				</ul>
			{/if}
		</div>

		<!-- Payment records grouped by billing date -->
		<div class="rounded-lg border bg-card p-5 space-y-4">
			<h2 class="font-semibold text-base">Payment Records</h2>

			{#if !hasPayments}
				<p class="text-sm text-muted-foreground">No payment records yet.</p>
			{:else}
				{#each rowsByDate() as [date, rows] (date)}
					<div class="space-y-2">
						<p class="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
							Billing period: {date}
						</p>
						<ul class="divide-y rounded-md border">
							{#each rows as row (row.payment.paymentId)}
								<li class="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
									<div class="min-w-0 space-y-0.5">
										<p class="text-sm font-medium">{row.memberName}</p>
										<p class="text-sm text-muted-foreground">{fmt.format(row.payment.amount)}</p>
										{#if row.payment.paidDate}
											<p class="text-xs text-muted-foreground">Paid: {row.payment.paidDate}</p>
										{/if}
									</div>
									<span
										class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium {statusBadgeClass[row.payment.status] ?? ''}"
									>
										{row.payment.status}
									</span>
								</li>
							{/each}
						</ul>
					</div>
				{/each}
			{/if}
		</div>
	</div>
</div>
