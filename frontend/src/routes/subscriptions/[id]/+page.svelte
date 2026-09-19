<script lang="ts">
	import { toast } from 'svelte-sonner';
	import { enhance as kitEnhance } from '$app/forms';
	import * as Card from '$lib/components/ui/card';
	import * as Dialog from '$lib/components/ui/dialog';
	import { Button } from '$lib/components/ui/button';
	import { Badge } from '$lib/components/ui/badge';
	import { Checkbox } from '$lib/components/ui/checkbox';
	import * as RadioGroup from '$lib/components/ui/radio-group';
	import * as ScrollArea from '$lib/components/ui/scroll-area';
	import { NativeSelect } from '$lib/components/native-select';
	import { formatDateOnly, formatMonthYear, mxnFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
	import { SectionUnavailable } from '$lib/components/section-status';
	import { sectionValue } from '$lib/types/section';
	import { billingGenerationStatus } from '$lib/obligation-status';
	import {
		billingGenerationDescription,
		billingGenerationLabel,
		contributionBadgeVariant,
		recordStateLabel,
	} from '$lib/subscription-labels';
	import { currentSplit, periodSummary, periodsOf } from '$lib/subscription-summary';
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

	// `null` means the section could not be loaded. It is deliberately not
	// collapsed to an empty array: "no members yet" and "we could not read the
	// members" are different statements, and only one of them is a fact.
	const members = $derived(sectionValue(data.members));
	const payments = $derived(sectionValue(data.payments));
	const linkedTransactions = $derived(sectionValue(data.linkedTransactions));
	const unlinkedTransactions = $derived(sectionValue(data.unlinkedTransactions));
	// "Partial" means some sections loaded and some did not; when every section
	// failed each one says so on its own.
	const sections = $derived([members, payments, linkedTransactions, unlinkedTransactions]);
	const partialData = $derived(
		sections.some((section) => section === null) && sections.some((section) => section !== null),
	);

	const fmt = $derived(mxnFormatter(data.preferences.locale));

	function periodLabel(billingDate: string): string {
		return formatMonthYear(billingDate, data.preferences.locale);
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

	// One captured instant per render, resolved in the User's own zone, so this
	// page, the dashboard and the manual generation action agree about which
	// calendar day it is. `nextBillingDate` is a generation cursor: it says
	// whether Keenti still owes itself Payment Records, never whether the
	// provider charged or was paid.
	const generation = $derived(
		billingGenerationStatus({
			nextBillingDate: data.subscription.nextBillingDate,
			today: data.obligationToday,
		}),
	);

	// Current price, current membership — an expectation, not a bill. The
	// period figures further down come only from stored Payment Records.
	const split = $derived(currentSplit({ subscription: data.subscription, members }));

	function memberName(memberId: number | null): string {
		if (memberId === null) return m.common_owner();
		const member = (members ?? []).find((x: MemberResponse) => x.id === memberId);
		return member?.contactName ?? m.member_number({ id: memberId });
	}

	// Distinct billing periods, newest first — one tab per period.
	const periods = $derived(periodsOf(payments));

	// Find the transaction that settled a PAID record (for the "paid via" hint).
	function linkedTransaction(transactionId: number | null): TransactionResponse | undefined {
		if (transactionId === null) return undefined;
		return (linkedTransactions ?? []).find((t: TransactionResponse) => t.id === transactionId);
	}

	// Eager init (so SSR has a selected period); the effect re-points it when the
	// set of periods changes, e.g. after generating billing or deleting a period.
	let selectedPeriod = $state(
		[...new Set((sectionValue(data.payments) ?? []).map((p: PaymentRecord) => p.billingDate))].sort((a, b) =>
			b.localeCompare(a),
		)[0] ?? '',
	);
	$effect(() => {
		if (periods.length > 0) {
			if (!periods.includes(selectedPeriod)) selectedPeriod = periods[0];
			return;
		}
		// No periods at all — deleting the last one, or a payments read that
		// failed. A selection kept here would leave the summary asking for a
		// period the loaded records no longer have, which `periodSummary`
		// answers with "no records for this period": a statement about that
		// period rather than about the empty (or unreadable) section.
		if (selectedPeriod !== '') selectedPeriod = '';
	});

	// Everything about the selected period comes from the records stored for
	// it. A period billed at an older price keeps that price here; nothing is
	// recomputed from today's cost or today's member list.
	const summary = $derived(
		periodSummary({ records: payments, billingDate: selectedPeriod === '' ? null : selectedPeriod }),
	);

	// The cycle the price is charged over, shown beside the price itself. An
	// unrecognised cycle names none rather than guessing "monthly".
	const cycleLabel = $derived(
		data.subscription.billingCycle === 'MONTHLY'
			? m.billing_monthly()
			: data.subscription.billingCycle === 'YEARLY'
				? m.billing_yearly()
				: null,
	);

	let copyFeedback = $state(false);
	// Generation is idempotent per period, but a second submit while the first
	// is in flight still races two reloads against one another.
	let generating = $state(false);
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

	// A retry (or any reload) can happen while a dialog is open, so the ids a
	// dialog is holding may no longer be linkable. Drop them rather than let a
	// submit send a stale selection the candidate list no longer backs.
	$effect(() => {
		if (unlinkedTransactions === null) {
			selectedTxIds = new Set();
			payLinkTxId = '';
			return;
		}
		const available = new Set(unlinkedTransactions.map((tx: TransactionResponse) => tx.id));
		if ([...selectedTxIds].some((id) => !available.has(id))) {
			selectedTxIds = new Set([...selectedTxIds].filter((id) => available.has(id)));
		}
		if (payLinkTxId && !available.has(Number(payLinkTxId))) payLinkTxId = '';
	});

	async function copyShareLink() {
		if (!data.subscription.tokenUuid) return;
		const url = `${window.location.origin}/public/subscription/${data.subscription.tokenUuid}`;
		await navigator.clipboard.writeText(url);
		copyFeedback = true;
		setTimeout(() => (copyFeedback = false), 1500);
	}
</script>

<svelte:head><title>{data.subscription.name} · {m.subscriptions_title()} · Keenti</title></svelte:head>

<div class="mx-auto max-w-3xl space-y-6">
	<!-- Back link -->
	<Button variant="link" href="/subscriptions" class="h-auto p-0 text-muted-foreground hover:text-foreground">
		{m.common_back_to_subscriptions()}
	</Button>

	{#if partialData}
		<p class="text-sm text-muted-foreground">{m.section_partial_notice()}</p>
	{/if}

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

			<!-- No "next billing" date here. `nextBillingDate` is a generation
			     cursor, and a past cursor rendered as an upcoming charge told the
			     User the provider bills them on a day that has already gone. The
			     cursor is stated once, in the generation block below, in the
			     wording that says what it actually tracks. -->
			<div class="grid gap-1 text-sm">
				{#if data.subscription.type === 'SHARED'}
					<p class="text-muted-foreground">
						{m.subscriptions_owner_participates_label()} <span class="font-medium text-foreground">
							{data.subscription.ownerParticipates === false ? m.common_no() : data.subscription.ownerParticipates === true ? m.common_yes() : '—'}
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

	<!-- Period summary: four separate facts, deliberately not one badge.
	     What the provider charges now, what the User currently expects to
	     collect, what this period's stored records actually say, and what
	     Keenti still has to generate. None of them is a provider payment. -->
	<Card.Root>
		<Card.Content class="space-y-5">
			<h2 class="font-semibold text-base">{m.subscriptions_summary_title()}</h2>

			<!-- Current price and split -->
			<div class="space-y-2">
				<div class="grid gap-4 sm:grid-cols-3">
					<div>
						<!-- The cycle sits with the price: "600.00" alone leaves the
						     User to guess whether that is a month or a year. -->
						<p class="text-xs text-muted-foreground">
							{m.subscriptions_provider_cost_current()}{cycleLabel ? ` · ${cycleLabel}` : ''}
						</p>
						<p class="text-xl font-semibold tabular-nums">
							{split.providerCost === null ? '—' : fmt.format(split.providerCost)}
						</p>
					</div>
					<div>
						<p class="text-xs text-muted-foreground">{m.subscriptions_expected_current()}</p>
						<p class="text-xl font-semibold tabular-nums">
							{split.expectedContributions === null ? '—' : fmt.format(split.expectedContributions)}
						</p>
					</div>
					<div>
						<p class="text-xs text-muted-foreground">{m.subscriptions_own_share_current()}</p>
						<p class="text-xl font-semibold tabular-nums">
							{split.ownShare === null ? '—' : fmt.format(split.ownShare)}
						</p>
					</div>
				</div>
				<!-- One provenance line, not a paragraph: where these three
				     figures come from, and the one thing they cannot say. -->
				<p class="text-xs text-muted-foreground">{m.subscriptions_summary_provenance()}</p>
				{#if split.state === 'split'}
					<p class="text-xs text-muted-foreground">
						{m.subscriptions_split_ways({ count: split.splitCount ?? 0, amount: fmt.format(split.shareAmount ?? 0) })}{data
							.subscription.ownerParticipates === false
							? ` · ${m.subscriptions_own_share_middleman()}`
							: ''}
					</p>
					{#if split.unreadableShares > 0}
						<p class="text-xs text-muted-foreground">
							{m.subscriptions_expected_estimated_note({ count: split.unreadableShares })}
						</p>
					{/if}
					{#if split.roundingRemainder !== null}
						<p class="text-xs text-muted-foreground">
							{m.subscriptions_split_rounding_note({ amount: fmt.format(split.roundingRemainder) })}
						</p>
					{/if}
				{:else if split.state === 'personal'}
					<p class="text-xs text-muted-foreground">{m.subscriptions_own_share_personal()}</p>
				{:else if split.state === 'no-members'}
					<p class="text-xs text-muted-foreground">
						{data.subscription.ownerParticipates === false
							? m.subscriptions_own_share_unallocated()
							: m.subscriptions_no_members_yet_action()}
					</p>
				{:else}
					<p class="text-xs text-muted-foreground">{m.subscriptions_members_unavailable_hint()}</p>
				{/if}
			</div>

			<!-- Selected period, from stored Payment Records only. The period
			     control sits with the figures it changes: it used to live two
			     cards further down, so choosing a period meant scrolling away
			     from the numbers being chosen. This is the page's only period
			     control — the records list below follows it. -->
			<div class="space-y-3 border-t pt-4">
				<div class="flex flex-wrap items-center justify-between gap-2">
					<p class="text-sm font-medium">
						{m.subscriptions_summary_period()}{selectedPeriod ? `: ${periodLabel(selectedPeriod)}` : ''}
					</p>
					{#if periods.length > 1}
						<NativeSelect
							name="period"
							value={selectedPeriod}
							onValueChange={(value) => (selectedPeriod = value)}
							placeholder={m.subscriptions_period_select()}
							items={periods.map((period) => ({ value: period, label: periodLabel(period) }))}
							class="h-8 w-full text-xs sm:w-44"
							aria-label={m.subscriptions_period_select()}
						/>
					{/if}
				</div>
				{#if summary.state === 'unavailable'}
					<p class="text-sm text-muted-foreground">{m.subscriptions_period_unavailable()}</p>
				{:else if periods.length === 0}
					<!-- Loaded and genuinely empty: nothing has been generated yet. -->
					<p class="text-sm text-muted-foreground">{m.subscriptions_no_payment_records()}</p>
				{:else if summary.state === 'no-records'}
					<p class="text-sm text-muted-foreground">{m.subscriptions_period_none()}</p>
				{:else}
					<!-- `—`, never `0.00`: a period whose records could not be read
					     has no total, and a zero would be a claim about the money
					     that arrived, not a report of a failed read. -->
					<div class="grid gap-4 sm:grid-cols-3">
						<div>
							<p class="text-xs text-muted-foreground">{m.subscriptions_period_billed()}</p>
							<p class="text-lg font-semibold tabular-nums">
								{summary.billed === null ? '—' : fmt.format(summary.billed)}
							</p>
						</div>
						<div>
							<p class="text-xs text-muted-foreground">{m.subscriptions_period_collected()}</p>
							<p class="text-lg font-semibold tabular-nums text-money-positive">
								{summary.collected === null ? '—' : fmt.format(summary.collected)}
							</p>
						</div>
						<div>
							<p class="text-xs text-muted-foreground">{m.subscriptions_period_outstanding()}</p>
							<p class="text-lg font-semibold tabular-nums">
								{summary.outstanding === null ? '—' : fmt.format(summary.outstanding)}
							</p>
						</div>
					</div>
					{#if summary.billed === null}
						<p class="text-xs text-muted-foreground">{m.subscriptions_period_totals_unavailable()}</p>
					{:else}
						<p class="text-xs text-muted-foreground">
							{m.subscriptions_period_stored_note({ period: periodLabel(selectedPeriod) })}
							{m.subscriptions_contribution_awaiting_note()}
						</p>
						{#if summary.partial}
							<p class="text-xs text-muted-foreground">
								{m.subscriptions_period_totals_partial()}
								{m.subscriptions_period_unreadable({ count: summary.unreadableCount })}
							</p>
						{/if}
					{/if}
					<!-- Every own-share record, not just the first: billing writes
					     one per period, but a list that showed `[0]` alone would
					     silently drop any other and round its amount to zero. -->
					{#each summary.ownerRecords as entry (entry.record.id)}
						<p class="text-xs text-muted-foreground">
							{entry.status.amount === null
								? m.subscriptions_owner_record_unreadable()
								: `${m.subscriptions_owner_record()}: ${fmt.format(entry.status.amount)}`}
						</p>
					{/each}
				{/if}
			</div>

			<!-- Generation cursor, with the action that advances it. "Pending" is
			     about records Keenti has not written, never about an unpaid
			     provider charge. The button lives here rather than beside the
			     records list, so the state and the thing that changes it are read
			     and acted on in one place. -->
			<div class="space-y-2 border-t pt-4">
				<div class="flex flex-wrap items-start justify-between gap-2">
					<div class="min-w-0 space-y-1">
						<p class="text-sm font-medium">{m.subscriptions_generation_title()}</p>
						<p class="text-sm">{billingGenerationLabel(generation.state)}</p>
					</div>
					<form
						method="POST"
						action="?/generateBilling"
						class="shrink-0"
						use:kitEnhance={async () => {
							generating = true;
							return async ({ result, update }) => {
								generating = false;
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
						<Button type="submit" variant="outline" size="sm" disabled={generating}>
							{m.subscriptions_generate_billing()}
						</Button>
					</form>
				</div>
				<p class="text-xs text-muted-foreground">
					{billingGenerationDescription(generation, data.preferences.locale)}
				</p>
				<p class="text-xs text-muted-foreground">{m.subscriptions_action_generate_explainer()}</p>
				{#if data.subscription.type === 'SHARED' && members !== null && members.length === 0}
					<p class="text-xs text-muted-foreground">{m.subscriptions_generation_no_members()}</p>
					<div>
						<Button variant="outline" size="sm" href="/subscriptions?members={data.subscription.id}">
							{m.subscriptions_add_members()}
						</Button>
					</div>
				{/if}
				<p class="text-xs text-muted-foreground">{m.subscriptions_provider_expense_note()}</p>
			</div>
		</Card.Content>
	</Card.Root>

	<!-- Members (SHARED only) -->
	{#if data.subscription.type === 'SHARED'}
		<Card.Root>
			<Card.Content class="space-y-3">
				<h2 class="font-semibold text-base">{m.subscriptions_members()}</h2>
				{#if members === null}
					<SectionUnavailable title={m.section_members_unavailable()} compact />
				{:else if members.length === 0}
					<p class="text-sm text-muted-foreground">{m.subscriptions_no_members_assigned()}</p>
					<p class="text-sm text-muted-foreground">{m.subscriptions_no_members_yet_action()}</p>
					<Button variant="outline" size="sm" href="/subscriptions?members={data.subscription.id}">
						{m.subscriptions_add_members()}
					</Button>
				{:else}
					<ul class="divide-y">
						{#each members as member (member.id)}
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
				{#if unlinkedTransactions !== null && unlinkedTransactions.length > 0}
					<Button variant="outline" size="sm" onclick={() => { selectedTxIds = new Set(); linkDialogOpen = true; }}>
						{m.subscriptions_link_transactions()}
					</Button>
				{/if}
			</div>
			<!-- When the linked list also failed, its own notice already offers a
			     retry for the same dependency — one control, not two. -->
			{#if unlinkedTransactions === null && linkedTransactions !== null}
				<SectionUnavailable
					title={m.section_linking_unavailable_title()}
					description={m.section_linking_unavailable()}
					compact
				/>
			{/if}
			{#if linkedTransactions === null}
				<SectionUnavailable title={m.section_linked_transactions_unavailable()} compact />
			{:else if linkedTransactions.length === 0}
				<p class="text-sm text-muted-foreground">{m.subscriptions_no_linked_transactions()}</p>
			{:else}
				<ul class="divide-y rounded-md border">
					{#each linkedTransactions as tx (tx.id)}
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
			<!-- The generation action lives beside the generation state above;
			     a second copy here would be a competing control for one write. -->
			<div class="flex flex-wrap items-center justify-between gap-2">
				<h2 class="font-semibold text-base">{m.subscriptions_payment_records()}</h2>
				{#if selectedPeriod}
					<p class="text-xs text-muted-foreground">
						{m.subscriptions_period_records_title({ period: periodLabel(selectedPeriod) })}
					</p>
				{/if}
			</div>

			{#if payments === null}
				<SectionUnavailable title={m.section_payments_unavailable()} compact />
			{:else if payments.length === 0}
				<p class="text-sm text-muted-foreground">
					{m.subscriptions_no_payment_records()}
				</p>
			{:else if selectedPeriod === ''}
				<p class="text-sm text-muted-foreground">{m.subscriptions_period_none()}</p>
			{:else}
				<!-- One period, the one the summary's control selected. -->
				{@const periodRecords = [...summary.memberRecords, ...summary.ownerRecords]}
				{#if periodRecords.length > 0 && periodRecords.every((entry) => entry.record.transactionId === null)}
					<div class="flex justify-end">
						<Button
							type="button"
							variant="outline"
							size="sm"
							class="h-7 px-3 text-xs text-destructive hover:text-destructive"
							onclick={() => openDeleteBillingPeriod(selectedPeriod)}
						>
							{m.subscriptions_billing_delete()}
						</Button>
					</div>
				{/if}
				<ul class="divide-y rounded-md border">
					{#each periodRecords as entry (entry.record.id)}
						{@const payment = entry.record}
						{@const contribution = entry.status}
						{@const tx = linkedTransaction(payment.transactionId)}
						<li class="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
							<div class="min-w-0 space-y-0.5">
								<p class="text-sm font-medium">
									{payment.memberId === null ? m.subscriptions_owner_record() : memberName(payment.memberId)}
								</p>
								<!-- The stored amount for this period, never today's share. -->
								<p class="text-sm text-muted-foreground">
									{contribution.amount === null ? '—' : fmt.format(contribution.amount)}
								</p>
								{#if contribution.paidDate}
									<p class="text-xs text-muted-foreground">{m.subscriptions_paid({ date: formatDateOnly(contribution.paidDate, data.preferences.locale) })}</p>
								{/if}
								{#if tx}
									<p class="text-xs text-muted-foreground truncate">
										{m.subscriptions_paid_via_transaction({ description: tx.description })}
									</p>
								{:else if payment.transactionId !== null}
									<p class="text-xs text-muted-foreground">{m.subscriptions_link_details_unavailable()}</p>
								{:else if contribution.state === 'received' && payment.memberId !== null}
									<!-- A received contribution with no link is still received:
									     a missing link is a missing link, not an unpaid record. -->
									<p class="text-xs text-muted-foreground">{m.subscriptions_contribution_received_no_link()}</p>
								{/if}
							</div>
							<div class="flex items-center gap-2 shrink-0">
								<Badge variant={contributionBadgeVariant(contribution.state)}>
									{recordStateLabel(contribution.state, payment.memberId === null)}
								</Badge>
								{#if payment.transactionId && contribution.state !== 'unavailable'}
									<form
										method="POST"
										action="?/unlinkTransactionFromPayment"
										use:kitEnhance={async () => {
											return async ({ result, update }) => {
												if (result.type === 'success') {
													toast.success(m.subscriptions_transaction_unlinked());
													await update();
												} else {
													const msg =
														(result as { data?: { message?: string } }).data?.message ??
														m.subscriptions_transaction_unlink_failed();
													toast.error(msg);
												}
											};
										}}
									>
										<input type="hidden" name="paymentId" value={payment.id} />
										<Button type="submit" size="sm" variant="outline" class="h-7 text-xs px-3">
											{m.subscriptions_unlink_transaction()}
										</Button>
									</form>
								{/if}
								<!-- Only an awaiting contribution can be received. An
								     unreadable record offers no write against itself. -->
								{#if contribution.state === 'awaiting'}
									<Button
										type="button"
										size="sm"
										variant="outline"
										class="h-7 text-xs px-3"
										disabled={unlinkedTransactions === null}
										title={unlinkedTransactions === null ? m.section_linking_unavailable() : undefined}
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
				<!-- Each action's explanation sits with the action, not in a block
				     of prose at the top of the page. -->
				<div class="space-y-1 text-xs text-muted-foreground">
					<p>{m.subscriptions_action_record_explainer()}</p>
					<p>{m.subscriptions_action_link_explainer()}</p>
				</div>
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

			{#if unlinkedTransactions === null}
				<p class="text-sm text-muted-foreground">{m.section_linking_unavailable()}</p>
			{:else if unlinkedTransactions.length === 0}
				<p class="text-sm text-muted-foreground">{m.subscriptions_no_unlinked_transactions()}</p>
			{:else}
				<ScrollArea.Root class="h-48 rounded-md border sm:h-72">
					<ul class="divide-y">
					{#each unlinkedTransactions as tx (tx.id)}
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

			{#if unlinkedTransactions === null}
				<p class="text-sm text-muted-foreground">{m.section_linking_unavailable()}</p>
			{:else if unlinkedTransactions.length === 0}
				<p class="text-sm text-muted-foreground">{m.subscriptions_no_unlinked_transactions()}</p>
			{:else}
				<ScrollArea.Root class="h-48 rounded-md border sm:h-72">
					<RadioGroup.Root bind:value={payLinkTxId} class="gap-0">
						{#each unlinkedTransactions as tx (tx.id)}
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
