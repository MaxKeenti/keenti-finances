<script lang="ts">
	/**
	 * Section 1 — the signed current position (decision D1).
	 *
	 * Every figure here arrives from the composed read model; none is computed
	 * on screen. The section's job is to make the arithmetic visible — money
	 * held, credit, Net Balance, In Boxes, Available to Spend — so a total the
	 * User disagrees with can be traced to the account or Box behind it rather
	 * than being taken on faith.
	 *
	 * Three things stay deliberately outside those totals: available credit,
	 * which is limit-derived capacity and not money held; any confirmed
	 * statement payment, which the attention section states separately because
	 * the purchases behind it already moved Net Balance; and expected money,
	 * which has not been received.
	 */
	import * as Card from '$lib/components/ui/card';
	import { Button } from '$lib/components/ui/button';
	import { ArrowRight } from '@lucide/svelte';
	import { SectionUnavailable } from '$lib/components/section-status';
	import { NetBalanceNote } from '$lib/components/balance';
	import type { TrackingStatus } from '$lib/balance-presentation';
	import { creditTerms } from '$lib/dashboard-sections';
	import type { OverviewPosition } from '$lib/server/payloads';
	import { m } from '$lib/paraglide/messages.js';

	let {
		position,
		tracking,
		format,
	}: {
		position: OverviewPosition;
		/** The authoritative tracking read, for the Net Balance source note. */
		tracking: TrackingStatus;
		format: (value: number) => string;
	} = $props();

	const unreconciled = $derived(position.availableToSpend < 0);
	// `null` means "tracking is off, so there is no account breakdown to show",
	// which is a different statement from a breakdown that happens to be zero.
	const hasBreakdown = $derived(position.moneyHeld !== null);
	const creditDebt = $derived(position.creditDebt ?? 0);
	const creditInFavor = $derived(position.creditInFavor ?? 0);

	// Debt and credit in the User's favour are independent sums, and both can be
	// non-zero at once: one card owed while another is overpaid. The sentence has
	// to name both terms when they are, because Net Balance already includes
	// both — saying "held minus debt = net" while a positive credit balance sits
	// inside `net` states arithmetic that does not add up on screen.
	const terms = $derived(creditTerms(position));
	const sumSentence = $derived.by(() => {
		if (position.moneyHeld === null || terms === null) return null;
		const held = format(position.moneyHeld);
		const net = format(position.netBalance);
		switch (terms) {
			case 'mixed':
				return m.dashboard_position_sum_mixed({
					held,
					favor: format(creditInFavor),
					debt: format(creditDebt),
					net,
				});
			case 'debt':
				return m.dashboard_position_sum_debt({ held, credit: format(creditDebt), net });
			case 'in-favor':
				return m.dashboard_position_sum_in_favor({ held, credit: format(creditInFavor), net });
			default:
				return m.dashboard_position_sum_settled({ held, net });
		}
	});
</script>

<section class="space-y-4" aria-labelledby="dashboard-position-heading">
	<div class="flex flex-wrap items-center justify-between gap-2">
		<h2 id="dashboard-position-heading" class="font-heading text-lg font-semibold">
			{m.dashboard_position_title()}
		</h2>
		<Button href="/accounts" size="sm" variant="ghost" class="text-xs">
			{m.dashboard_open_accounts()}
			<ArrowRight class="size-3.5" aria-hidden="true" />
		</Button>
	</div>

	<div class="grid grid-cols-1 gap-4 lg:grid-cols-3">
		<Card.Root class="lg:col-span-1">
			<Card.Header class="gap-1">
				<Card.Description>{m.dashboard_net_balance()}</Card.Description>
				<Card.Title class="font-heading text-4xl font-bold tabular-nums tracking-tight">
					{format(position.netBalance)}
				</Card.Title>
			</Card.Header>
			<Card.Content class="space-y-2">
				<!-- Which formula produced this total is a fact about the User's
				     setup, never inferred from the total itself. -->
				<NetBalanceNote {tracking} />
				{#if sumSentence}
					<p class="text-xs text-muted-foreground">{sumSentence}</p>
				{:else}
					<p class="text-xs text-muted-foreground">
						{m.dashboard_position_breakdown_unavailable()}
					</p>
				{/if}
			</Card.Content>
		</Card.Root>

		<div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:col-span-2">
			<a
				href="/boxes"
				class="rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
			>
				<Card.Root class="h-full transition-colors hover:bg-muted/40">
					<Card.Header class="gap-1">
						<Card.Description>{m.balance_in_boxes()}</Card.Description>
						<Card.Title class="text-2xl font-bold tabular-nums">{format(position.inBoxes)}</Card.Title>
					</Card.Header>
					<Card.Content class="flex items-center justify-between gap-2">
						<p class="text-xs text-muted-foreground">{m.dashboard_in_boxes_description()}</p>
						<ArrowRight class="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
					</Card.Content>
				</Card.Root>
			</a>

			<Card.Root class={unreconciled ? 'ring-destructive/60' : ''}>
				<Card.Header class="gap-1">
					<Card.Description>{m.balance_available_to_spend()}</Card.Description>
					<Card.Title
						class="text-2xl font-bold tabular-nums {unreconciled ? 'text-destructive' : ''}"
					>
						{format(position.availableToSpend)}
					</Card.Title>
				</Card.Header>
				<Card.Content class="space-y-1">
					<p class="text-xs text-muted-foreground">
						{m.dashboard_available_arithmetic({
							net: format(position.netBalance),
							boxes: format(position.inBoxes),
							available: format(position.availableToSpend),
						})}
					</p>
					<!-- Explicitly not a cash forecast. -->
					<p class="text-xs text-muted-foreground">{m.balance_available_note()}</p>
				</Card.Content>
			</Card.Root>
		</div>
	</div>

	{#if hasBreakdown}
		<div class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
			<Card.Root>
				<Card.Header class="gap-1">
					<Card.Description>{m.dashboard_position_money_held()}</Card.Description>
					<Card.Title class="text-xl font-bold tabular-nums">
						{format(position.moneyHeld ?? 0)}
					</Card.Title>
				</Card.Header>
				<Card.Content>
					<p class="text-xs text-muted-foreground">
						{m.dashboard_position_money_held_description()}
					</p>
				</Card.Content>
			</Card.Root>

			<!-- Credit debt and credit in the User's favour are separate sums, not
			     one signed figure: they are different situations, and netting them
			     would hide a card that is owed while another is overpaid. Each one
			     that exists gets its own card, so neither is ever the footnote of
			     the other. -->
			{#if creditDebt > 0}
				<Card.Root>
					<Card.Header class="gap-1">
						<Card.Description>{m.balance_credit_debt()}</Card.Description>
						<Card.Title class="text-xl font-bold tabular-nums">{format(creditDebt)}</Card.Title>
					</Card.Header>
					<Card.Content>
						<p class="text-xs text-muted-foreground">{m.dashboard_credit_debt_note()}</p>
					</Card.Content>
				</Card.Root>
			{/if}

			{#if creditInFavor > 0}
				<Card.Root>
					<Card.Header class="gap-1">
						<Card.Description>{m.balance_credit_in_favor()}</Card.Description>
						<Card.Title class="text-xl font-bold tabular-nums">{format(creditInFavor)}</Card.Title>
					</Card.Header>
					<Card.Content>
						<p class="text-xs text-muted-foreground">{m.dashboard_credit_in_favor_note()}</p>
					</Card.Content>
				</Card.Root>
			{/if}

			{#if creditDebt === 0 && creditInFavor === 0}
				<Card.Root>
					<Card.Header class="gap-1">
						<Card.Description>{m.balance_credit_settled()}</Card.Description>
						<Card.Title class="text-xl font-bold tabular-nums">{format(0)}</Card.Title>
					</Card.Header>
				</Card.Root>
			{/if}

			<!-- Capacity, not money. `null` means no limit is configured anywhere,
			     and $0.00 would claim the User has none left. -->
			{#if position.availableCredit !== null && !position.creditLimitsPartial}
				<Card.Root>
					<Card.Header class="gap-1">
						<Card.Description>{m.balance_available_credit()}</Card.Description>
						<Card.Title class="text-xl font-bold tabular-nums">
							{format(position.availableCredit)}
						</Card.Title>
					</Card.Header>
					<Card.Content>
						<p class="text-xs text-muted-foreground">{m.balance_available_credit_note()}</p>
					</Card.Content>
				</Card.Root>
			{/if}
		</div>

		<!-- A card's limit that could not be read costs the capacity figure its
		     completeness and nothing else. The balances above were all read
		     successfully and are stated without qualification. -->
		{#if position.creditLimitsPartial}
			<SectionUnavailable compact title={m.dashboard_position_credit_limits_partial()} />
		{/if}

		{#if position.accounts.length > 0}
			<ul class="divide-y rounded-xl border text-sm">
				{#each position.accounts as account (account.id)}
					<li>
						<a
							href="/accounts/{account.id}"
							class="flex items-center justify-between gap-3 px-3 py-2 transition-colors hover:bg-muted/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
						>
							<span class="min-w-0 flex-1 truncate">{account.name}</span>
							<span
								class="shrink-0 tabular-nums {account.balance < 0 ? 'text-money-negative' : ''}"
							>
								{format(account.balance)}
							</span>
						</a>
					</li>
				{/each}
			</ul>
		{/if}
	{/if}
</section>
