<script lang="ts">
	import { superForm } from 'sveltekit-superforms';
	import { zod4Client } from 'sveltekit-superforms/adapters';
	import { z } from 'zod';
	import { toast } from 'svelte-sonner';
	import { enhance as kitEnhance } from '$app/forms';
	import { invalidateAll } from '$app/navigation';
	import { submitWithAdaptiveConfirm } from '$lib/components/adaptive-confirm';
	import * as Dialog from '$lib/components/ui/dialog';
	import * as Form from '$lib/components/ui/form';
	import * as Alert from '$lib/components/ui/alert';
	import * as Empty from '$lib/components/ui/empty';
	import * as Card from '$lib/components/ui/card';
	import * as Table from '$lib/components/ui/table';
	import { Input } from '$lib/components/ui/input';
	import { Textarea } from '$lib/components/ui/textarea';
	import { Button } from '$lib/components/ui/button';
	import { Badge } from '$lib/components/ui/badge';
	import { NativeSelect } from '$lib/components/native-select';
	import { NativeDatePicker } from '$lib/components/native-date-picker';
	import { dateInTimeZone, mxnFormatter, shortDateFormatter } from '$lib/formatting';
	import { DataTableWrapper } from '$lib/components/ui/data-table';
	import type { ColumnDef, Row } from '@tanstack/table-core';
	import { m } from '$lib/paraglide/messages.js';
	import type { PageData } from './$types';

	const debtSchema = z.object({
		id: z.coerce.number().optional(),
		contactId: z.coerce.number().positive(m.validation_contact_required()),
		description: z.string().min(1, m.validation_description_required()),
		totalAmount: z.coerce.number().positive(m.validation_total_amount_positive()),
		createdAt: z.string().min(1, m.validation_date_required()),
	});

	const bulkPaymentSchema = z.object({
		contactId: z.coerce.number().positive(m.validation_contact_required()),
		totalAmount: z.coerce.number().positive(m.validation_amount_positive()),
		paymentDate: z.string().min(1, m.validation_payment_date_required()),
		categoryId: z.coerce.number().positive(m.validation_category_required()),
		notes: z.string().optional(),
	});

	type Debt = {
		id: number;
		contactId: number | null;
		contactName: string | null;
		description: string;
		totalAmount: number;
		totalPaid: number;
		remaining: number;
		status: string;
		createdAt: string | null;
	};

	type BulkPaymentItem = {
		debtId: number;
		description: string;
		applied: number;
		remaining: number;
		debtStatus: string;
	};

	type BulkResult = {
		contactId: number;
		contactName: string | null;
		totalAmount: number;
		totalApplied: number;
		totalUnused: number;
		payments: BulkPaymentItem[];
	};

	type Category = { id: number; name: string; type: string };
	type DebtorSummary = { key: string; name: string; outstanding: number; debtCount: number };

	let { data }: { data: PageData } = $props();

	let dialogOpen = $state(false);
	let bulkDialogOpen = $state(false);
	let bulkResult = $state<BulkResult | null>(null);
	let editMode = $state(false);
	let deleteTargetId = $state<number | null>(null);
	let deleteForm = $state<HTMLFormElement | null>(null);

	const sf = superForm(data.form, {
		validators: zod4Client(debtSchema),
		onResult({ result }) {
			if (result.type === 'success') {
				dialogOpen = false;
				toast.success(editMode ? m.debts_updated() : m.debts_created());
			} else if (result.type === 'failure') {
				const msg = (result.data as Record<string, unknown> | undefined)?.form as
					| { message?: string }
					| undefined;
				if (msg?.message) toast.error(msg.message);
			}
		},
	});
	const { form, enhance, submitting, message } = sf;

	const bulkSf = superForm(data.bulkForm, {
		validators: zod4Client(bulkPaymentSchema),
		onResult({ result }) {
			if (result.type === 'success') {
				const r = (result.data as Record<string, unknown> | undefined)?.bulkResult as BulkResult | undefined;
				if (r) {
					bulkResult = r;
					toast.success(m.debts_bulk_payment_applied({
						amount: fmt.format(r.totalApplied),
						count: r.payments.length,
					}));
					invalidateAll();
				}
			} else if (result.type === 'failure') {
				const msg = (result.data as Record<string, unknown> | undefined)?.bulkForm as
					| { message?: string }
					| undefined;
				if (msg?.message) toast.error(msg.message);
				else toast.error(m.debts_bulk_payment_failed());
			}
		},
	});
	const { form: bulkForm, enhance: bulkEnhance, submitting: bulkSubmitting } = bulkSf;

	const ingressCategories = $derived(
		(data.categories as Category[])
			.filter((c) => c.type === 'INGRESS' || c.type === 'BOTH')
			.sort((a, b) => a.name.localeCompare(b.name)),
	);

	function openCreate() {
		editMode = false;
		sf.reset({
			data: { contactId: 0, description: '', totalAmount: 0, createdAt: dateInTimeZone(data.preferences.timeZone) },
		});
		dialogOpen = true;
	}

	function openBulkPayment() {
		bulkResult = null;
		bulkSf.reset({
			data: {
				contactId: 0,
				totalAmount: 0,
				paymentDate: dateInTimeZone(data.preferences.timeZone),
				categoryId: 0,
				notes: '',
			},
		});
		bulkDialogOpen = true;
	}

	function openEdit(debt: Debt) {
		editMode = true;
		const existing = debt.createdAt ? debt.createdAt.split('T')[0] : dateInTimeZone(data.preferences.timeZone);
		form.set({
			id: debt.id,
			contactId: debt.contactId ?? 0,
			description: debt.description,
			totalAmount: debt.totalAmount,
			createdAt: existing,
		});
		dialogOpen = true;
	}

	async function openDelete(debt: Debt) {
		deleteTargetId = debt.id;
		await submitWithAdaptiveConfirm(deleteForm, {
			title: m.debts_delete_title(),
			description: `${m.delete_confirm_debt_prefix()} ${debt.description}${m.delete_confirm_suffix()}`,
			confirmLabel: m.common_delete(),
			cancelLabel: m.common_cancel(),
			destructive: true,
		});
	}

	const fmt = $derived(mxnFormatter(data.preferences.locale));
	const shortDate = $derived(shortDateFormatter(data.preferences.locale));
	const debtorSummaries = $derived.by(() => {
		const summaries = new Map<string, DebtorSummary>();
		for (const debt of data.debts as Debt[]) {
			if (debt.status !== 'ACTIVE' || debt.remaining <= 0) continue;
			const key = debt.contactId === null ? `debt-${debt.id}` : `contact-${debt.contactId}`;
			const current = summaries.get(key);
			if (current) {
				current.outstanding += debt.remaining;
				current.debtCount += 1;
			} else {
				summaries.set(key, {
					key,
					name: debt.contactName ?? m.contact_number({ id: debt.contactId ?? debt.id }),
					outstanding: debt.remaining,
					debtCount: 1,
				});
			}
		}
		return [...summaries.values()].sort((a, b) => b.outstanding - a.outstanding || a.name.localeCompare(b.name));
	});

	// Desktop columns. `accessorFn` keeps the searchable text in the row model so
	// the wrapper's global filter matches debtor and description, and sorting on
	// the money columns compares numbers rather than formatted strings.
	const debtColumns: ColumnDef<Debt>[] = [
		{
			id: 'debtor',
			header: m.debts_column_debtor(),
			accessorFn: (d) => d.contactName ?? m.contact_number({ id: d.contactId ?? d.id }),
		},
		{ id: 'description', header: m.common_description(), accessorFn: (d) => d.description },
		{ id: 'total', header: m.common_total(), accessorFn: (d) => d.totalAmount },
		{ id: 'paid', header: m.common_paid(), accessorFn: (d) => d.totalPaid },
		{ id: 'remaining', header: m.common_remaining(), accessorFn: (d) => d.remaining },
		{ id: 'status', header: m.debts_filter_status(), accessorFn: (d) => debtStatusLabel(d.status) },
		{ id: 'actions', header: '', enableSorting: false },
	];

	// Status is a separate control rather than part of the search box: it is a
	// closed set, and typing "activa" should not be the way to narrow to it.
	let statusFilter = $state<'ALL' | 'ACTIVE' | 'PAID'>('ALL');
	const visibleDebts = $derived(
		statusFilter === 'ALL'
			? data.debts
			: data.debts.filter((d) => (statusFilter === 'PAID' ? d.status === 'PAID' : d.status !== 'PAID')),
	);
	const statusFilters = $derived([
		{ value: 'ALL' as const, label: m.debts_filter_all() },
		{ value: 'ACTIVE' as const, label: m.debts_filter_active() },
		{ value: 'PAID' as const, label: m.debts_filter_paid() },
	]);

	const statusBadgeVariant: Record<string, 'warning' | 'success'> = {
		ACTIVE: 'warning',
		PAID: 'success',
	};

	function debtStatusLabel(status: string): string {
		if (status === 'ACTIVE') return m.status_active();
		if (status === 'PAID') return m.status_paid();
		return status;
	}
</script>

<svelte:head><title>{m.debts_title()} · Keenti</title></svelte:head>

<div class="space-y-6">
	<div class="flex items-center justify-between">
		<div>
			<h1 class="text-2xl font-semibold tracking-tight">{m.debts_title()}</h1>
			<p class="text-sm text-muted-foreground">{m.debts_description()}</p>
		</div>
		<div class="flex gap-2">
			<Button variant="outline" onclick={openBulkPayment}>{m.debts_bulk_payment()}</Button>
			<Button onclick={openCreate}>{m.debts_new()}</Button>
		</div>
	</div>

	{#if debtorSummaries.length > 0}
		<section class="space-y-3" aria-labelledby="debtor-outstanding-title">
			<div>
				<h2 id="debtor-outstanding-title" class="text-lg font-semibold">{m.debts_outstanding_by_debtor()}</h2>
				<p class="text-sm text-muted-foreground">{m.debts_outstanding_by_debtor_description()}</p>
			</div>
			<div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
				{#each debtorSummaries as debtor (debtor.key)}
					<Card.Root>
						<Card.Content class="space-y-2 py-4">
							<p class="truncate font-semibold">{debtor.name}</p>
							<p class="text-sm text-muted-foreground">{debtor.debtCount === 1 ? m.debts_one_active_debt() : m.debts_active_debt_count({ count: debtor.debtCount })}</p>
							<div class="border-t pt-2">
								<p class="text-xs font-medium text-muted-foreground">{m.debts_amount_owed_to_you()}</p>
								<p class="text-xl font-bold tabular-nums text-amber-600 dark:text-amber-400">{fmt.format(debtor.outstanding)}</p>
							</div>
						</Card.Content>
					</Card.Root>
				{/each}
			</div>
		</section>
	{/if}

	{#if data.debts.length === 0}
		<Empty.Root class="border">
			<Empty.Title>{m.debts_empty_title()}</Empty.Title>
			<Empty.Description>{m.debts_empty_description()}</Empty.Description>
		</Empty.Root>
	{:else}
		<DataTableWrapper
			columns={debtColumns}
			data={visibleDebts}
			pageSize={12}
			filterPlaceholder={m.debts_search_placeholder()}
			mobileCard={debtCard}
			{toolbar}
			cellRenders={{ debtor: debtorCell, total: totalCell, paid: paidCell, remaining: remainingCell, status: statusCell }}
			actionCell={rowActions}
		/>
	{/if}

{#snippet toolbar()}
	<div class="flex items-center gap-1 rounded-lg border p-0.5" role="group" aria-label={m.debts_filter_status()}>
		{#each statusFilters as option (option.value)}
			<Button
				variant={statusFilter === option.value ? 'secondary' : 'ghost'}
				size="sm"
				onclick={() => (statusFilter = option.value)}
				aria-pressed={statusFilter === option.value}
			>
				{option.label}
			</Button>
		{/each}
	</div>
{/snippet}

{#snippet debtorCell(row: Row<Debt>)}
	<a href="/debts/{row.original.id}" class="font-medium hover:underline">
		{row.original.contactName ?? m.contact_number({ id: row.original.contactId ?? row.original.id })}
	</a>
{/snippet}

{#snippet totalCell(row: Row<Debt>)}<span class="tabular-nums">{fmt.format(row.original.totalAmount)}</span>{/snippet}

{#snippet paidCell(row: Row<Debt>)}
	<span class="tabular-nums text-money-positive">{fmt.format(row.original.totalPaid)}</span>
{/snippet}

{#snippet remainingCell(row: Row<Debt>)}
	<span class="font-medium tabular-nums {row.original.status === 'PAID' ? 'text-money-positive' : 'text-amber-600 dark:text-amber-400'}">
		{fmt.format(row.original.remaining)}
	</span>
{/snippet}

{#snippet statusCell(row: Row<Debt>)}
	<Badge variant={statusBadgeVariant[row.original.status]}>{debtStatusLabel(row.original.status)}</Badge>
{/snippet}

{#snippet rowActions(row: Row<Debt>)}
	<!-- Same hover-reveal treatment the Movimientos table uses. -->
	<div class="flex justify-end gap-1 opacity-0 transition-opacity focus-within:opacity-100 group-hover/row:opacity-100">
		<Button variant="outline" size="sm" href="/debts/{row.original.id}">{m.debts_payments()}</Button>
		<Button variant="ghost" size="sm" onclick={() => openEdit(row.original)}>{m.common_edit()}</Button>
		<Button
			variant="ghost"
			size="sm"
			class="text-destructive hover:bg-destructive/10 hover:text-destructive"
			onclick={() => openDelete(row.original)}
		>
			{m.common_delete()}
		</Button>
	</div>
{/snippet}

{#snippet debtCard(row: Row<Debt>)}
	{@const debt = row.original}
			<Card.Root class="flex flex-col relative">
				<a
					href="/debts/{debt.id}"
					class="absolute inset-0 rounded-[inherit] focus:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
					aria-label={m.debts_view_aria({ name: debt.contactName ?? m.contact_number({ id: debt.contactId ?? debt.id }) })}
				></a>
				<Card.Content class="flex flex-1 flex-col space-y-3">
					<div class="flex items-start justify-between gap-2">
						<div class="min-w-0">
							<p class="font-semibold text-base truncate">
								{debt.contactName ?? m.contact_number({ id: debt.contactId ?? debt.id })}
							</p>
							<p class="text-sm text-muted-foreground truncate mt-0.5">{debt.description}</p>
						</div>
						<Badge class="shrink-0" variant={statusBadgeVariant[debt.status]}>{debtStatusLabel(debt.status)}</Badge>
					</div>

					<div class="space-y-1 text-sm">
						{#if debt.createdAt}
							<div class="flex justify-between">
								<span class="text-muted-foreground">{m.common_date()}</span>
								<span class="font-medium tabular-nums">
									{shortDate.format(new Date(debt.createdAt))}
								</span>
							</div>
						{/if}
						<div class="flex justify-between">
							<span class="text-muted-foreground">{m.common_total()}</span>
							<span class="font-medium">{fmt.format(debt.totalAmount)}</span>
						</div>
						<div class="flex justify-between">
							<span class="text-muted-foreground">{m.common_paid()}</span>
							<span class="font-medium text-money-positive">
								{fmt.format(debt.totalPaid)}
							</span>
						</div>
						<div class="flex justify-between border-t pt-1">
							<span class="text-muted-foreground font-medium">{m.common_remaining()}</span>
							<span
								class="font-bold {debt.status === 'PAID'
									? 'text-money-positive'
									: 'text-amber-600 dark:text-amber-400'}"
							>
								{fmt.format(debt.remaining)}
							</span>
						</div>
					</div>

					<div class="flex gap-2 mt-auto pt-1 relative z-1">
						<Button variant="default" size="sm" class="flex-1" href="/debts/{debt.id}">
							{m.debts_payments()}
						</Button>
						<Button variant="outline" size="sm" onclick={() => openEdit(debt)}>{m.common_edit()}</Button>
						<Button variant="ghost" size="sm" class="ml-auto text-destructive hover:bg-destructive/10 hover:text-destructive" onclick={() => openDelete(debt)}>{m.common_delete()}</Button>
					</div>
				</Card.Content>
			</Card.Root>
{/snippet}
</div>

<!-- Create / Edit dialog -->
<Dialog.Root bind:open={dialogOpen}>
	<Dialog.Content class="sm:max-w-md">
		<Dialog.Header>
			<Dialog.Title>{editMode ? m.debts_edit_title() : m.debts_new_title()}</Dialog.Title>
			<Dialog.Description>
				{editMode ? m.debts_edit_description() : m.debts_new_description()}
			</Dialog.Description>
		</Dialog.Header>

		{#if $message}
			<Alert.Root variant="destructive">
				<Alert.Description>{$message}</Alert.Description>
			</Alert.Root>
		{/if}

		<form
			method="POST"
			action={editMode ? '?/update' : '?/create'}
			use:enhance
			class="grid gap-4"
		>
			{#if editMode && $form.id}
				<input type="hidden" name="id" value={$form.id} />
			{/if}

			<Form.Field form={sf} name="contactId">
				<Form.Control>
					{#snippet children({ props })}
						{@const { name: fieldName, ...triggerProps } = props}
						<Form.Label>{m.debts_debtor_contact()}</Form.Label>
						<NativeSelect
							name={fieldName}
							value={$form.contactId > 0 ? String($form.contactId) : ''}
							onValueChange={(v) => { $form.contactId = v ? Number(v) : 0; }}
							placeholder={m.common_select_contact()}
							items={[...data.contacts].sort((a, b) => a.name.localeCompare(b.name)).map(c => ({ value: String(c.id), label: c.name }))}
							{...triggerProps}
						/>
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Form.Field form={sf} name="description">
				<Form.Control>
					{#snippet children({ props })}
						<Form.Label>{m.common_description()}</Form.Label>
						<Textarea
							{...props}
							bind:value={$form.description}
							rows={3}
							placeholder={m.debts_placeholder_description()}
						/>
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Form.Field form={sf} name="totalAmount">
				<Form.Control>
					{#snippet children({ props })}
						<Form.Label>{m.debts_total_amount_mxn()}</Form.Label>
						<Input {...props} type="number" step="0.01" min="0.01" bind:value={$form.totalAmount} />
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Form.Field form={sf} name="createdAt">
				<Form.Control>
					{#snippet children({ props })}
						{@const { name: fieldName, ...triggerProps } = props}
						<Form.Label>{m.debts_date()}</Form.Label>
						<NativeDatePicker
							name={fieldName}
							value={$form.createdAt}
							onValueChange={(v) => { $form.createdAt = v; }}
							{...triggerProps}
						/>
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Dialog.Footer>
				<Button type="button" variant="outline" onclick={() => (dialogOpen = false)}>{m.common_cancel()}</Button>
				<Button type="submit" disabled={$submitting}>
					{$submitting ? m.common_saving() : editMode ? m.common_update() : m.common_create()}
				</Button>
			</Dialog.Footer>
		</form>
	</Dialog.Content>
</Dialog.Root>

<!-- Bulk payment dialog -->
<Dialog.Root bind:open={bulkDialogOpen}>
	<Dialog.Content class="sm:max-w-lg">
		<Dialog.Header>
			<Dialog.Title>{m.debts_bulk_payment()}</Dialog.Title>
			<Dialog.Description>{m.debts_bulk_description()}</Dialog.Description>
		</Dialog.Header>

		{#if bulkResult}
			<!-- Result summary -->
			<div class="space-y-4">
				<div class="grid grid-cols-3 gap-3 text-sm">
					<div>
						<p class="text-muted-foreground">{m.common_total()}</p>
						<p class="font-semibold">{fmt.format(bulkResult.totalAmount)}</p>
					</div>
					<div>
						<p class="text-muted-foreground">{m.common_applied()}</p>
						<p class="font-semibold text-money-positive">{fmt.format(bulkResult.totalApplied)}</p>
					</div>
					<div>
						<p class="text-muted-foreground">{m.common_unused()}</p>
						<p class="font-semibold {bulkResult.totalUnused > 0 ? 'text-amber-600 dark:text-amber-400' : ''}">{fmt.format(bulkResult.totalUnused)}</p>
					</div>
				</div>

				<div class="rounded-md border overflow-hidden">
					<Table.Root>
						<Table.Header class="bg-muted/50">
							<Table.Row>
								<Table.Head>{m.common_debt()}</Table.Head>
								<Table.Head class="text-right">{m.common_applied()}</Table.Head>
								<Table.Head class="text-right">{m.common_remaining()}</Table.Head>
								<Table.Head>{m.common_status()}</Table.Head>
							</Table.Row>
						</Table.Header>
						<Table.Body>
							{#each bulkResult.payments as item (item.debtId)}
								<Table.Row>
									<Table.Cell class="text-sm max-w-45 truncate">{item.description}</Table.Cell>
									<Table.Cell class="text-right tabular-nums text-money-positive font-medium">
										{fmt.format(item.applied)}
									</Table.Cell>
									<Table.Cell class="text-right tabular-nums">
										{fmt.format(item.remaining)}
									</Table.Cell>
									<Table.Cell>
										<Badge variant={item.debtStatus === 'PAID' ? 'success' : 'warning'} class="text-xs">
											{debtStatusLabel(item.debtStatus)}
										</Badge>
									</Table.Cell>
								</Table.Row>
							{/each}
						</Table.Body>
					</Table.Root>
				</div>

				<Dialog.Footer>
					<Button onclick={() => (bulkDialogOpen = false)}>{m.common_close()}</Button>
					<Button variant="outline" onclick={openBulkPayment}>{m.debts_new_bulk_payment()}</Button>
				</Dialog.Footer>
			</div>
		{:else}
			<form method="POST" action="?/bulkPayment" use:bulkEnhance class="grid gap-4">
				<Form.Field form={bulkSf} name="contactId">
					<Form.Control>
						{#snippet children({ props })}
							{@const { name: fieldName, ...triggerProps } = props}
							<Form.Label>{m.debts_contact_debtor()}</Form.Label>
							<NativeSelect
								name={fieldName}
								value={$bulkForm.contactId > 0 ? String($bulkForm.contactId) : ''}
								onValueChange={(v) => { $bulkForm.contactId = v ? Number(v) : 0; }}
								placeholder={m.common_select_contact()}
								items={[...data.contacts].sort((a, b) => a.name.localeCompare(b.name)).map(c => ({ value: String(c.id), label: c.name }))}
								{...triggerProps}
							/>
						{/snippet}
					</Form.Control>
					<Form.FieldErrors />
				</Form.Field>

				<div class="grid gap-4 sm:grid-cols-2">
					<Form.Field form={bulkSf} name="totalAmount">
						<Form.Control>
							{#snippet children({ props })}
								<Form.Label>{m.debts_total_amount_mxn()}</Form.Label>
								<Input {...props} type="number" step="0.01" min="0.01" bind:value={$bulkForm.totalAmount} />
							{/snippet}
						</Form.Control>
						<Form.FieldErrors />
					</Form.Field>

					<Form.Field form={bulkSf} name="paymentDate">
						<Form.Control>
							{#snippet children({ props })}
								{@const { name: fieldName, ...triggerProps } = props}
								<Form.Label>{m.debts_payment_date()}</Form.Label>
								<NativeDatePicker
									name={fieldName}
									value={$bulkForm.paymentDate}
									onValueChange={(v) => { $bulkForm.paymentDate = v; }}
									{...triggerProps}
								/>
							{/snippet}
						</Form.Control>
						<Form.FieldErrors />
					</Form.Field>
				</div>

				<Form.Field form={bulkSf} name="categoryId">
					<Form.Control>
						{#snippet children({ props })}
							{@const { name: fieldName, ...triggerProps } = props}
							<Form.Label>{m.common_ingress_category()}</Form.Label>
							<NativeSelect
								name={fieldName}
								value={$bulkForm.categoryId > 0 ? String($bulkForm.categoryId) : ''}
								onValueChange={(v) => { $bulkForm.categoryId = v ? Number(v) : 0; }}
								placeholder={m.common_select_category()}
								items={ingressCategories.map(c => ({ value: String(c.id), label: c.name }))}
								{...triggerProps}
							/>
						{/snippet}
					</Form.Control>
					<Form.FieldErrors />
				</Form.Field>

				{#if data.accountTracking.active}
					<Form.Field form={bulkSf} name="accountId">
						<Form.Control>
							{#snippet children({ props })}
								{@const { name: fieldName, ...triggerProps } = props}
								<Form.Label>{m.debts_receiving_account()}</Form.Label>
								<NativeSelect name={fieldName} value={$bulkForm.accountId ? String($bulkForm.accountId) : ''} onValueChange={(v) => { $bulkForm.accountId = v ? Number(v) : ''; }} placeholder={m.transfer_select_account()} items={data.accounts.map(account => ({ value: String(account.id), label: account.name }))} {...triggerProps} />
							{/snippet}
						</Form.Control>
						<Form.FieldErrors />
					</Form.Field>
				{/if}

				<Form.Field form={bulkSf} name="notes">
					<Form.Control>
						{#snippet children({ props })}
							<Form.Label>{m.common_notes()} <span class="text-muted-foreground">{m.common_optional()}</span></Form.Label>
							<Textarea
								{...props}
								bind:value={$bulkForm.notes}
								rows={2}
								placeholder={m.debts_placeholder_notes()}
							/>
						{/snippet}
					</Form.Control>
					<Form.FieldErrors />
				</Form.Field>

				<Dialog.Footer>
					<Button type="button" variant="outline" onclick={() => (bulkDialogOpen = false)}>{m.common_cancel()}</Button>
					<Button type="submit" disabled={$bulkSubmitting || data.accountTracking.setupRequired}>
						{$bulkSubmitting ? m.common_processing() : m.debts_apply_bulk_payment()}
					</Button>
				</Dialog.Footer>
			</form>
		{/if}
	</Dialog.Content>
</Dialog.Root>

<form
	bind:this={deleteForm}
	method="POST"
	action="?/delete"
	class="hidden"
	aria-hidden="true"
	use:kitEnhance={async () => {
		return async ({ result, update }) => {
			if (result.type === 'success') {
				deleteTargetId = null;
				toast.success(m.debts_trashed());
				await update();
			} else {
				const msg =
					(result as { data?: { message?: string } }).data?.message ?? m.debts_delete_failed();
				toast.error(msg);
			}
		};
	}}
>
	<input type="hidden" name="id" value={deleteTargetId} />
</form>
