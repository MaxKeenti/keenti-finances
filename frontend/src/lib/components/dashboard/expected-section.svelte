<script lang="ts">
	/**
	 * Section 4 — money recorded as owed to the User.
	 *
	 * Explicitly not received. These totals are never added to Net Balance, In
	 * Boxes or Available to Spend, and the note under the heading says so rather
	 * than leaving the reader to infer it from the layout.
	 *
	 * What counts, and what does not:
	 *
	 * - An `ACTIVE` Debt with something still outstanding is expected money.
	 * - A `PENDING` Payment Record belonging to a Subscription Member is
	 *   expected money. It stays *awaiting* however old its billing date is:
	 *   no contribution due date or grace period is agreed anywhere in the
	 *   contract, so "late" would be invented policy (decision D2).
	 * - A Personal Subscription's own record (`memberId = null`) is the Owner's
	 *   own charge, not money another person owes, and never appears here.
	 * - A `PAID` record with no linked Transaction is still received. A missing
	 *   link is a missing link, not an unpaid contribution.
	 *
	 * Every row links to the record it came from, so a figure can be traced to
	 * the Debt or Subscription behind it.
	 */
	import * as Card from '$lib/components/ui/card';
	import { Button } from '$lib/components/ui/button';
	import { SectionUnavailable } from '$lib/components/section-status';
	import { expectedEmptiness } from '$lib/dashboard-sections';
	import { formatDateOnly } from '$lib/formatting';
	import type { OverviewExpected } from '$lib/server/payloads';
	import { m } from '$lib/paraglide/messages.js';

	let {
		expected,
		format,
		locale,
	}: {
		expected: OverviewExpected;
		format: (value: number) => string;
		locale: string | undefined;
	} = $props();

	// `null` when the contribution read failed; never coerced to a number.
	const contributionsTotal = $derived(
		expected.contributionsAvailable ? expected.contributionsOutstanding : null,
	);

	// "Nothing is owed to you" is a positive claim, and only a complete read can
	// make it. When the contribution read failed, its count is zero because no
	// rows arrived — not because none exist — so the empty state stays hidden
	// and the unavailable notice speaks instead.
	const emptiness = $derived(expectedEmptiness(expected));
</script>

<section class="space-y-3" aria-labelledby="dashboard-expected-heading">
	<h2 id="dashboard-expected-heading" class="font-heading text-lg font-semibold">
		{m.dashboard_expected_title()}
	</h2>
	<p class="text-sm text-muted-foreground">{m.dashboard_expected_note()}</p>

	<!-- Unread contributions come with a Retry. Without one the notice is just a
	     disclaimer under a total that looks complete. -->
	{#if expected.partial || !expected.contributionsAvailable}
		<SectionUnavailable
			compact
			title={m.section_expected_partial()}
			description={expected.contributionsAvailable
				? m.dashboard_expected_partial()
				: m.dashboard_expected_contributions_unavailable()}
		/>
	{/if}

	{#if emptiness === 'empty'}
		<p class="rounded-md border border-dashed px-3 py-2 text-sm text-muted-foreground">
			{m.dashboard_expected_none()}
		</p>
	{:else if emptiness === 'populated'}
		<div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
			{#if expected.debtCount > 0}
				<Card.Root>
					<Card.Header class="gap-1">
						<Card.Description>{m.debts_amount_owed_to_you()}</Card.Description>
						<Card.Title class="text-2xl font-bold tabular-nums">
							{format(expected.debtsOutstanding)}
						</Card.Title>
					</Card.Header>
					<Card.Content class="space-y-1">
						<ul class="divide-y text-sm">
							{#each expected.debts as debt (debt.debtId)}
								<li>
									<a
										href="/debts/{debt.debtId}"
										class="flex items-center justify-between gap-3 py-2 transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
									>
										<span class="min-w-0 flex-1 truncate">
											{debt.contactName ?? debt.description}
										</span>
										<span class="shrink-0 tabular-nums">{format(debt.remaining)}</span>
									</a>
								</li>
							{/each}
						</ul>
						{#if expected.debtsTruncated}
							<p class="text-xs text-muted-foreground">
								{m.dashboard_expected_truncated({
									shown: expected.debts.length,
									total: expected.debtCount,
								})}
							</p>
						{/if}
					</Card.Content>
					<Card.Footer>
						<Button href="/debts" size="sm" variant="outline">{m.nav_debts()}</Button>
					</Card.Footer>
				</Card.Root>
			{/if}

			<!-- The total is rendered only when one actually arrived. `null` is the
			     backend saying the read failed, and formatting it as $0.00 would
			     turn a missing answer into "nobody owes you anything". -->
			{#if expected.contributionCount > 0 && contributionsTotal !== null}
				<Card.Root>
					<Card.Header class="gap-1">
						<Card.Description>{m.dashboard_expected_contributions()}</Card.Description>
						<Card.Title class="text-2xl font-bold tabular-nums">
							{format(contributionsTotal)}
						</Card.Title>
					</Card.Header>
					<Card.Content class="space-y-1">
						<p class="text-xs text-muted-foreground">
							{m.dashboard_expected_contributions_description()}
						</p>
						<ul class="divide-y text-sm">
							{#each expected.contributions as contribution (contribution.paymentRecordId)}
								<li>
									<a
										href="/subscriptions/{contribution.subscriptionId}"
										class="flex items-center justify-between gap-3 py-2 transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
									>
										<span class="min-w-0 flex-1 truncate">
											{contribution.contactName ?? contribution.subscriptionName}
											{#if contribution.billingDate}
												<span class="text-muted-foreground">
													· {formatDateOnly(contribution.billingDate, locale)}
												</span>
											{/if}
										</span>
										<span class="shrink-0 tabular-nums">{format(contribution.amount)}</span>
									</a>
								</li>
							{/each}
						</ul>
						{#if expected.contributionsTruncated}
							<p class="text-xs text-muted-foreground">
								{m.dashboard_expected_truncated({
									shown: expected.contributions.length,
									total: expected.contributionCount,
								})}
							</p>
						{/if}
					</Card.Content>
					<Card.Footer>
						<Button href="/subscriptions" size="sm" variant="outline">
							{m.nav_subscriptions()}
						</Button>
					</Card.Footer>
				</Card.Root>
			{/if}
		</div>
	{/if}
</section>
