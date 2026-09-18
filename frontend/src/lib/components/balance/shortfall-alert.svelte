<script lang="ts">
	/**
	 * Explains a negative Available to Spend (decision D1).
	 *
	 * Over-reserving and a negative Net Balance are different situations with
	 * different remedies, and the page used to describe both with one sentence
	 * about money being "short". The breakdown is shown so the User can see
	 * which of the two figures produced it, and a withdrawal is only offered
	 * when some Box actually holds money — suggesting one otherwise sends them
	 * to an action that cannot succeed.
	 *
	 * Nothing here decides that a record is wrong: a confirmed-statement
	 * reconciliation mismatch is a separate notice with its own evidence.
	 */
	import * as Alert from '$lib/components/ui/alert';
	import { Button } from '$lib/components/ui/button';
	import { AlertTriangle } from '@lucide/svelte';
	import { availableToSpendExplanation, type BalanceTotals } from '$lib/balance-presentation';
	import { m } from '$lib/paraglide/messages.js';

	let {
		totals,
		format,
		boxBalances = null,
		/** Where "Review reservations" goes; omitted on the Boxes page itself. */
		reviewHref = '/boxes',
	}: {
		totals: BalanceTotals | null;
		format: (value: number) => string;
		boxBalances?: number[] | null;
		reviewHref?: string | null;
	} = $props();

	const explanation = $derived(availableToSpendExplanation(totals, { boxBalances }));
	const overReserved = $derived(explanation.state === 'over-reserved');
</script>

{#if totals && (explanation.state === 'over-reserved' || explanation.state === 'negative-net')}
	<Alert.Root variant="destructive">
		<AlertTriangle aria-hidden="true" />
		<Alert.Title>
			{overReserved ? m.balance_over_reserved_title() : m.balance_negative_net_title()}
		</Alert.Title>
		<Alert.Description class="space-y-1">
			<p>
				{#if overReserved}
					{m.balance_over_reserved_description({
						net: format(totals.netBalance),
						boxes: format(totals.inBoxes),
						amount: format(explanation.shortfall ?? 0),
					})}
				{:else}
					<!-- The Net Balance's own magnitude, not the Available shortfall:
					     Boxes holding money make the latter larger, and this sentence
					     is about the recorded position. -->
					{m.balance_negative_net_description({ amount: format(explanation.negativeNet ?? 0) })}
				{/if}
			</p>
			<p>
				{explanation.withdrawalPossible
					? m.balance_over_reserved_withdraw_hint()
					: m.balance_no_withdrawal_hint()}
			</p>
		</Alert.Description>
		{#if explanation.withdrawalPossible && reviewHref}
			<Alert.Action>
				<Button href={reviewHref} size="sm" variant="outline">{m.balance_review_boxes()}</Button>
			</Alert.Action>
		{:else if !overReserved}
			<!-- Balances first, not Transactions: a negative recorded position is
			     not by itself evidence of a missing or wrong record, so the offer
			     is to look at what the accounts say rather than to go edit one. -->
			<Alert.Action>
				<Button href="/accounts" size="sm" variant="outline">
					{m.balance_review_accounts()}
				</Button>
			</Alert.Action>
		{/if}
	</Alert.Root>
{/if}
