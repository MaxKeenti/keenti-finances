<script lang="ts">
	import { untrack } from 'svelte';
	import { superForm } from 'sveltekit-superforms';
	import { zod4Client } from 'sveltekit-superforms/adapters';
	import { toast } from 'svelte-sonner';
	import { enhance as kitEnhance } from '$app/forms';
	import { goto } from '$app/navigation';
	import { adaptiveConfirm, submitWithAdaptiveConfirm } from '$lib/components/adaptive-confirm';
	import { dockActionStore } from '$lib/components/app-shell/dock-action.svelte';
	import {
		BoxAllocationEditor,
		TransactionBoxBreakdown,
	} from '$lib/components/transactions';
	import { FundingSuggestionEditor } from '$lib/components/funding-triggers';
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
	import Trash2Icon from '@lucide/svelte/icons/trash-2';
	import * as Table from '$lib/components/ui/table';
	import * as Dialog from '$lib/components/ui/dialog';
	import * as Form from '$lib/components/ui/form';
	import * as Alert from '$lib/components/ui/alert';
	import * as Empty from '$lib/components/ui/empty';
	import { Input } from '$lib/components/ui/input';
	import { Button } from '$lib/components/ui/button';
	import { Checkbox } from '$lib/components/ui/checkbox';
	import { NativeSelect } from '$lib/components/native-select';
	import { NativeDatePicker } from '$lib/components/native-date-picker';
	import { CategoryBadge } from '$lib/components/ui/category-badge';
	import * as Card from '$lib/components/ui/card';
	import { dateInTimeZone, formatDateOnly, mxnFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
	import { transactionSchema } from '$lib/schemas/transaction';
	import {
		allocationTotal,
		amountToCents,
		hasAtMostTwoDecimalPlaces,
		type BoxAllocationInput,
		type TransactionDirection,
	} from '$lib/types/transactions';
	import type { PageData } from './$types';

	type Transaction = PageData['transactions'][number];
	type Transfer = PageData['transfers'][number];
	type LedgerItem =
		| { key: string; kind: 'Transaction'; date: string; amount: number; transaction: Transaction }
		| { key: string; kind: 'Transfer'; date: string; amount: number; transfer: Transfer };
	type TransactionSortBy = PageData['transactionPage']['sortBy'];
	type TransactionSortDirection = PageData['transactionPage']['sortDirection'];

	let { data }: { data: PageData } = $props();

	let dialogOpen = $state(false);
	let editMode = $state(false);
	let deleteTargetId = $state<number | null>(null);
	let deleteForm = $state<HTMLFormElement | null>(null);
	let bulkDeleteForm = $state<HTMLFormElement | null>(null);
	let selectedTxIds = $state<Set<number>>(new Set());
	let editingTransaction = $state<Transaction | null>(null);

	const today = $derived(dateInTimeZone(data.preferences.timeZone));

	const sf = superForm(untrack(() => data.form), {
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
		editingTransaction = null;
		sf.reset({
			data: {
				amount: 0,
				direction: 'INGRESS',
				description: '',
				transactionDate: today,
				categoryId: data.categories[0]?.id ?? 0,
				contactId: '',
				accountId: '',
				boxFunding: [],
				boxDistributions: [],
			},
		});
		dialogOpen = true;
	}

	async function openEdit(tx: Transaction) {
		if (
			tx.boxDistributions.length > 0 &&
			!(await adaptiveConfirm({
				title: m.transactions_distribution_edit_warning_title(),
				description: m.transactions_distribution_edit_warning_description(),
				confirmLabel: m.common_edit(),
				cancelLabel: m.common_cancel(),
			}))
		) {
			return;
		}
		editMode = true;
		editingTransaction = tx;
		form.set({
			id: tx.id,
			amount: tx.amount,
			direction: tx.direction as 'INGRESS' | 'EGRESS',
			description: tx.description ?? '',
			transactionDate: tx.transactionDate,
			categoryId: tx.categoryId,
			contactId: tx.contactId ?? '',
			accountId: tx.accountId ?? '',
			boxFunding: tx.boxFunding.map(({ boxId, amount }) => ({ boxId, amount })),
			boxDistributions: [],
		});
		dialogOpen = true;
	}

	async function openDelete(tx: { id: number; description: string | null; amount: number; direction: string }) {
		const description = tx.description || formatAmount(tx.amount, tx.direction as 'INGRESS' | 'EGRESS');
		const linkedDepositWarning =
			data.transactions.find((transaction) => transaction.id === tx.id)?.boxDistributions.length
				? ` ${m.transactions_distribution_delete_warning()}`
				: '';
		deleteTargetId = tx.id;
		await submitWithAdaptiveConfirm(deleteForm, {
			title: m.transactions_delete_title(),
			description: `${m.delete_confirm_prefix()} ${description}${m.delete_confirm_suffix()}${linkedDepositWarning}`,
			confirmLabel: m.common_delete(),
			cancelLabel: m.common_cancel(),
			destructive: true,
		});
	}

	const fmt = $derived(mxnFormatter(data.preferences.locale));
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
	const ledgerItems = $derived.by(() => {
		const items: LedgerItem[] = [
			...data.activityTransactions.map((transaction) => ({
				key: `transaction-${transaction.id}`,
				kind: 'Transaction' as const,
				date: transaction.transactionDate,
				amount: transaction.amount,
				transaction: transaction as Transaction,
			})),
			...data.transfers.map((transfer) => ({
				key: `transfer-${transfer.id}`,
				kind: 'Transfer' as const,
				date: transfer.transferDate,
				amount: transfer.amount,
				transfer,
			})),
		];
		const value = (item: LedgerItem): string | number => {
			if (data.transactionPage.sortBy === 'transactionDate') return item.date;
			if (data.transactionPage.sortBy === 'amount') return item.amount;
			if (item.kind === 'Transfer') {
				if (data.transactionPage.sortBy === 'description') return `${item.transfer.sourceAccountName ?? ''} ${item.transfer.destinationAccountName ?? ''}`;
				return '';
			}
			return item.transaction[data.transactionPage.sortBy] ?? '';
		};
		const direction = data.transactionPage.sortDirection === 'asc' ? 1 : -1;
		return items.sort((left, right) => {
			const leftValue = value(left);
			const rightValue = value(right);
			const compared = typeof leftValue === 'number' && typeof rightValue === 'number'
				? leftValue - rightValue
				: String(leftValue).localeCompare(String(rightValue));
			return compared === 0 ? right.date.localeCompare(left.date) : compared * direction;
		});
	});
	const ledgerTotalPages = $derived(Math.ceil(ledgerItems.length / data.transactionPage.pageSize));
	const ledgerPageIndex = $derived(Math.min(data.transactionPage.pageIndex, Math.max(ledgerTotalPages - 1, 0)));
	const ledgerPageItems = $derived(ledgerItems.slice(
		ledgerPageIndex * data.transactionPage.pageSize,
		(ledgerPageIndex + 1) * data.transactionPage.pageSize,
	));
	const visibleTxIds = $derived(ledgerPageItems.filter((item) => item.kind === 'Transaction').map((item) => item.transaction.id));
	const allVisibleSelected = $derived(
		visibleTxIds.length > 0 && visibleTxIds.every((id) => selectedTxIds.has(id)),
	);
	const someVisibleSelected = $derived(
		visibleTxIds.some((id) => selectedTxIds.has(id)) && !allVisibleSelected,
	);
	const currentPage = $derived(ledgerItems.length === 0 ? 0 : ledgerPageIndex + 1);
	const pageRangeStart = $derived(
		ledgerItems.length === 0
			? 0
			: ledgerPageIndex * data.transactionPage.pageSize + 1,
	);
	const pageRangeEnd = $derived(
		Math.min(
			(ledgerPageIndex + 1) * data.transactionPage.pageSize,
			ledgerItems.length,
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

	function toggleSelectedTx(id: number) {
		const next = new Set(selectedTxIds);
		if (next.has(id)) next.delete(id);
		else next.add(id);
		selectedTxIds = next;
	}

	function setVisibleSelection(selected: boolean) {
		const next = new Set(selectedTxIds);
		for (const id of visibleTxIds) {
			if (selected) next.add(id);
			else next.delete(id);
		}
		selectedTxIds = next;
	}

	function clearSelection() {
		selectedTxIds = new Set();
	}

	async function confirmBulkDelete() {
		if (selectedTxIds.size === 0) return;
		const containsLinkedDeposits = data.transactions.some(
			(transaction) =>
				selectedTxIds.has(transaction.id) && transaction.boxDistributions.length > 0,
		);
		await submitWithAdaptiveConfirm(bulkDeleteForm, {
			title: m.transactions_bulk_delete_title(),
			description: `${m.transactions_bulk_delete_description({ count: selectedTxIds.size })}${
				containsLinkedDeposits ? ` ${m.transactions_distribution_bulk_delete_warning()}` : ''
			}`,
			confirmLabel: m.common_delete(),
			cancelLabel: m.common_cancel(),
			destructive: true,
		});
	}

	// Register the contextual bulk-action bar (which swaps the dock) while a
	// selection is active; clear it when empty or on navigating away.
	$effect(() => {
		const count = selectedTxIds.size;
		if (count === 0) {
			dockActionStore.clear();
			return;
		}
		dockActionStore.set({
			count,
			actions: [
				{
					label: m.common_delete(),
					icon: Trash2Icon,
					variant: 'destructive',
					onClick: confirmBulkDelete,
				},
			],
			onCancel: clearSelection,
		});
		return () => dockActionStore.clear();
	});

	function changeDirection(direction: TransactionDirection) {
		$form.direction = direction;
		$form.boxFunding = [];
		$form.boxDistributions = [];
	}

	function setBoxFunding(allocations: BoxAllocationInput[]) {
		$form.boxFunding = allocations;
	}

	function setBoxDistributions(allocations: BoxAllocationInput[]) {
		$form.boxDistributions = allocations;
	}

	const filteredCategories = $derived(
		data.categories
			.filter((c) => c.type === $form.direction || c.type === 'BOTH')
			.sort((a, b) => a.name.localeCompare(b.name)),
	);

	const sortedContacts = $derived(
		[...data.contacts].sort((a, b) => a.name.localeCompare(b.name)),
	);
	const allocationInvalid = $derived.by(() => {
		const allocations =
			$form.direction === 'EGRESS'
				? $form.boxFunding
				: editMode
					? []
					: $form.boxDistributions;
		if (allocations.length === 0) return false;
		if ($form.transactionDate > today) return true;
		if (
			allocations.some(
				(allocation) =>
					allocation.amount <= 0 || !hasAtMostTwoDecimalPlaces(allocation.amount),
			)
		) {
			return true;
		}

		const allocatedCents = amountToCents(allocationTotal(allocations));
		if ($form.direction === 'INGRESS') {
			return (
				allocatedCents >
				amountToCents(Math.max(0, data.balanceSummary.availableToSpend + $form.amount))
			);
		}

		if (allocatedCents > amountToCents($form.amount)) return true;
		return allocations.some((allocation) => {
			const currentBalance = data.boxes.find((box) => box.id === allocation.boxId)?.balance ?? 0;
			const originalAmount =
				editingTransaction?.boxFunding.find((funding) => funding.boxId === allocation.boxId)
					?.amount ?? 0;
			return (
				amountToCents(allocation.amount) >
				amountToCents(currentBalance + originalAmount)
			);
		});
	});

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

	{#if ledgerItems.length > 0}
		<div class="flex flex-col gap-3 rounded-lg border bg-card p-3 sm:flex-row sm:items-center sm:justify-between">
			<p class="text-sm text-muted-foreground">
				{m.transactions_showing({
					start: pageRangeStart,
					end: pageRangeEnd,
					total: ledgerItems.length,
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

	{#if ledgerItems.length === 0}
		<Empty.Root class="border">
			<Empty.Title>{m.transactions_empty_title()}</Empty.Title>
			<Empty.Description>{m.transactions_empty_description()}</Empty.Description>
		</Empty.Root>
	{:else}
		<!-- Mobile card grid (< md) -->
		<!-- grid-cols-1 (minmax(0,1fr)) keeps the single column from growing to the
		     cards' max-content width; without it the truncated (nowrap) descriptions
		     blow the track past the viewport and the page overflows horizontally. -->
		<div class="grid grid-cols-1 gap-4 md:hidden">
			{#each ledgerPageItems as item (item.key)}
				{#if item.kind === 'Transaction'}
					{@const tx = item.transaction}
					{@const selected = selectedTxIds.has(tx.id)}
					<div class="relative">
						<div class="absolute left-3 top-4 z-1">
							<Checkbox checked={selected} onclick={(event) => { event.preventDefault(); event.stopPropagation(); toggleSelectedTx(tx.id); }} aria-label={m.transactions_select_transaction({ description: tx.description ?? formatAmount(tx.amount, tx.direction as 'INGRESS' | 'EGRESS') })} />
						</div>
						<Card.Root class="transition-colors hover:bg-muted/50 {selected ? 'ring-2 ring-primary/50' : ''}">
							<Card.Content class="pt-4 pl-10">
								<div class="flex items-start justify-between gap-2">
									<a href="/transactions/{tx.id}" class="min-w-0 flex-1 rounded-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"><p class="truncate text-sm text-muted-foreground">{tx.description ?? '—'}</p><p class="mt-0.5 text-xs text-muted-foreground">{formatDateOnly(tx.transactionDate, data.preferences.locale)}</p></a>
									<a href="/transactions/{tx.id}" class="shrink-0 font-mono text-sm font-semibold {tx.direction === 'INGRESS' ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}">{formatAmount(tx.amount, tx.direction as 'INGRESS' | 'EGRESS')}</a>
								</div>
								<div class="mt-2 flex flex-wrap items-center gap-2">{#if tx.categoryName}<CategoryBadge hue={tx.categoryHue} name={tx.categoryName} direction={tx.direction} />{/if}{#if tx.contactName}<span class="text-xs text-muted-foreground">{tx.contactName}</span>{/if}{#if tx.accountName}<span class="text-xs text-muted-foreground">{tx.accountName}</span>{/if}</div>
								{#if tx.boxFunding.length > 0 || tx.boxDistributions.length > 0}<div class="mt-3"><TransactionBoxBreakdown direction={tx.direction as TransactionDirection} amount={tx.amount} boxFunding={tx.boxFunding} boxDistributions={tx.boxDistributions} availableToSpendAmount={tx.availableToSpendAmount} locale={data.preferences.locale} /></div>{/if}
							</Card.Content>
						</Card.Root>
					</div>
				{:else}
					{@const transfer = item.transfer}
					<Card.Root class="border-violet-500/20 bg-violet-500/4">
						<Card.Content class="pt-4">
							<div class="flex items-start justify-between gap-2"><div class="min-w-0 flex-1"><p class="truncate text-sm text-muted-foreground">{transfer.sourceAccountName ?? 'Archived account'} → {transfer.destinationAccountName ?? 'Archived account'}</p><p class="mt-0.5 text-xs text-muted-foreground">{formatDateOnly(transfer.transferDate, data.preferences.locale)}</p></div><span class="shrink-0 font-mono text-sm font-semibold text-violet-700 dark:text-violet-300">↔ {fmt.format(transfer.amount)}</span></div>
							<div class="mt-2 flex flex-wrap items-center gap-2"><span class="inline-flex rounded-full bg-violet-500/15 px-2 py-0.5 text-xs font-medium text-violet-700 dark:text-violet-300">Transfer</span>{#if transfer.notes}<span class="min-w-0 truncate text-xs text-muted-foreground">{transfer.notes}</span>{/if}</div>
						</Card.Content>
					</Card.Root>
				{/if}
			{/each}
		</div>

		<!-- Desktop table (>= md) -->
		<div class="hidden overflow-x-auto rounded-lg border md:block">
			<Table.Root>
				<Table.Header>
					<Table.Row>
						<Table.Head class="w-10">
							<Checkbox
								checked={allVisibleSelected}
								indeterminate={someVisibleSelected}
								onclick={() => setVisibleSelection(!allVisibleSelected)}
								aria-label={m.transactions_select_page()}
							/>
						</Table.Head>
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
						<Table.Head>Account</Table.Head>
						<Table.Head class="w-30 text-right">{m.common_actions()}</Table.Head>
					</Table.Row>
				</Table.Header>
				<Table.Body>
					{#each ledgerPageItems as item (item.key)}
						{#if item.kind === 'Transaction'}
							{@const tx = item.transaction}
							<Table.Row data-state={selectedTxIds.has(tx.id) ? 'selected' : undefined}>
								<Table.Cell><Checkbox checked={selectedTxIds.has(tx.id)} onclick={() => toggleSelectedTx(tx.id)} aria-label={m.transactions_select_transaction({ description: tx.description ?? formatAmount(tx.amount, tx.direction as 'INGRESS' | 'EGRESS') })} /></Table.Cell>
								<Table.Cell class="whitespace-nowrap">{formatDateOnly(tx.transactionDate, data.preferences.locale)}</Table.Cell>
								<Table.Cell class="text-muted-foreground"><div class="space-y-1.5"><a href="/transactions/{tx.id}" class="hover:text-foreground hover:underline">{tx.description ?? '—'}</a>{#if tx.boxFunding.length > 0 || tx.boxDistributions.length > 0}<TransactionBoxBreakdown direction={tx.direction as TransactionDirection} amount={tx.amount} boxFunding={tx.boxFunding} boxDistributions={tx.boxDistributions} availableToSpendAmount={tx.availableToSpendAmount} locale={data.preferences.locale} />{/if}</div></Table.Cell>
								<Table.Cell class="font-mono font-medium {tx.direction === 'INGRESS' ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}">{formatAmount(tx.amount, tx.direction as 'INGRESS' | 'EGRESS')}</Table.Cell>
								<Table.Cell>{#if tx.categoryName}<CategoryBadge hue={tx.categoryHue} name={tx.categoryName} direction={tx.direction} />{:else}—{/if}</Table.Cell>
								<Table.Cell>{tx.contactName ?? '—'}</Table.Cell>
								<Table.Cell>{tx.accountName ?? '—'}</Table.Cell>
								<Table.Cell class="text-right"><div class="flex justify-end gap-2"><Button variant="outline" size="sm" onclick={() => openEdit(tx)}>{m.common_edit()}</Button><Button variant="destructive" size="sm" onclick={() => openDelete(tx)}>{m.common_delete()}</Button></div></Table.Cell>
							</Table.Row>
						{:else}
							{@const transfer = item.transfer}
							<Table.Row class="bg-violet-500/4">
								<Table.Cell></Table.Cell>
								<Table.Cell class="whitespace-nowrap">{formatDateOnly(transfer.transferDate, data.preferences.locale)}</Table.Cell>
								<Table.Cell><a href="/accounts" class="font-medium hover:underline">{transfer.sourceAccountName ?? 'Archived account'} → {transfer.destinationAccountName ?? 'Archived account'}</a>{#if transfer.notes}<p class="text-xs text-muted-foreground">{transfer.notes}</p>{/if}</Table.Cell>
								<Table.Cell class="font-mono font-medium text-violet-700 dark:text-violet-300">↔ {fmt.format(transfer.amount)}</Table.Cell>
								<Table.Cell><span class="inline-flex rounded-full bg-violet-500/15 px-2 py-0.5 text-xs font-medium text-violet-700 dark:text-violet-300">Transfer</span></Table.Cell>
								<Table.Cell>—</Table.Cell>
								<Table.Cell>—</Table.Cell>
								<Table.Cell></Table.Cell>
							</Table.Row>
						{/if}
					{/each}
				</Table.Body>
			</Table.Root>
		</div>

		<div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
			<p class="text-sm text-muted-foreground">
				{m.transactions_page_of({
					page: currentPage,
					total: Math.max(ledgerTotalPages, 1),
				})}
			</p>
			<div class="flex items-center gap-2">
				<Button
					variant="outline"
					size="sm"
					href={transactionHref({ pageIndex: ledgerPageIndex - 1 })}
					disabled={ledgerPageIndex <= 0}
				>
					<ChevronLeftIcon data-icon="inline-start" />
					{m.transactions_previous()}
				</Button>
				<Button
					variant="outline"
					size="sm"
					href={transactionHref({ pageIndex: ledgerPageIndex + 1 })}
					disabled={ledgerPageIndex + 1 >= ledgerTotalPages}
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
	<Dialog.Content class="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
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

		{#if !editMode && data.accountTracking.setupRequired}
			<Alert.Root>
				<Alert.Title>Set up Account tracking first</Alert.Title>
				<Alert.Description>New Users need at least one Financial Account before recording activity. <a class="underline" href="/accounts">Set up Accounts</a>.</Alert.Description>
			</Alert.Root>
		{/if}

		{#if editMode && editingTransaction && editingTransaction.boxDistributions.length > 0}
			<Alert.Root>
				<Alert.Title>{m.transactions_distribution_independent_title()}</Alert.Title>
				<Alert.Description class="space-y-2">
					<p>{m.transactions_distribution_independent_description()}</p>
					<TransactionBoxBreakdown
						direction="INGRESS"
						amount={editingTransaction.amount}
						boxDistributions={editingTransaction.boxDistributions}
						locale={data.preferences.locale}
					/>
				</Alert.Description>
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
								onValueChange={(v) => changeDirection(v as TransactionDirection)}
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

			{#if data.accountTracking.active}
				<Form.Field form={sf} name="accountId">
					<Form.Control>
						{#snippet children({ props })}
							{@const { name: fieldName, ...triggerProps } = props}
							<Form.Label>Financial Account</Form.Label>
							<NativeSelect
								name={fieldName}
								value={$form.accountId ? String($form.accountId) : ''}
								onValueChange={(v) => { $form.accountId = v ? Number(v) : ''; }}
								placeholder="Select an account"
								items={data.accounts.map(account => ({
									value: String(account.id),
									label: `${account.name} · ${fmt.format(account.balance)}`,
								}))}
								{...triggerProps}
							/>
						{/snippet}
					</Form.Control>
					<Form.FieldErrors />
				</Form.Field>
			{/if}

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

			{#if $form.direction === 'EGRESS'}
				<BoxAllocationEditor
					kind="funding"
					boxes={data.boxes}
					allocations={$form.boxFunding}
					onChange={setBoxFunding}
					transactionAmount={$form.amount}
					transactionDate={$form.transactionDate}
					{today}
					availableBefore={data.balanceSummary.availableToSpend}
					locale={data.preferences.locale}
					originalAmount={editingTransaction?.amount ?? 0}
					originalDirection={(editingTransaction?.direction as TransactionDirection | undefined) ?? null}
					originalFunding={editingTransaction?.boxFunding ?? []}
					categoryName={data.categories.find((category) => category.id === $form.categoryId)?.name ?? null}
				/>
			{:else if !editMode}
				<FundingSuggestionEditor
					categoryId={$form.categoryId}
					ingressAmount={$form.amount}
					availableBefore={data.balanceSummary.availableToSpend}
					allocations={$form.boxDistributions}
					onChange={setBoxDistributions}
					locale={data.preferences.locale}
					active={dialogOpen}
				/>
				<BoxAllocationEditor
					kind="distribution"
					boxes={data.boxes}
					allocations={$form.boxDistributions}
					onChange={setBoxDistributions}
					transactionAmount={$form.amount}
					transactionDate={$form.transactionDate}
					{today}
					availableBefore={data.balanceSummary.availableToSpend}
					locale={data.preferences.locale}
				/>
			{/if}

			<Dialog.Footer>
				<Button type="button" variant="outline" onclick={() => (dialogOpen = false)}>{m.common_cancel()}</Button>
				<Button type="submit" disabled={$submitting || allocationInvalid || (!editMode && data.accountTracking.setupRequired)}>
					{$submitting ? m.common_saving() : editMode ? m.common_update() : m.common_create()}
				</Button>
			</Dialog.Footer>
		</form>
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
				if (deleteTargetId != null) {
					const next = new Set(selectedTxIds);
					next.delete(deleteTargetId);
					selectedTxIds = next;
				}
				deleteTargetId = null;
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
</form>

<form
	bind:this={bulkDeleteForm}
	method="POST"
	action="?/bulkDelete"
	class="hidden"
	aria-hidden="true"
	use:kitEnhance={async () => {
		return async ({ result, update }) => {
			if (result.type === 'success') {
				const deleted = (result.data as { deleted?: number } | undefined)?.deleted ?? selectedTxIds.size;
				clearSelection();
				toast.success(m.transactions_bulk_trashed({ count: deleted }));
				await update();
			} else {
				const msg =
					(result as { data?: { message?: string } }).data?.message ??
					m.transactions_bulk_delete_failed();
				toast.error(msg);
			}
		};
	}}
>
	{#each selectedTxIds as txId}
		<input type="hidden" name="id" value={txId} />
	{/each}
</form>
