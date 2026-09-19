<script lang="ts">
	import { enhance } from '$app/forms';
	import { untrack } from 'svelte';
	import Archive from '@lucide/svelte/icons/archive';
	import ArrowLeft from '@lucide/svelte/icons/arrow-left';
	import Palette from '@lucide/svelte/icons/palette';
	import RotateCcw from '@lucide/svelte/icons/rotate-ccw';
	import { CreditAccountPanel } from '$lib/components/accounts';
	import { ColorPicker } from '$lib/components/color-picker';
	import { NativeDatePicker } from '$lib/components/native-date-picker';
	import { NativeSelect } from '$lib/components/native-select';
	import * as Alert from '$lib/components/ui/alert';
	import { Button } from '$lib/components/ui/button';
	import * as Card from '$lib/components/ui/card';
	import * as Dialog from '$lib/components/ui/dialog';
	import { Input } from '$lib/components/ui/input';
	import { Label } from '$lib/components/ui/label';
	import { SectionUnavailable } from '$lib/components/section-status';
	import { dateInTimeZone, formatDateOnly, mxnFormatter } from '$lib/formatting';
	import { availableCredit, creditMagnitude, creditPosition } from '$lib/balance-presentation';
	import { accountStatementPaymentStatus, isOutstandingStatement } from '$lib/obligation-status';
	import { statementAmountLabel, statementStateLabel } from '$lib/statement-labels';
	import { sectionValue } from '$lib/types/section';
	import { m } from '$lib/paraglide/messages.js';
	import type { PageData } from './$types';

	let { data }: { data: PageData } = $props();
	let lifecycleError = $state('');
	let appearanceOpen = $state(false);
	let accountHue = $state(untrack(() => data.account.hue));
	let selectedPurchaseId = $state('');
	let firstInstallmentDate = $state(untrack(() => dateInTimeZone(data.preferences.timeZone)));
	const fmt = $derived(mxnFormatter(data.preferences.locale));
	const accountKindLabel = $derived(({
		CASH: m.account_kind_cash(), DEBIT: m.account_kind_debit(), CHECKING: m.account_kind_checking(), SAVINGS: m.account_kind_savings(), CREDIT: m.account_kind_credit(),
	} as Record<string, string>)[data.account.kind] ?? data.account.kind);
	const balanceLabel = $derived(data.account.kind === 'CREDIT' && data.account.balance < 0 ? fmt.format(Math.abs(data.account.balance)) : fmt.format(data.account.balance));

	// The three credit figures are deliberately separate facts and never
	// summed: the signed balance is what this card owes (already inside Net
	// Balance), available credit is limit-derived capacity outside every total,
	// and a confirmed statement's outstanding payment is its own obligation.
	const position = $derived(creditPosition(data.account.balance));
	const positionAmount = $derived(creditMagnitude(data.account.balance));
	// `null` from the section means the settings could not be read; `null`
	// inside an ok section means none are configured. Only the latter offers
	// the "set this up" hint — and neither shows $0.00 available credit, which
	// would claim the User has none left.
	const creditSettings = $derived(
		data.credit ? sectionValue(data.credit.settings) : null,
	);
	const creditSettingsUnavailable = $derived(data.credit?.settings.status === 'unavailable');
	const availableCreditAmount = $derived(
		creditSettings ? availableCredit(creditSettings.creditLimit, data.account.balance) : null,
	);

	// One captured instant per render, resolved in the User's own zone, so this
	// page and the dashboard agree about which calendar day it is.
	const today = $derived(data.obligationToday);
	const statements = $derived(data.credit ? sectionValue(data.credit.statements) : null);
	const statementPayment = $derived(
		accountStatementPaymentStatus({
			statements,
			today,
			read: (statement) => statement,
			// "Estimated statement, not confirmed" describes the estimate, so it
			// is only said once that separate read succeeded.
			estimateAvailable: data.credit?.estimateAvailable === true,
		}),
	);
	const needsStatementSchedule = $derived(data.credit?.statementScheduleUnconfigured && statements?.length === 0);
	const outstanding = $derived(isOutstandingStatement(statementPayment.status.state));
	// Prefills the existing neutral Transfer workflow on the Accounts page with
	// this card as the destination. It opens a form; nothing is recorded until
	// the User confirms it there.
	const payCardHref = $derived(
		`/accounts?payCard=${data.account.id}` +
			(statementPayment.status.outstanding !== null && statementPayment.status.outstanding > 0
				? `&amount=${statementPayment.status.outstanding}`
				: ''),
	);
	const purchaseItems = $derived(data.credit?.creditTransactions.map((transaction) => ({
		value: String(transaction.id),
		label: `${transaction.transactionDate} · ${transaction.description ?? m.account_expense()} · ${fmt.format(transaction.amount)}`,
	})) ?? []);

	function enhanceLifecycle() {
		return async ({ result, update }: { result: { type: string; data?: { message?: string } }; update: () => Promise<void> }) => {
			lifecycleError = result.type === 'failure' ? result.data?.message ?? m.account_update_error() : '';
			await update();
		};
	}

	function enhanceAppearance() {
		return async ({ result, update }: { result: { type: string; data?: { message?: string } }; update: () => Promise<void> }) => {
			if (result.type === 'success') appearanceOpen = false;
			lifecycleError = result.type === 'failure' ? result.data?.message ?? m.account_update_error() : '';
			await update();
		};
	}
</script>

<svelte:head><title>{data.account.name} · {m.accounts_title()} · Keenti</title></svelte:head>

<div class="space-y-6">
	<header
		class="flex flex-wrap items-start justify-between gap-4 rounded-xl border bg-gradient-to-br from-[oklch(0.97_0.025_var(--account-hue))] to-card p-5 dark:from-[oklch(0.27_0.035_var(--account-hue))]"
		style:--account-hue={String(data.account.hue)}
	>
		<div class="space-y-2">
			<Button href="/accounts" variant="ghost" size="sm"><ArrowLeft />{m.accounts_title()}</Button>
			<div><p class="text-sm text-muted-foreground">{accountKindLabel}{data.account.archived ? ` · ${m.account_archived()}` : ''}</p><h1 class="text-2xl font-semibold tracking-tight">{data.account.name}</h1></div>
		</div>
		<div class="flex flex-wrap gap-2">
			<Button type="button" variant="outline" onclick={() => (appearanceOpen = true)}><Palette />{m.account_personalize()}</Button>
			{#if data.account.archived}
				<form method="POST" action="?/restore" use:enhance={enhanceLifecycle}><Button type="submit"><RotateCcw />{m.account_restore()}</Button></form>
			{:else}
				<form method="POST" action="?/archive" use:enhance={enhanceLifecycle}><Button type="submit" variant="outline" disabled={data.account.balance !== 0}><Archive />{m.account_archive()}</Button></form>
			{/if}
		</div>
	</header>

	{#if lifecycleError}<Alert.Root variant="destructive"><Alert.Description>{lifecycleError}</Alert.Description></Alert.Root>{/if}
	{#if !data.account.archived && data.account.balance !== 0}<Alert.Root><Alert.Description>{m.account_archive_zero_required()}</Alert.Description></Alert.Root>{/if}

	<!-- The Credit Financial Account's position, its limit-derived capacity, and
	     its confirmed statement obligation are three separate labeled facts. The
	     page used to show "Current debt" even when the balance was in the
	     User's favour, and to leave the statement payment implicit. -->
	<section class="grid gap-4 sm:grid-cols-3">
		<Card.Root>
			<Card.Header>
				<Card.Description>
					{#if data.account.kind !== 'CREDIT'}
						{m.account_current_balance()}
					{:else if position === 'debt'}
						{m.credit_label_debt()}
					{:else if position === 'in-favor'}
						{m.credit_label_in_favor()}
					{:else if position === 'settled'}
						{m.credit_label_settled()}
					{:else}
						{m.credit_balance_unknown()}
					{/if}
				</Card.Description>
				<Card.Title class="text-2xl tabular-nums">
					<span class:text-destructive={position === 'debt'}>
						{data.account.kind === 'CREDIT'
							? positionAmount === null
								? m.credit_balance_unknown()
								: fmt.format(positionAmount)
							: balanceLabel}
					</span>
				</Card.Title>
			</Card.Header>
			{#if data.account.kind === 'CREDIT' && position !== 'unknown'}
				<Card.Content>
					<p class="text-xs text-muted-foreground">
						{position === 'in-favor' ? m.credit_in_favor_note() : m.credit_debt_note()}
					</p>
				</Card.Content>
			{/if}
		</Card.Root>

		<Card.Root>
			<Card.Header>
				<Card.Description>{data.account.kind === 'CREDIT' ? m.credit_label_available() : m.account_opening_balance()}</Card.Description>
				{#if data.account.kind !== 'CREDIT'}
					<Card.Title class="text-2xl tabular-nums">{fmt.format(data.account.openingBalance)}</Card.Title>
				{:else if availableCreditAmount !== null}
					<Card.Title class="text-2xl tabular-nums">{fmt.format(availableCreditAmount)}</Card.Title>
				{:else if !creditSettingsUnavailable}
					<Card.Title class="text-base font-medium">{m.account_credit_limit_unset()}</Card.Title>
				{/if}
			</Card.Header>
			<Card.Content>
				{#if data.account.kind === 'CREDIT' && creditSettingsUnavailable}
					<SectionUnavailable title={m.credit_label_available()} compact />
				{:else if data.account.kind === 'CREDIT'}
					{#if creditSettings}
						<p class="text-xs text-muted-foreground">
							{m.account_credit_limit()}: {fmt.format(creditSettings.creditLimit)}
						</p>
					{/if}
					<p class="text-xs text-muted-foreground">{m.credit_available_note()}</p>
				{/if}
			</Card.Content>
		</Card.Root>

		<Card.Root><Card.Header><Card.Description>{m.account_tracking_started()}</Card.Description><Card.Title class="text-2xl">{formatDateOnly(data.account.openingDate, data.preferences.locale)}</Card.Title></Card.Header></Card.Root>
	</section>

	{#if data.credit}
		<!-- The confirmed statement payment: the backend's outstandingBalance,
		     never the current signed balance, the available credit, the minimum,
		     or the avoid-interest amount. Those stay separately labeled. -->
		<Card.Root>
			<Card.Header>
				<Card.Description>{m.credit_label_outstanding_statement()}</Card.Description>
				<Card.Title class="text-xl">{needsStatementSchedule ? m.credit_schedule_needed_title() : statementStateLabel(statementPayment.status.state)}</Card.Title>
			</Card.Header>
			<Card.Content class="space-y-3">
				{#if needsStatementSchedule}
					<p class="text-sm text-muted-foreground">{m.credit_schedule_needed_description()}</p>
				{:else if statementPayment.status.state === 'unavailable'}
					<SectionUnavailable title={m.statement_status_unavailable()} />
				{:else if statementPayment.status.state === 'estimated-only'}
					<p class="text-sm text-muted-foreground">{m.statement_status_estimated_description()}</p>
					{#if data.credit.currentEstimate}
						<p class="text-2xl font-semibold tabular-nums">{fmt.format(data.credit.currentEstimate.estimatedBalance)}</p>
						<p class="text-sm text-muted-foreground">{m.account_current_estimate({ date: formatDateOnly(data.credit.currentEstimate.periodEnd, data.preferences.locale) })} · {m.account_estimate_description({ date: formatDateOnly(data.credit.currentEstimate.dueDate, data.preferences.locale) })}</p>
					{/if}
				{:else}
					{#if statementAmountLabel(statementPayment.status, (value) => fmt.format(value), data.preferences.locale)}
						<p class="text-lg font-semibold tabular-nums">
							{statementAmountLabel(statementPayment.status, (value) => fmt.format(value), data.preferences.locale)}
						</p>
					{/if}
					{#if statementPayment.statement}
						<p class="text-sm text-muted-foreground">
							{m.statement_minimum_and_avoid({
								minimum: fmt.format(statementPayment.statement.officialMinimumPayment),
								avoidInterest: fmt.format(statementPayment.statement.officialAvoidInterest),
							})}
						</p>
					{/if}
					<!-- A bank-issued snapshot, kept as recorded. A later change to
					     prior activity flags a mismatch rather than rewriting it. -->
					<p class="text-xs text-muted-foreground">{m.statement_snapshot_note()}</p>
					<p class="text-xs text-muted-foreground">{m.statement_not_subtracted_note()}</p>
					{#if outstanding && !data.account.archived}
						<!-- Paying the card is a Transfer, not an expense: it goes
						     through the existing neutral Transfer workflow, which
						     allocates oldest-unpaid-statement first. -->
						<div class="space-y-1">
							<Button href={payCardHref} variant="outline">{m.credit_pay_card()}</Button>
							<p class="text-xs text-muted-foreground">{m.credit_pay_card_description()}</p>
						</div>
					{/if}
				{/if}

			</Card.Content>
		</Card.Root>

		<Card.Root>
			<Card.Header><Card.Title>{m.account_msi_title()}</Card.Title><Card.Description>{m.account_msi_description()}</Card.Description></Card.Header>
			<Card.Content class="space-y-5">
				{#if data.credit.msiPlans.length}
					<div class="divide-y rounded-lg border">{#each data.credit.msiPlans as plan}<div class="flex flex-wrap items-center justify-between gap-3 p-4 text-sm"><div><p>{m.account_msi_summary({ count: plan.installmentCount, date: plan.firstInstallmentDate })}</p>{#if !plan.active}<p class="text-muted-foreground">{plan.endReason?.toLocaleLowerCase(data.preferences.locale)}</p>{/if}</div><span>{m.account_msi_amounts({ installment: fmt.format(plan.installmentAmount), total: fmt.format(plan.purchaseAmount) })}</span>{#if plan.active && !data.account.archived}<div class="flex gap-2"><form method="POST" action="?/endMsiPlan" use:enhance={enhanceLifecycle}><input type="hidden" name="planId" value={plan.id} /><input type="hidden" name="reason" value="COMPLETED" /><Button type="submit" size="sm" variant="outline">{m.account_complete()}</Button></form><form method="POST" action="?/endMsiPlan" use:enhance={enhanceLifecycle}><input type="hidden" name="planId" value={plan.id} /><input type="hidden" name="reason" value="CANCELLED" /><Button type="submit" size="sm" variant="destructive">{m.common_cancel()}</Button></form></div>{/if}</div>{/each}</div>
				{:else}<p class="text-sm text-muted-foreground">{m.account_no_msi()}</p>{/if}

				{#if !data.account.archived}
					<form method="POST" action="?/createMsiPlan" use:enhance={enhanceLifecycle} class="grid gap-4 border-t pt-5 md:grid-cols-2 lg:grid-cols-4">
						<div class="grid gap-2"><Label for="msi-purchase">{m.account_msi_purchase()}</Label><NativeSelect id="msi-purchase" name="transactionId" value={selectedPurchaseId} onValueChange={(value) => (selectedPurchaseId = value)} placeholder={purchaseItems.length ? m.account_select_purchase() : m.account_no_eligible_purchase()} items={purchaseItems} required disabled={purchaseItems.length === 0} /></div>
						<div class="grid gap-2"><Label for="msi-count">{m.account_installments()}</Label><Input id="msi-count" name="installmentCount" type="number" min="2" max="60" required placeholder="12" disabled={purchaseItems.length === 0} /></div>
						<div class="grid gap-2"><Label for="msi-first-date">{m.account_first_installment()}</Label><NativeDatePicker id="msi-first-date" name="firstInstallmentDate" value={firstInstallmentDate} onValueChange={(value) => (firstInstallmentDate = value)} disabled={purchaseItems.length === 0} /></div>
						<div class="flex items-end justify-end"><Button type="submit" variant="outline" disabled={purchaseItems.length === 0}>{m.account_create_msi()}</Button></div>
					</form>
				{/if}
			</Card.Content>
		</Card.Root>
	{/if}


	<Card.Root>
		<Card.Header><Card.Title>{m.account_activity_title()}</Card.Title><Card.Description>{m.account_activity_description()}</Card.Description></Card.Header>
		<Card.Content>
			{#if data.activity.length === 0}<p class="text-sm text-muted-foreground">{m.account_no_activity()}</p>{:else}<div class="divide-y rounded-lg border">{#each data.activity as item (item.id)}<div class="flex flex-wrap items-center justify-between gap-3 p-4"><div><p class="font-medium">{item.title}</p><p class="text-sm text-muted-foreground">{item.type === 'TRANSFER' ? m.transfer_title() : m.common_transaction()} · {item.date}{item.detail ? ` · ${item.detail}` : ''}</p></div><span class:text-destructive={item.amount < 0} class="font-medium tabular-nums">{item.amount > 0 ? '+' : '−'}{fmt.format(Math.abs(item.amount))}</span></div>{/each}</div>{/if}
		</Card.Content>
	</Card.Root>
	{#if data.credit}
		<!-- Advanced setup sits below the everyday reading. -->
		<CreditAccountPanel account={data.account} detail={data.credit} locale={data.preferences.locale} {today} />
	{/if}
</div>

<Dialog.Root bind:open={appearanceOpen}>
	<Dialog.Content class="sm:max-w-md">
		<Dialog.Header><Dialog.Title>{m.account_personalize()}</Dialog.Title><Dialog.Description>{m.account_personalize_description()}</Dialog.Description></Dialog.Header>
		<form method="POST" action="?/updateAppearance" use:enhance={enhanceAppearance} class="grid gap-4">
			<input type="hidden" name="hue" value={accountHue} />
			<div class="grid gap-1.5"><span class="text-sm font-medium">{m.common_colour()}</span><ColorPicker name={data.account.name} hue={accountHue} onchange={(hue) => (accountHue = hue)} /></div>
			<Dialog.Footer><Button type="button" variant="outline" onclick={() => (appearanceOpen = false)}>{m.common_cancel()}</Button><Button type="submit">{m.common_save()}</Button></Dialog.Footer>
		</form>
	</Dialog.Content>
</Dialog.Root>
