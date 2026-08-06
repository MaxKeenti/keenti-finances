<script lang="ts">
	import * as Card from '$lib/components/ui/card';
	import { Badge } from '$lib/components/ui/badge';
	import { formatDateOnly, mxnFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
	import type { PageData } from './$types';

	let { data }: { data: PageData } = $props();

	const fmt = $derived(mxnFormatter(data.preferences.locale));

	const cycleBadgeVariant: Record<string, 'info' | 'purple'> = {
		MONTHLY: 'info',
		YEARLY: 'purple',
	};

	const statusBadgeVariant: Record<string, 'warning' | 'success'> = {
		PENDING: 'warning',
		PAID: 'success',
	};

	function statusLabel(status: string): string {
		if (status === 'PAID') return m.status_paid();
		if (status === 'PENDING') return m.status_pending();
		return status;
	}

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
			const name = member.contactName ?? m.member_number({ id: member.memberId });
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
		<Card.Root>
			<Card.Content class="space-y-4">
				<div class="flex flex-wrap items-start justify-between gap-3">
					<div class="min-w-0">
						<h1 class="text-2xl font-semibold tracking-tight truncate">
							{data.subscription.subscriptionName}
						</h1>
						<p class="text-3xl font-bold text-foreground mt-1">
							{fmt.format(data.subscription.cost)}
						</p>
					</div>
					<Badge variant={cycleBadgeVariant[data.subscription.billingCycle]}>
						{data.subscription.billingCycle === 'MONTHLY' ? m.billing_monthly() : m.billing_yearly()}
					</Badge>
				</div>

				<div class="text-sm text-muted-foreground">
					{m.public_next_billing()}
					<span class="font-medium text-foreground">{formatDateOnly(data.subscription.nextBillingDate, data.preferences.locale)}</span>
				</div>

				<p class="text-xs text-muted-foreground">
					{m.public_read_only()}
				</p>
			</Card.Content>
		</Card.Root>

		<!-- Members section -->
		<Card.Root>
			<Card.Content class="space-y-3">
				<h2 class="font-semibold text-base">{m.public_members()}</h2>
				{#if !hasMembers}
					<p class="text-sm text-muted-foreground">{m.public_no_members()}</p>
				{:else}
					<ul class="divide-y">
						{#each data.subscription.members as member (member.memberId)}
							<li class="flex items-center justify-between py-2">
								<span class="text-sm">{member.contactName ?? m.member_number({ id: member.memberId })}</span>
								{#if member.shareAmount != null}
									<span class="text-sm font-medium">{fmt.format(member.shareAmount)}</span>
								{/if}
							</li>
						{/each}
					</ul>
				{/if}
			</Card.Content>
		</Card.Root>

		<!-- Payment records grouped by billing date -->
		<Card.Root>
			<Card.Content class="space-y-4">
				<h2 class="font-semibold text-base">{m.public_payment_records()}</h2>

				{#if !hasPayments}
					<p class="text-sm text-muted-foreground">{m.public_no_payment_records()}</p>
				{:else}
					{#each rowsByDate() as [date, rows] (date)}
						<div class="space-y-2">
							<p class="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
								{m.subscriptions_billing_period({ date: formatDateOnly(date, data.preferences.locale) })}
							</p>
							<ul class="divide-y rounded-md border">
								{#each rows as row (row.payment.paymentId)}
									<li class="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
										<div class="min-w-0 space-y-0.5">
											<p class="text-sm font-medium">{row.memberName}</p>
											<p class="text-sm text-muted-foreground">{fmt.format(row.payment.amount)}</p>
											{#if row.payment.paidDate}
												<p class="text-xs text-muted-foreground">{m.subscriptions_paid({ date: formatDateOnly(row.payment.paidDate, data.preferences.locale) })}</p>
											{/if}
										</div>
										<Badge variant={statusBadgeVariant[row.payment.status]}>
											{statusLabel(row.payment.status)}
										</Badge>
									</li>
								{/each}
							</ul>
						</div>
					{/each}
				{/if}
			</Card.Content>
		</Card.Root>
	</div>
</div>
