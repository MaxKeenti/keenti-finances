<script lang="ts">
	/**
	 * Section 2 — what needs attention (decision D2).
	 *
	 * The read model ships facts: confirmed statement amounts and due dates,
	 * generation cursors, account balances and limits. The labels are derived
	 * here, against the one calendar day the layout captured in the User's own
	 * time zone — the same instant is 7 September in Mexico City and 8 September
	 * in Tokyo, so a due date compared against the browser's day reads "past
	 * due" a day early.
	 *
	 * The dimensions stay apart. Billing generation, a Subscription Member's
	 * contribution and a provider payment are three different facts, and none of
	 * them is compressed into a single paid/unpaid badge. Nothing here writes:
	 * the actions link to the existing workflows, which is where a User
	 * deliberately generates billing or records a payment.
	 */
	import * as Alert from '$lib/components/ui/alert';
	import { Button } from '$lib/components/ui/button';
	import { SectionUnavailable } from '$lib/components/section-status';
	import { attentionEmptiness } from '$lib/dashboard-sections';
	import { AlertTriangle, CalendarClock } from '@lucide/svelte';
	import {
		accountStatementPaymentStatus,
		billingGenerationStatus,
		obligationPriority,
		type DayResolution,
	} from '$lib/obligation-status';
	import { statementAmountLabel, statementStateLabel } from '$lib/statement-labels';
	import { billingGenerationDescription, billingGenerationLabel } from '$lib/subscription-labels';
	import { availableToSpendExplanation } from '$lib/balance-presentation';
	import { ShortfallAlert } from '$lib/components/balance';
	import type { OverviewAttention, OverviewPosition } from '$lib/server/payloads';
	import { m } from '$lib/paraglide/messages.js';

	let {
		attention,
		position,
		today,
		format,
		locale,
	}: {
		attention: OverviewAttention;
		/** `null` when the position section is unavailable; no alert is invented. */
		position: OverviewPosition | null;
		today: DayResolution;
		format: (value: number) => string;
		locale: string | undefined;
	} = $props();

	/** Statements grouped per Credit Financial Account, as decision D2 evaluates them. */
	const byAccount = $derived.by(() => {
		const groups = new Map<number, { accountId: number; accountName: string; statements: typeof attention.statements }>();
		for (const statement of attention.statements) {
			const group = groups.get(statement.accountId);
			if (group) group.statements.push(statement);
			else
				groups.set(statement.accountId, {
					accountId: statement.accountId,
					accountName: statement.accountName,
					statements: [statement],
				});
		}
		return [...groups.values()].map((group) => ({
			...group,
			...accountStatementPaymentStatus({
				statements: group.statements,
				today,
				read: (statement) => statement,
			}),
		}));
	});

	// A covered statement is not an obligation; it stays out of this list.
	const statementRows = $derived(
		byAccount
			.filter((group) => group.status.state !== 'covered')
			.sort((a, b) => obligationPriority(a.status.state) - obligationPriority(b.status.state)),
	);

	// Reconciliation mismatch is an independent review notice: it survives both
	// a covered payment state and the priority selection above, so it is listed
	// from every flagged statement rather than only the selected one.
	const mismatchRows = $derived(
		byAccount.flatMap((group) =>
			group.mismatches.map(({ statement, status }) => ({ group, statement, status })),
		),
	);

	/**
	 * Billing generation, cursor only.
	 *
	 * A caught-up Subscription needs no attention and is omitted. An unreadable
	 * cursor is shown as unavailable rather than assumed caught up — silence
	 * about a cursor is not evidence that Keenti is up to date.
	 */
	const billingRows = $derived(
		attention.billing
			.map((entry) => ({
				entry,
				generation: billingGenerationStatus({
					nextBillingDate: entry.nextBillingDate,
					today,
				}),
			}))
			.filter(({ generation }) => generation.state !== 'caught-up')
			.sort(
				(a, b) => obligationPriority(a.generation.state) - obligationPriority(b.generation.state),
			),
	);

	/** Overdrawn asset accounts and cards past their limit, from the position facts. */
	const accountAlerts = $derived.by(() => {
		if (position === null) return [];
		const alerts: Array<{ id: number; title: string; description: string; href: string }> = [];
		for (const account of position.accounts) {
			if (account.kind !== 'CREDIT' && account.balance < 0) {
				alerts.push({
					id: account.id,
					title: m.warning_account_overdrawn_title({ name: account.name }),
					description: m.warning_account_overdrawn_description({
						amount: format(account.balance),
					}),
					href: `/accounts/${account.id}`,
				});
			}
			// A limit is only breached when one is configured; a card with no
			// configured limit has nothing to breach and gets no alert.
			if (
				account.kind === 'CREDIT' &&
				account.creditLimit !== null &&
				account.balance < -account.creditLimit
			) {
				alerts.push({
					id: account.id,
					title: m.warning_credit_limit_title({ name: account.name }),
					description: m.warning_credit_limit_description(),
					href: `/accounts/${account.id}`,
				});
			}
		}
		return alerts;
	});

	const shortfall = $derived(
		position === null
			? null
			: availableToSpendExplanation({
					netBalance: position.netBalance,
					inBoxes: position.inBoxes,
					availableToSpend: position.availableToSpend,
				}),
	);
	const hasShortfall = $derived(
		shortfall !== null && (shortfall.state === 'over-reserved' || shortfall.state === 'negative-net'),
	);

	/**
	 * "Nothing needs your attention" is the strongest claim on this page, and it
	 * is only true if every read behind it completed.
	 *
	 * A card whose statements failed (`partial`) may owe money nobody can see. An
	 * unavailable position section means no account balance or credit limit was
	 * checked, so no overdrawn or over-limit alert could have been raised. A
	 * truncated list — of statements or of billing cursors — has items that were
	 * dropped for space. In all of those cases
	 * the absence of alerts is the absence of evidence, and the section says what
	 * it could not check instead of pronouncing the User healthy.
	 */
	const emptiness = $derived(
		attentionEmptiness({
			alertCount:
				(hasShortfall ? 1 : 0) +
				statementRows.length +
				mismatchRows.length +
				billingRows.length +
				accountAlerts.length,
			partial:
				attention.partial ||
				attention.statementsTruncated ||
				attention.billingTruncated ||
				(position?.creditLimitsPartial ?? false),
			positionAvailable: position !== null,
		}),
	);
</script>

<section class="space-y-3" aria-labelledby="dashboard-attention-heading">
	<h2 id="dashboard-attention-heading" class="font-heading text-lg font-semibold">
		{m.dashboard_attention_title()}
	</h2>

	<!-- Each gap gets named, and the unread ones get a Retry: a notice with no
	     way to act on it reads as a permanent footnote under a healthy list. -->
	{#if attention.partial || position?.creditLimitsPartial}
		<SectionUnavailable
			compact
			title={m.section_attention_partial()}
			description={m.section_warnings_partial()}
		/>
	{/if}
	<!-- The position section owns account balances and credit limits. Without it
	     no overdrawn or over-limit alert could be raised, and silence here must
	     not be mistaken for a clean set of accounts. -->
	{#if position === null}
		<p class="text-sm text-muted-foreground">{m.dashboard_attention_position_unavailable()}</p>
	{/if}
	{#if attention.statementsTruncated}
		<p class="text-sm text-muted-foreground">{m.dashboard_statements_truncated()}</p>
	{/if}
	{#if attention.billingTruncated}
		<p class="text-sm text-muted-foreground">{m.dashboard_billing_truncated()}</p>
	{/if}

	{#if emptiness === 'empty'}
		<p class="rounded-md border border-dashed px-3 py-2 text-sm text-muted-foreground">
			{m.dashboard_attention_none()}
		</p>
	{/if}

	<!-- Over-reserving and a negative Net Balance are different stories with
	     different remedies; the shared alert tells them apart. -->
	{#if position}
		<ShortfallAlert
			totals={{
				netBalance: position.netBalance,
				inBoxes: position.inBoxes,
				availableToSpend: position.availableToSpend,
			}}
			{format}
		/>
	{/if}

	{#each statementRows as group (group.accountId)}
		<Alert.Root variant={group.status.state === 'outstanding-upcoming' ? 'default' : 'destructive'}>
			<AlertTriangle aria-hidden="true" />
			<Alert.Title>{group.accountName} · {statementStateLabel(group.status.state)}</Alert.Title>
			<Alert.Description class="space-y-1">
				{#if statementAmountLabel(group.status, format, locale)}
					<p>{statementAmountLabel(group.status, format, locale)}</p>
				{/if}
				<!-- The purchases behind it already changed Net Balance. -->
				<p>{m.statement_not_subtracted_note()}</p>
			</Alert.Description>
			<Alert.Action>
				<Button href="/accounts/{group.accountId}" size="sm" variant="outline">
					{m.statement_action_review()}
				</Button>
			</Alert.Action>
		</Alert.Root>
	{/each}

	{#each mismatchRows as { group, statement, status } (statement.statementId)}
		<Alert.Root>
			<AlertTriangle aria-hidden="true" />
			<Alert.Title>{m.balance_mismatch_title({ name: group.accountName })}</Alert.Title>
			<Alert.Description>
				{m.balance_mismatch_description({ amount: format(Math.abs(status.mismatchAmount ?? 0)) })}
			</Alert.Description>
			<Alert.Action>
				<Button href="/accounts/{group.accountId}" size="sm" variant="outline">
					{m.balance_mismatch_action()}
				</Button>
			</Alert.Action>
		</Alert.Root>
	{/each}

	{#each billingRows as { entry, generation } (entry.subscriptionId)}
		<Alert.Root>
			<CalendarClock aria-hidden="true" />
			<Alert.Title>{entry.name} · {billingGenerationLabel(generation.state)}</Alert.Title>
			<Alert.Description class="space-y-1">
				<p>{billingGenerationDescription(generation, locale)}</p>
				<!-- Generation happens on the Subscription page, deliberately. -->
				<p>{m.dashboard_billing_reads_note()}</p>
			</Alert.Description>
			<Alert.Action>
				<Button href="/subscriptions/{entry.subscriptionId}" size="sm" variant="outline">
					{m.dashboard_billing_open()}
				</Button>
			</Alert.Action>
		</Alert.Root>
	{/each}

	{#each accountAlerts as alert (`${alert.id}-${alert.title}`)}
		<Alert.Root variant="destructive">
			<AlertTriangle aria-hidden="true" />
			<Alert.Title>{alert.title}</Alert.Title>
			<Alert.Description>{alert.description}</Alert.Description>
			<Alert.Action>
				<Button href={alert.href} size="sm" variant="outline">{m.dashboard_review_account()}</Button>
			</Alert.Action>
		</Alert.Root>
	{/each}
</section>
