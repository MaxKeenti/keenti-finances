<script lang="ts">
	import { superForm } from 'sveltekit-superforms';
	import { zod4Client } from 'sveltekit-superforms/adapters';
	import { z } from 'zod';
	import { toast } from 'svelte-sonner';
	import { enhance as kitEnhance } from '$app/forms';
	import { goto } from '$app/navigation';
	import {
		createTable,
		getCoreRowModel,
		type ColumnDef,
		type PaginationState,
		type SortingState,
	} from '@tanstack/table-core';
	import ArrowDownIcon from '@lucide/svelte/icons/arrow-down';
	import ArrowUpIcon from '@lucide/svelte/icons/arrow-up';
	import ArrowUpDownIcon from '@lucide/svelte/icons/arrow-up-down';
	import ChevronLeftIcon from '@lucide/svelte/icons/chevron-left';
	import ChevronRightIcon from '@lucide/svelte/icons/chevron-right';
	import * as Table from '$lib/components/ui/table';
	import * as Dialog from '$lib/components/ui/dialog';
	import * as Form from '$lib/components/ui/form';
	import * as Alert from '$lib/components/ui/alert';
	import * as Empty from '$lib/components/ui/empty';
	import { Input } from '$lib/components/ui/input';
	import { Button } from '$lib/components/ui/button';
	import { NativeSelect } from '$lib/components/native-select';
	import { NativeDatePicker } from '$lib/components/native-date-picker';
	import { CategoryBadge } from '$lib/components/ui/category-badge';
	import * as Card from '$lib/components/ui/card';
	import { m } from '$lib/paraglide/messages.js';
	import type { PageData } from './$types';

	type Transaction = PageData['transactions'][number];
	type TransactionSortBy = PageData['transactionPage']['sortBy'];
	type TransactionSortDirection = PageData['transactionPage']['sortDirection'];

	const transactionSchema = z.object({
		id: z.coerce.number().optional(),
		amount: z.coerce.number().positive(m.validation_amount_positive()),
		direction: z.enum(['INGRESS', 'EGRESS']),
		description: z.string().max(500).optional(),
		transactionDate: z.string().min(1, m.validation_date_required()),
		categoryId: z.coerce.number().min(1, m.validation_category_required()),
		contactId: z.union([z.coerce.number(), z.literal('')]).optional(),
	});

	let { data }: { data: PageData } = $props();

	let dialogOpen = $state(false);
	let deleteDialogOpen = $state(false);
	let editMode = $state(false);
	let deleteTargetId = $state<number | null>(null);
	let deleteTargetDesc = $state('');

	const today = new Date().toISOString().split('T')[0];

	const sf = superForm(data.form, {
		dataType: 'json',
		validators: zod4Client(transactionSchema),
		onResult({ result }) {
			if (result.type === 'success') {
				dialogOpen = false;
				toast.success(editMode ? m.transactions_updated() : m.transactions_created());
			} else if (result.type === 'failure') {
				const msg = (result.data as Record<string, unknown> | undefined)?.form as
					| { message?: string }
					| undefined;
				if (msg?.message) toast.error(msg.message);
			}
		},
	});
	const { form, enhance, submitting, message } = sf;

	function openCreate() {
		editMode = false;
		sf.reset({
			data: {
				amount: 0,
				direction: 'INGRESS',
				description: '',
				transactionDate: today,
				categoryId: data.categories[0]?.id ?? 0,
				contactId: '',
			},
		});
		dialogOpen = true;
	}

	function openEdit(tx: {
		id: number;
		amount: number;
		direction: string;
		description: string | null;
		transactionDate: string;
		categoryId: number;
		contactId: number | null;
	}) {
		editMode = true;
		form.set({
			id: tx.id,
			amount: tx.amount,
			direction: tx.direction as 'INGRESS' | 'EGRESS',
			description: tx.description ?? '',
			transactionDate: tx.transactionDate,
			categoryId: tx.categoryId,
			contactId: tx.contactId ?? '',
		});
		dialogOpen = true;
	}

	function openDelete(tx: { id: number; description: string | null; amount: number; direction: string }) {
		deleteTargetId = tx.id;
		deleteTargetDesc = tx.description || formatAmount(tx.amount, tx.direction as 'INGRESS' | 'EGRESS');
		deleteDialogOpen = true;
	}

	const fmt = new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' });
	const sortItems: { value: TransactionSortBy; label: string }[] = [
		{ value: 'transactionDate', label: m.common_date() },
		{ value: 'amount', label: m.common_amount() },
		{ value: 'description', label: m.common_description() },
		{ value: 'categoryName', label: m.common_category() },
		{ value: 'contactName', label: m.common_contact() },
		{ value: 'direction', label: m.common_direction() },
	];
	const sortDirectionItems: { value: TransactionSortDirection; label: string }[] = [
		{ value: 'desc', label: m.transactions_sort_descending() },
		{ value: 'asc', label: m.transactions_sort_ascending() },
	];
	const pageSizeItems = [10, 25, 50, 100].map((size) => ({
		value: String(size),
		label: m.transactions_page_size({ size }),
	}));
	const tableColumns: ColumnDef<Transaction>[] = [
		{ id: 'transactionDate', accessorKey: 'transactionDate' },
		{ id: 'description', accessorKey: 'description' },
		{ id: 'amount', accessorKey: 'amount' },
		{ id: 'categoryName', accessorKey: 'categoryName' },
		{ id: 'contactName', accessorKey: 'contactName' },
	];

	const pagination = $derived({
		pageIndex: data.transactionPage.pageIndex,
		pageSize: data.transactionPage.pageSize,
	} satisfies PaginationState);
	const sorting = $derived([
		{
			id: data.transactionPage.sortBy,
			desc: data.transactionPage.sortDirection === 'desc',
		},
	] satisfies SortingState);
	const table = $derived(
		createTable<Transaction>({
			data: data.transactions,
			columns: tableColumns,
			getCoreRowModel: getCoreRowModel(),
			manualPagination: true,
			manualSorting: true,
			pageCount: data.transactionPage.totalPages,
			state: { pagination, sorting },
			onStateChange: () => {},
			renderFallbackValue: null,
		}),
	);
	const currentPage = $derived(data.transactionPage.totalPages === 0 ? 0 : data.transactionPage.pageIndex + 1);
	const pageRangeStart = $derived(
		data.transactionPage.totalItems === 0
			? 0
			: data.transactionPage.pageIndex * data.transactionPage.pageSize + 1,
	);
	const pageRangeEnd = $derived(
		Math.min(
			(data.transactionPage.pageIndex + 1) * data.transactionPage.pageSize,
			data.transactionPage.totalItems,
		),
	);

	function formatAmount(amount: number, direction: 'INGRESS' | 'EGRESS'): string {
		const prefix = direction === 'INGRESS' ? '+' : '-';
		return `${prefix}${fmt.format(amount)}`;
	}

	function transactionHref({
		pageIndex = data.transactionPage.pageIndex,
		pageSize = data.transactionPage.pageSize,
		sortBy = data.transactionPage.sortBy,
		sortDirection = data.transactionPage.sortDirection,
	}: {
		pageIndex?: number;
		pageSize?: number;
		sortBy?: TransactionSortBy;
		sortDirection?: TransactionSortDirection;
	} = {}) {
		const params = new URLSearchParams({
			page: String(Math.max(0, pageIndex)),
			pageSize: String(pageSize),
			sortBy,
			sortDirection,
		});
		return `/transactions?${params.toString()}`;
	}

	function navigateTransactions(args: Parameters<typeof transactionHref>[0]) {
		void goto(transactionHref(args), { keepFocus: true, noScroll: true });
	}

	function nextSortDirection(sortBy: TransactionSortBy): TransactionSortDirection {
		if (data.transactionPage.sortBy !== sortBy) return sortBy === 'transactionDate' ? 'desc' : 'asc';
		return data.transactionPage.sortDirection === 'asc' ? 'desc' : 'asc';
	}

	const filteredCategories = $derived(
		data.categories
			.filter((c) => c.type === $form.direction || c.type === 'BOTH')
			.sort((a, b) => a.name.localeCompare(b.name)),
	);

	const sortedContacts = $derived(
		[...data.contacts].sort((a, b) => a.name.localeCompare(b.name)),
	);

	$effect(() => {
		const ids = filteredCategories.map((c) => c.id);
		if ($form.categoryId && !ids.includes($form.categoryId)) {
			$form.categoryId = ids[0] ?? 0;
		}
	});

</script>

{#snippet sortIcon(column: TransactionSortBy)}
	{#if data.transactionPage.sortBy === column}
		{#if data.transactionPage.sortDirection === 'asc'}
			<ArrowUpIcon data-icon="inline-end" class="text-foreground" />
		{:else}
			<ArrowDownIcon data-icon="inline-end" class="text-foreground" />
		{/if}
	{:else}
		<ArrowUpDownIcon data-icon="inline-end" class="text-muted-foreground" />
	{/if}
{/snippet}

<div class="space-y-6">
	<div class="flex items-center justify-between">
		<div>
			<h1 class="text-2xl font-semibold tracking-tight">{m.transactions_title()}</h1>
			<p class="text-sm text-muted-foreground">{m.transactions_description()}</p>
		</div>
		<Button onclick={openCreate} disabled={data.categories.length === 0}>{m.transactions_new()}</Button>
	</div>

	{#if data.transactionPage.totalItems > 0}
		<div class="flex flex-col gap-3 rounded-lg border bg-card p-3 sm:flex-row sm:items-center sm:justify-between">
			<p class="text-sm text-muted-foreground">
				{m.transactions_showing({
					start: pageRangeStart,
					end: pageRangeEnd,
					total: data.transactionPage.totalItems,
				})}
			</p>
			<div class="grid gap-2 sm:grid-cols-[minmax(11rem,1fr)_minmax(9rem,auto)_minmax(8rem,auto)]">
				<NativeSelect
					name="sortBy"
					aria-label={m.transactions_sort_by()}
					value={data.transactionPage.sortBy}
					onValueChange={(v) => navigateTransactions({ sortBy: v as TransactionSortBy, pageIndex: 0 })}
					items={sortItems}
				/>
				<NativeSelect
					name="sortDirection"
					aria-label={m.transactions_sort_direction_aria()}
					value={data.transactionPage.sortDirection}
					onValueChange={(v) =>
						navigateTransactions({ sortDirection: v as TransactionSortDirection, pageIndex: 0 })}
					items={sortDirectionItems}
				/>
				<NativeSelect
					name="pageSize"
					aria-label={m.transactions_per_page_aria()}
					value={String(data.transactionPage.pageSize)}
					onValueChange={(v) => navigateTransactions({ pageSize: Number(v), pageIndex: 0 })}
					items={pageSizeItems}
				/>
			</div>
		</div>
	{/if}

	{#if data.transactionPage.totalItems === 0}
		<Empty.Root class="border">
			<Empty.Title>{m.transactions_empty_title()}</Empty.Title>
			<Empty.Description>{m.transactions_empty_description()}</Empty.Description>
		</Empty.Root>
	{:else}
		<!-- Mobile card grid (< md) -->
		<div class="grid gap-4 md:hidden">
			{#each table.getRowModel().rows as row (row.original.id)}
				{@const tx = row.original}
				<a href="/transactions/{tx.id}" class="block">
					<Card.Root class="transition-colors hover:bg-muted/50">
						<Card.Content class="pt-4">
							<div class="flex items-start justify-between gap-2">
								<div class="flex-1 min-w-0">
									<p class="text-sm text-muted-foreground truncate">{tx.description ?? '—'}</p>
									<p class="text-xs text-muted-foreground mt-0.5">{tx.transactionDate}</p>
								</div>
								<span
									class="font-mono font-semibold text-sm shrink-0 {tx.direction === 'INGRESS'
										? 'text-green-600 dark:text-green-400'
										: 'text-red-600 dark:text-red-400'}"
								>
									{formatAmount(tx.amount, tx.direction as 'INGRESS' | 'EGRESS')}
								</span>
							</div>
							<div class="flex items-center gap-2 mt-2">
								{#if tx.categoryName}
									<CategoryBadge hue={tx.categoryHue} name={tx.categoryName} direction={tx.direction} />
								{/if}
								{#if tx.contactName}
									<span class="text-xs text-muted-foreground">{tx.contactName}</span>
								{/if}
							</div>
						</Card.Content>
					</Card.Root>
				</a>
			{/each}
		</div>

		<!-- Desktop table (>= md) -->
		<div class="hidden md:block rounded-lg border">
			<Table.Root>
				<Table.Header>
					<Table.Row>
						<Table.Head>
							<Button
								variant="ghost"
								size="sm"
								class="-ml-2"
								href={transactionHref({
									sortBy: 'transactionDate',
									sortDirection: nextSortDirection('transactionDate'),
									pageIndex: 0,
								})}
								aria-label={m.transactions_sort_by_date()}
							>
								{m.common_date()} {@render sortIcon('transactionDate')}
							</Button>
						</Table.Head>
						<Table.Head>
							<Button
								variant="ghost"
								size="sm"
								class="-ml-2"
								href={transactionHref({
									sortBy: 'description',
									sortDirection: nextSortDirection('description'),
									pageIndex: 0,
								})}
								aria-label={m.transactions_sort_by_description()}
							>
								{m.common_description()} {@render sortIcon('description')}
							</Button>
						</Table.Head>
						<Table.Head>
							<Button
								variant="ghost"
								size="sm"
								class="-ml-2"
								href={transactionHref({
									sortBy: 'amount',
									sortDirection: nextSortDirection('amount'),
									pageIndex: 0,
								})}
								aria-label={m.transactions_sort_by_amount()}
							>
								{m.common_amount()} {@render sortIcon('amount')}
							</Button>
						</Table.Head>
						<Table.Head>
							<Button
								variant="ghost"
								size="sm"
								class="-ml-2"
								href={transactionHref({
									sortBy: 'categoryName',
									sortDirection: nextSortDirection('categoryName'),
									pageIndex: 0,
								})}
								aria-label={m.transactions_sort_by_category()}
							>
								{m.common_category()} {@render sortIcon('categoryName')}
							</Button>
						</Table.Head>
						<Table.Head>
							<Button
								variant="ghost"
								size="sm"
								class="-ml-2"
								href={transactionHref({
									sortBy: 'contactName',
									sortDirection: nextSortDirection('contactName'),
									pageIndex: 0,
								})}
								aria-label={m.transactions_sort_by_contact()}
							>
								{m.common_contact()} {@render sortIcon('contactName')}
							</Button>
						</Table.Head>
						<Table.Head class="w-[120px] text-right">{m.common_actions()}</Table.Head>
					</Table.Row>
				</Table.Header>
				<Table.Body>
					{#each table.getRowModel().rows as row (row.original.id)}
						{@const tx = row.original}
						<Table.Row>
							<Table.Cell class="whitespace-nowrap">{tx.transactionDate}</Table.Cell>
							<Table.Cell class="text-muted-foreground">{tx.description ?? '—'}</Table.Cell>
							<Table.Cell
								class="font-mono font-medium {tx.direction === 'INGRESS'
									? 'text-green-600 dark:text-green-400'
									: 'text-red-600 dark:text-red-400'}"
							>
								{formatAmount(tx.amount, tx.direction as 'INGRESS' | 'EGRESS')}
							</Table.Cell>
							<Table.Cell>
								{#if tx.categoryName}
									<CategoryBadge hue={tx.categoryHue} name={tx.categoryName} direction={tx.direction} />
								{:else}
									—
								{/if}
							</Table.Cell>
							<Table.Cell>{tx.contactName ?? '—'}</Table.Cell>
							<Table.Cell class="text-right">
								<div class="flex justify-end gap-2">
									<Button variant="outline" size="sm" onclick={() => openEdit(tx)}>{m.common_edit()}</Button>
									<Button variant="destructive" size="sm" onclick={() => openDelete(tx)}>{m.common_delete()}</Button>
								</div>
							</Table.Cell>
						</Table.Row>
					{/each}
				</Table.Body>
			</Table.Root>
		</div>

		<div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
			<p class="text-sm text-muted-foreground">
				{m.transactions_page_of({
					page: currentPage,
					total: Math.max(data.transactionPage.totalPages, 1),
				})}
			</p>
			<div class="flex items-center gap-2">
				<Button
					variant="outline"
					size="sm"
					href={transactionHref({ pageIndex: data.transactionPage.pageIndex - 1 })}
					disabled={data.transactionPage.pageIndex <= 0}
				>
					<ChevronLeftIcon data-icon="inline-start" />
					{m.transactions_previous()}
				</Button>
				<Button
					variant="outline"
					size="sm"
					href={transactionHref({ pageIndex: data.transactionPage.pageIndex + 1 })}
					disabled={data.transactionPage.pageIndex + 1 >= data.transactionPage.totalPages}
				>
					{m.transactions_next()}
					<ChevronRightIcon data-icon="inline-end" />
				</Button>
			</div>
		</div>
	{/if}
</div>

<!-- Create / Edit dialog -->
<Dialog.Root bind:open={dialogOpen}>
	<Dialog.Content class="sm:max-w-lg">
		<Dialog.Header>
			<Dialog.Title>{editMode ? m.transactions_edit_title() : m.transactions_new_title()}</Dialog.Title>
			<Dialog.Description>
				{editMode ? m.transactions_edit_description() : m.transactions_new_description()}
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

			<div class="grid grid-cols-2 gap-4">
				<Form.Field form={sf} name="amount">
					<Form.Control>
						{#snippet children({ props })}
							<Form.Label>{m.common_amount_mxn()}</Form.Label>
							<Input
								{...props}
								type="number"
								step="0.01"
								min="0.01"
								bind:value={$form.amount}
								placeholder="0.00"
							/>
						{/snippet}
					</Form.Control>
					<Form.FieldErrors />
				</Form.Field>

				<Form.Field form={sf} name="direction">
					<Form.Control>
						{#snippet children({ props })}
							{@const { name: fieldName, ...triggerProps } = props}
							<Form.Label>{m.common_direction()}</Form.Label>
							<NativeSelect
								name={fieldName}
								value={$form.direction}
								onValueChange={(v) => { $form.direction = v as 'INGRESS' | 'EGRESS'; }}
								placeholder={m.common_select_direction()}
								items={[
									{ value: 'INGRESS', label: m.direction_ingress_income() },
									{ value: 'EGRESS', label: m.direction_egress_expense() },
								]}
								{...triggerProps}
							/>
						{/snippet}
					</Form.Control>
					<Form.FieldErrors />
				</Form.Field>
			</div>

			<Form.Field form={sf} name="description">
				<Form.Control>
					{#snippet children({ props })}
						<Form.Label>{m.common_description()}</Form.Label>
						<Input {...props} bind:value={$form.description} placeholder={m.transactions_placeholder_description()} />
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Form.Field form={sf} name="transactionDate">
				<Form.Control>
					{#snippet children({ props })}
						{@const { name: fieldName, ...triggerProps } = props}
						<Form.Label>{m.common_date()}</Form.Label>
						<NativeDatePicker
							name={fieldName}
							value={$form.transactionDate}
							onValueChange={(v) => { $form.transactionDate = v; }}
							{...triggerProps}
						/>
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Form.Field form={sf} name="categoryId">
				<Form.Control>
					{#snippet children({ props })}
						{@const { name: fieldName, ...triggerProps } = props}
						<Form.Label>{m.common_category()}</Form.Label>
						<NativeSelect
							name={fieldName}
							value={$form.categoryId ? String($form.categoryId) : ''}
							onValueChange={(v) => { $form.categoryId = v ? Number(v) : 0; }}
							placeholder={m.common_select_category()}
							items={filteredCategories.map(c => ({ value: String(c.id), label: c.name }))}
							{...triggerProps}
						/>
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Form.Field form={sf} name="contactId">
				<Form.Control>
					{#snippet children({ props })}
						{@const { name: fieldName, ...triggerProps } = props}
						<Form.Label>{m.common_contact_optional()}</Form.Label>
						<NativeSelect
							name={fieldName}
							value={$form.contactId !== '' ? String($form.contactId) : ''}
							onValueChange={(v) => { $form.contactId = v ? Number(v) : ''; }}
							placeholder={m.common_none()}
							items={sortedContacts.map(c => ({ value: String(c.id), label: c.name }))}
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

<!-- Delete confirmation dialog -->
<Dialog.Root bind:open={deleteDialogOpen}>
	<Dialog.Content class="sm:max-w-md">
		<Dialog.Header>
			<Dialog.Title>{m.transactions_delete_title()}</Dialog.Title>
			<Dialog.Description>
				{m.delete_confirm_prefix()} <strong>{deleteTargetDesc}</strong>{m.delete_confirm_suffix()}
			</Dialog.Description>
		</Dialog.Header>
		<form
			method="POST"
			action="?/delete"
			use:kitEnhance={async () => {
				return async ({ result, update }) => {
					if (result.type === 'success') {
						deleteDialogOpen = false;
						toast.success(m.transactions_trashed());
						await update();
					} else {
						const msg =
							(result as { data?: { message?: string } }).data?.message ??
							m.transactions_delete_failed();
						toast.error(msg);
					}
				};
			}}
		>
			<input type="hidden" name="id" value={deleteTargetId} />
			<Dialog.Footer>
				<Button type="button" variant="outline" onclick={() => (deleteDialogOpen = false)}>
					{m.common_cancel()}
				</Button>
				<Button type="submit" variant="destructive">{m.common_delete()}</Button>
			</Dialog.Footer>
		</form>
	</Dialog.Content>
</Dialog.Root>
