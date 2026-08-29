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
	import { dateInTimeZone, formatDateOnly, mxnFormatter } from '$lib/formatting';
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
	// Without credit settings there is no limit to subtract from, so available
	// credit is unknown rather than zero — rendering $0.00 claimed the User
	// had none left. `null` makes the card show the same "set this up" hint
	// the credit-limit card beside it already uses.
	const availableCredit = $derived(
		data.credit?.settings
			? Math.max(data.credit.settings.creditLimit + data.account.balance, 0)
			: null,
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

	<section class="grid gap-4 sm:grid-cols-3">
		<Card.Root><Card.Header><Card.Description>{data.account.kind === 'CREDIT' ? (data.account.balance > 0 ? m.account_credit_positive() : m.account_debt_current()) : m.account_current_balance()}</Card.Description><Card.Title class="text-2xl tabular-nums"><span class:text-destructive={data.account.kind === 'CREDIT' && data.account.balance < 0}>{balanceLabel}</span></Card.Title></Card.Header></Card.Root>
		<Card.Root><Card.Header><Card.Description>{data.account.kind === 'CREDIT' ? m.account_available_credit() : m.account_opening_balance()}</Card.Description><Card.Title class="text-2xl tabular-nums">{data.account.kind === 'CREDIT' ? (availableCredit === null ? m.account_credit_limit_unset() : fmt.format(availableCredit)) : fmt.format(data.account.openingBalance)}</Card.Title></Card.Header></Card.Root>
		<Card.Root><Card.Header><Card.Description>{m.account_tracking_started()}</Card.Description><Card.Title class="text-2xl">{formatDateOnly(data.account.openingDate, data.preferences.locale)}</Card.Title></Card.Header></Card.Root>
	</section>

	{#if data.credit}
		<section class="grid gap-4 sm:grid-cols-2">
			<Card.Root><Card.Header><Card.Description>{m.account_credit_limit()}</Card.Description><Card.Title class="text-2xl tabular-nums">{data.credit.settings ? fmt.format(data.credit.settings.creditLimit) : m.account_credit_limit_unset()}</Card.Title></Card.Header></Card.Root>
			<Card.Root><Card.Header><Card.Description>{m.account_next_payment()}</Card.Description><Card.Title class="text-2xl">{data.credit.nextStatement ? formatDateOnly(data.credit.nextStatement.dueDate, data.preferences.locale) : m.account_no_payment_due()}</Card.Title></Card.Header>{#if data.credit.nextStatement}<Card.Content><p class="text-sm text-muted-foreground">{m.account_remaining_to_avoid({ remaining: fmt.format(data.credit.nextStatement.outstandingBalance), avoidInterest: fmt.format(Math.max(data.credit.nextStatement.officialAvoidInterest - data.credit.nextStatement.paidAmount, 0)) })}</p></Card.Content>{/if}</Card.Root>
		</section>
		{#if data.credit.currentEstimate}<Card.Root><Card.Header><Card.Description>{m.account_current_estimate({ date: formatDateOnly(data.credit.currentEstimate.periodEnd, data.preferences.locale) })}</Card.Description><Card.Title class="text-2xl tabular-nums">{fmt.format(data.credit.currentEstimate.estimatedBalance)}</Card.Title></Card.Header><Card.Content><p class="text-sm text-muted-foreground">{m.account_estimate_description({ date: formatDateOnly(data.credit.currentEstimate.dueDate, data.preferences.locale) })}</p></Card.Content></Card.Root>{/if}

		<CreditAccountPanel account={data.account} detail={data.credit} locale={data.preferences.locale} />

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
