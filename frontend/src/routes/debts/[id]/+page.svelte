<script lang="ts">
	import { superForm } from 'sveltekit-superforms';
	import { zod4Client } from 'sveltekit-superforms/adapters';
	import { z } from 'zod';
	import { toast } from 'svelte-sonner';
	import { invalidateAll } from '$app/navigation';
	import * as Form from '$lib/components/ui/form';
	import * as Card from '$lib/components/ui/card';
	import * as Table from '$lib/components/ui/table';
	import { Input } from '$lib/components/ui/input';
	import { Textarea } from '$lib/components/ui/textarea';
	import { Button } from '$lib/components/ui/button';
	import { Badge } from '$lib/components/ui/badge';
	import { Progress } from '$lib/components/ui/progress';
	import { NativeSelect } from '$lib/components/native-select';
	import { NativeDatePicker } from '$lib/components/native-date-picker';
	import { formatDateOnly, mxnFormatter, dateInTimeZone } from '$lib/formatting';
	import { debtDirection } from '$lib/debts';
	import { BoxAllocationEditor } from '$lib/components/transactions';
	import { SectionUnavailable } from '$lib/components/section-status';
	import { boxAllocationSchema } from '$lib/schemas/transaction';
	import {
		allocationTotal,
		amountToCents,
		hasAtMostTwoDecimalPlaces,
		type BoxAllocationInput,
	} from '$lib/types/transactions';
	import { sectionValue } from '$lib/types/section';
	import { m } from '$lib/paraglide/messages.js';
	import type { PageData } from './$types';

	const paymentSchema = z.object({
		amount: z.coerce.number().positive(m.validation_amount_positive()),
		paymentDate: z.string().min(1, m.validation_payment_date_required()),
		categoryId: z.coerce.number().positive(m.validation_category_required()),
		notes: z.string().optional(),
		boxFunding: z.array(boxAllocationSchema).default([]),
	});

	type DebtPayment = {
		id: number;
		debtId: number;
		amount: number;
		paymentDate: string;
		transactionId: number | null;
		notes: string | null;
		createdAt: string;
	};

	type Category = { id: number; name: string; type: string };

	let { data }: { data: PageData } = $props();

	const fmt = $derived(mxnFormatter(data.preferences.locale));

	const statusBadgeVariant: Record<string, 'warning' | 'success'> = {
		ACTIVE: 'warning',
		PAID: 'success',
	};

	const paidPercent = $derived(
		data.debt.totalAmount > 0
			? Math.min(100, Math.round((data.debt.totalPaid / data.debt.totalAmount) * 100))
			: 0,
	);

	const isPaid = $derived(data.debt.status === 'PAID');

	// The Debt's Direction decides what a payment does: money owed to the User
	// comes in, money the User owes goes out (ADR-0023). Everything on this card
	// that names a Direction follows from it.
	const direction = $derived(debtDirection(data.debt.direction));
	const owedByUser = $derived(direction === 'EGRESS');

	type RecordedPayment = { debtId: number; paymentId: number | null; amount: number; transactionId: number | null };

	// The Transaction that saving created, kept on the page after the toast is
	// gone so the User can still follow it.
	let recordedPayment = $state<RecordedPayment | null>(null);

	const sf = superForm(data.form, {
		dataType: 'json',
		validators: zod4Client(paymentSchema),
		onResult({ result }) {
			if (result.type === 'success') {
				recordedPayment =
					((result.data as Record<string, unknown> | undefined)?.recordedPayment as
						| RecordedPayment
						| undefined) ?? null;
				toast.success(m.debts_payment_recorded());
				invalidateAll();
			} else if (result.type === 'failure') {
				const msg = (result.data as Record<string, unknown> | undefined)?.form as
					| { message?: string }
					| undefined;
				recordedPayment = null;
				if (msg?.message) toast.error(msg.message);
				else toast.error(m.debts_payment_record_failed());
			}
		},
	});

	const { form, errors, enhance, submitting } = sf;

	const today = $derived(dateInTimeZone(data.preferences.timeZone));
	// Editing funding needs Available to Spend to project against; without it the
	// editor is withheld rather than shown with a guessed limit.
	const availableBalance = $derived(sectionValue(data.balanceSummary));

	function setBoxFunding(allocations: BoxAllocationInput[]) {
		$form.boxFunding = allocations;
	}

	// The same conditions the editor already renders inline, gathered so an
	// unpayable allocation cannot be submitted just because a message is hidden.
	const fundingInvalid = $derived.by(() => {
		if (!owedByUser || $form.boxFunding.length === 0) return false;
		if ($form.paymentDate > today) return true;
		if (
			$form.boxFunding.some(
				(allocation) => allocation.amount <= 0 || !hasAtMostTwoDecimalPlaces(allocation.amount),
			)
		) {
			return true;
		}
		if (amountToCents(allocationTotal($form.boxFunding)) > amountToCents($form.amount)) return true;
		return $form.boxFunding.some((allocation) => {
			const balance = data.boxes.find((box) => box.id === allocation.boxId)?.balance ?? 0;
			return amountToCents(allocation.amount) > amountToCents(balance);
		});
	});

	// Money coming in is not spending, so a receivable's payment never carries
	// funding — including any left behind by a direction that changed underneath.
	$effect(() => {
		if (!owedByUser && $form.boxFunding.length > 0) setBoxFunding([]);
	});

	const paymentCategories = $derived(
		(data.categories as Category[])
			.filter((c) => c.type === direction || c.type === 'BOTH')
			.sort((a, b) => a.name.localeCompare(b.name)),
	);

	function debtStatusLabel(status: string): string {
		if (status === 'ACTIVE') return m.status_active();
		if (status === 'PAID') return m.status_paid();
		return status;
	}
</script>

<svelte:head><title>{data.debt.contactName ?? (data.debt.contactId != null ? m.contact_number({ id: data.debt.contactId }) : m.entity_contact())} · {m.debts_title()} · Keenti</title></svelte:head>

<div class="mx-auto max-w-3xl space-y-6">
	<!-- Back link -->
	<Button variant="link" href="/debts" class="h-auto p-0 text-muted-foreground hover:text-foreground">
		{m.common_back_to_debts()}
	</Button>

	<!-- Header card -->
	<Card.Root>
		<Card.Content class="space-y-4">
			<div class="flex flex-wrap items-start justify-between gap-3">
				<div class="min-w-0">
					<h1 class="text-2xl font-semibold tracking-tight">
						{data.debt.contactName ??
							(data.debt.contactId != null
								? m.contact_number({ id: data.debt.contactId })
								: m.entity_contact())}
					</h1>
					<p class="text-sm text-muted-foreground mt-0.5">{data.debt.description}</p>
				</div>
				<div class="flex shrink-0 items-center gap-2">
					<Badge variant="outline" class={owedByUser ? 'text-money-negative' : 'text-amber-600 dark:text-amber-400'}>
						{owedByUser ? m.debts_badge_you_owe() : m.debts_badge_owes_you()}
					</Badge>
					<Badge variant={statusBadgeVariant[data.debt.status]}>{debtStatusLabel(data.debt.status)}</Badge>
				</div>
			</div>

			<!-- Balance breakdown -->
			<div class="grid grid-cols-3 gap-4 text-sm">
				<div>
					<p class="text-muted-foreground">{m.common_total()}</p>
					<p class="text-lg font-semibold">{fmt.format(data.debt.totalAmount)}</p>
				</div>
				<div>
					<p class="text-muted-foreground">{m.common_paid()}</p>
					<p class="text-lg font-semibold text-money-positive">
						{fmt.format(data.debt.totalPaid)}
					</p>
				</div>
				<div>
					<p class="text-muted-foreground">{m.common_remaining()}</p>
					<p class="text-lg font-semibold {owedByUser ? 'text-money-negative' : 'text-amber-600 dark:text-amber-400'}">
						{fmt.format(data.debt.remaining)}
					</p>
				</div>
			</div>

			<!-- Progress bar -->
			<div class="space-y-1">
				<div class="flex justify-between text-xs text-muted-foreground">
					<span>{m.common_progress()}</span>
					<span>{paidPercent}%</span>
				</div>
				<!-- The track stays neutral; only the primitive's indicator fills.
				     A coloured track reads as 100% at 0%. -->
				<Progress value={paidPercent} class="h-2" />
			</div>
		</Card.Content>
	</Card.Root>

	<!-- Payment history card -->
	<Card.Root>
		<Card.Content class="space-y-4">
			<h2 class="font-semibold text-base">{m.debts_payment_history()}</h2>

			{#if data.payments.length === 0}
				<p class="text-sm text-muted-foreground">{m.debts_no_payments()}</p>
			{:else}
				<div class="rounded-md border overflow-hidden">
					<Table.Root>
						<Table.Header class="bg-muted/50">
							<Table.Row>
								<Table.Head>{m.common_date()}</Table.Head>
								<Table.Head class="text-right">{m.common_amount()}</Table.Head>
								<Table.Head>{m.common_notes()}</Table.Head>
								<Table.Head class="text-right">{m.common_transaction()}</Table.Head>
							</Table.Row>
						</Table.Header>
						<Table.Body>
							{#each data.payments as payment (payment.id)}
								<Table.Row>
									<Table.Cell class="tabular-nums">{formatDateOnly((payment as DebtPayment).paymentDate, data.preferences.locale)}</Table.Cell>
									<Table.Cell class="text-right font-medium tabular-nums">
										{fmt.format((payment as DebtPayment).amount)}
									</Table.Cell>
									<Table.Cell class="text-muted-foreground">
										{(payment as DebtPayment).notes ?? '—'}
									</Table.Cell>
									<Table.Cell class="text-right">
										{#if (payment as DebtPayment).transactionId}
											<a
												class="font-mono text-xs underline underline-offset-4 hover:text-foreground"
												href="/transactions/{(payment as DebtPayment).transactionId}"
												aria-label={m.debts_transaction_link_aria({ id: (payment as DebtPayment).transactionId ?? 0 })}
											>
												#{(payment as DebtPayment).transactionId}
											</a>
										{:else}
											<span class="text-xs text-muted-foreground">—</span>
										{/if}
									</Table.Cell>
								</Table.Row>
							{/each}
						</Table.Body>
					</Table.Root>
				</div>
			{/if}
		</Card.Content>
	</Card.Root>

	<!-- Record payment card -->
	<Card.Root>
		<Card.Content class="space-y-4">
			<div class="flex items-center justify-between">
				<h2 class="font-semibold text-base">{m.debts_record_payment_title()}</h2>
				{#if isPaid}
					<span class="text-xs text-muted-foreground">{m.debts_fully_paid()}</span>
				{/if}
			</div>

			<form method="POST" action="?/recordPayment" use:enhance class="space-y-4">
				<fieldset disabled={isPaid || $submitting} class="contents">
					<div class="grid gap-4 sm:grid-cols-2">
						<Form.Field form={sf} name="amount">
							<Form.Control>
								{#snippet children({ props })}
									<Form.Label>{m.common_amount_mxn()}</Form.Label>
									<Input
										{...props}
										type="number"
										step="0.01"
										min="0.01"
										max={data.debt.remaining}
										bind:value={$form.amount}
									/>
								{/snippet}
							</Form.Control>
							<Form.FieldErrors />
						</Form.Field>

						<Form.Field form={sf} name="paymentDate">
							<Form.Control>
								{#snippet children({ props })}
									{@const { name: fieldName, ...triggerProps } = props}
									<Form.Label>{m.debts_payment_date()}</Form.Label>
									<NativeDatePicker
										name={fieldName}
										value={$form.paymentDate}
										onValueChange={(v) => { $form.paymentDate = v; }}
										{...triggerProps}
									/>
								{/snippet}
							</Form.Control>
							<Form.FieldErrors />
						</Form.Field>
					</div>

					<Form.Field form={sf} name="categoryId">
						<Form.Control>
							{#snippet children({ props })}
								{@const { name: fieldName, ...triggerProps } = props}
								<Form.Label>{owedByUser ? m.common_egress_category() : m.common_ingress_category()}</Form.Label>
								<NativeSelect
									name={fieldName}
									value={$form.categoryId > 0 ? String($form.categoryId) : ''}
									onValueChange={(v) => { $form.categoryId = v ? Number(v) : 0; }}
									placeholder={m.common_select_category()}
									items={paymentCategories.map(c => ({ value: String(c.id), label: c.name }))}
									{...triggerProps}
								/>
							{/snippet}
						</Form.Control>
						{#if $errors.categoryId}
							<p class="text-destructive text-sm">{$errors.categoryId}</p>
						{/if}
					</Form.Field>

					{#if data.accountTracking.active}
						<Form.Field form={sf} name="accountId">
							<Form.Control>
								{#snippet children({ props })}
									{@const { name: fieldName, ...triggerProps } = props}
									<Form.Label>{owedByUser ? m.debts_paying_account() : m.debts_receiving_account()}</Form.Label>
									<NativeSelect name={fieldName} value={$form.accountId ? String($form.accountId) : ''} onValueChange={(v) => { $form.accountId = v ? Number(v) : ''; }} placeholder={m.transfer_select_account()} items={data.accounts.map(account => ({ value: String(account.id), label: account.name }))} {...triggerProps} />
								{/snippet}
							</Form.Control>
							<Form.FieldErrors />
						</Form.Field>
					{/if}

					<Form.Field form={sf} name="notes">
						<Form.Control>
							{#snippet children({ props })}
								<Form.Label>{m.common_notes()} <span class="text-muted-foreground">{m.common_optional()}</span></Form.Label>
								<Textarea
									{...props}
									bind:value={$form.notes}
									rows={2}
									placeholder={m.debts_placeholder_payment_notes()}
								/>
							{/snippet}
						</Form.Control>
						<Form.FieldErrors />
					</Form.Field>

					{#if owedByUser}
						{#if availableBalance === null}
							<SectionUnavailable
								compact
								title={m.section_balance_unavailable()}
								description={m.section_box_funding_unavailable()}
							/>
						{:else}
							<BoxAllocationEditor
								kind="funding"
								boxes={data.boxes}
								allocations={$form.boxFunding}
								onChange={setBoxFunding}
								transactionAmount={$form.amount}
								transactionDate={$form.paymentDate}
								{today}
								availableBefore={availableBalance.availableToSpend}
								locale={data.preferences.locale}
								categoryName={data.categories.find((category) => category.id === $form.categoryId)?.name ?? null}
								disabled={isPaid}
							/>
						{/if}
					{/if}

					<p class="text-sm text-muted-foreground">
						{owedByUser ? m.debts_payment_creates_expense() : m.debts_payment_creates_income()}
					</p>

					{#if recordedPayment && recordedPayment.debtId === data.debt.id}
						<div class="rounded-md border border-money-positive/40 bg-money-positive/5 p-3 text-sm" role="status">
							{#if recordedPayment.transactionId}
								<p>
									{owedByUser
										? m.debts_payment_created_expense_transaction({ amount: fmt.format(recordedPayment.amount) })
										: m.debts_payment_created_transaction({ amount: fmt.format(recordedPayment.amount) })}
								</p>
								<a
									class="font-medium underline underline-offset-4"
									href="/transactions/{recordedPayment.transactionId}"
									aria-label={m.debts_transaction_link_aria({ id: recordedPayment.transactionId })}
								>
									{m.debts_payment_view_transaction()}
								</a>
							{:else}
								<p>{m.debts_payment_transaction_missing({ amount: fmt.format(recordedPayment.amount) })}</p>
								<a class="font-medium underline underline-offset-4" href="/transactions">
									{m.debts_payment_view_transactions()}
								</a>
							{/if}
						</div>
					{/if}

					<Button type="submit" disabled={isPaid || $submitting || fundingInvalid || data.accountTracking.setupRequired} class="w-full sm:w-auto">
						{$submitting ? m.common_recording() : m.common_record_payment()}
					</Button>
				</fieldset>
			</form>
		</Card.Content>
	</Card.Root>
</div>
