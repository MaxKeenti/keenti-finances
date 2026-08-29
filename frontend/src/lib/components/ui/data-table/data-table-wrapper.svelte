<script lang="ts" generics="TData">
	/**
	 * Ported from anotame-microservices (anotame-web `DataTableWrapper`).
	 *
	 * Two changes for Keenti. The original rendered a table only, so on a phone it
	 * became a horizontally scrolling grid; this one takes a `mobileCard` snippet
	 * and renders cards below `md` from the *same* row model, so the search box,
	 * the toolbar filters, sorting and pagination all drive both views. And the
	 * page size is a plain prop rather than a `runed` PersistedState store, which
	 * Keenti does not depend on and which would have competed with the per-User
	 * page-size preference the app already stores.
	 */
	import { untrack } from 'svelte';
	import type { Snippet } from 'svelte';
	import type { Row } from '@tanstack/table-core';
	import { createResponsiveTable, type ResponsiveTableProps } from './responsive-table.svelte';
	import * as Table from '$lib/components/ui/table';
	import { Input } from '$lib/components/ui/input';
	import { Button } from '$lib/components/ui/button';
	import { m } from '$lib/paraglide/messages.js';

	let {
		columns,
		data,
		pageSize: pageSizeProp = 20,
		loading = false,
		emptyMessage,
		filterPlaceholder,
		showFilter = true,
		showPagination = true,
		actionCell,
		cellRenders = {},
		bulkActions = false,
		bulkMode = $bindable(false),
		onSelectionChange,
		manualPagination = false,
		pageIndex = 0,
		pageCount,
		onPageChange,
		/** Rendered per row below `md` in place of the table. */
		mobileCard,
		/** Controls that sit beside the search box — status filters and the like. */
		toolbar,
	}: ResponsiveTableProps<TData> & {
		mobileCard?: Snippet<[Row<TData>]>;
		toolbar?: Snippet;
	} = $props();

	const emptyText = $derived(emptyMessage ?? m.table_no_results());
	const searchText = $derived(filterPlaceholder ?? m.table_search_placeholder());

	const state = createResponsiveTable<TData>({
		columns: () => columns,
		data: () => data,
		pageSize: () => pageSizeProp,
		// Seeds local pagination once; later changes come through `pageSize`.
		initialPageSize: untrack(() => pageSizeProp),
		bulkActions: () => bulkActions,
		bulkMode: () => bulkMode,
		manualPagination: () => manualPagination,
		pageIndex: () => pageIndex,
		pageCount: () => pageCount,
		enableColumnPinning: true,
		onPageChange: () => onPageChange,
		onSelectionChange: () => onSelectionChange,
	});

	const rows = $derived(state.table.getRowModel().rows);
	const totalRows = $derived(state.table.getFilteredRowModel().rows.length);
</script>

<div class="space-y-4">
	{#if showFilter || toolbar}
		<div class="flex flex-col gap-3 sm:flex-row sm:items-center">
			{#if showFilter}
				<div class="flex-1">
					<label for="dt-filter" class="sr-only">{m.common_search()}</label>
					<Input id="dt-filter" type="search" placeholder={searchText} bind:value={state.globalFilter} />
				</div>
			{/if}
			{#if toolbar}
				<div class="flex flex-wrap items-center gap-2">{@render toolbar()}</div>
			{/if}
		</div>
	{/if}

	{#if !loading && rows.length === 0}
		<p class="rounded-lg border border-dashed py-10 text-center text-sm text-muted-foreground">
			{emptyText}
		</p>
	{:else}
		<!-- Mobile: cards. Same rows as the table, so filters and paging agree. -->
		{#if mobileCard}
			<div class="grid gap-3 md:hidden">
				{#each rows as row (row.id)}
					{@render mobileCard(row)}
				{/each}
			</div>
		{/if}

		<!-- Desktop: table. -->
		<div class="{mobileCard ? 'hidden md:block' : ''} overflow-x-auto rounded-lg border">
			<Table.Root>
				<Table.Header>
					{#each state.table.getHeaderGroups() as headerGroup (headerGroup.id)}
						<Table.Row>
							{#each headerGroup.headers as header (header.id)}
								<Table.Head class={header.column.id === '__select__' ? 'w-10' : ''}>
									{#if !header.isPlaceholder}
										{#if header.column.id === '__select__'}
											<input
												type="checkbox"
												aria-label={m.table_select_all()}
												checked={state.table.getIsAllRowsSelected()}
												onchange={state.table.getToggleAllRowsSelectedHandler()}
											/>
										{:else if header.column.getCanSort()}
											<button
												type="button"
												class="inline-flex h-7 items-center gap-1 font-medium hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-sm"
												onclick={header.column.getToggleSortingHandler()}
												aria-label={m.table_sort_by({ column: header.column.columnDef.header as string })}
											>
												{header.column.columnDef.header as string}
												<span aria-hidden="true" class="text-xs">
													{#if header.column.getIsSorted() === 'asc'}↑
													{:else if header.column.getIsSorted() === 'desc'}↓
													{:else}<span class="opacity-30">↕</span>{/if}
												</span>
											</button>
										{:else}
											{header.column.columnDef.header as string}
										{/if}
									{/if}
								</Table.Head>
							{/each}
						</Table.Row>
					{/each}
				</Table.Header>
				<Table.Body>
					{#if loading}
						<Table.Row>
							<Table.Cell colspan={state.effectiveColumns.length} class="h-24 text-center text-muted-foreground">
								{m.table_loading()}
							</Table.Cell>
						</Table.Row>
					{:else}
						{#each rows as row (row.id)}
							<Table.Row class="group/row">
								{#each row.getVisibleCells() as cell (cell.id)}
									<Table.Cell>
										{#if cell.column.id === '__select__'}
											<input
												type="checkbox"
												aria-label={m.table_select_row()}
												checked={cell.row.getIsSelected()}
												onchange={cell.row.getToggleSelectedHandler()}
											/>
										{:else if cellRenders && cellRenders[cell.column.id]}
											{@render cellRenders[cell.column.id](row)}
										{:else if cell.column.id === 'actions' && actionCell}
											{@render actionCell(row)}
										{:else}
											{(cell.getValue() as string) ?? ''}
										{/if}
									</Table.Cell>
								{/each}
							</Table.Row>
						{/each}
					{/if}
				</Table.Body>
			</Table.Root>
		</div>
	{/if}

	{#if showPagination && state.table.getPageCount() > 1}
		<div class="flex items-center justify-between gap-3">
			<p class="text-sm text-muted-foreground tabular-nums">
				{m.table_page_of({
					current: String(state.table.getState().pagination.pageIndex + 1),
					total: String(state.table.getPageCount() || 1),
				})}
			</p>
			<div class="flex gap-2">
				<Button
					variant="outline"
					size="sm"
					disabled={!state.table.getCanPreviousPage()}
					onclick={() => state.table.previousPage()}
				>
					{m.table_previous()}
				</Button>
				<Button
					variant="outline"
					size="sm"
					disabled={!state.table.getCanNextPage()}
					onclick={() => state.table.nextPage()}
				>
					{m.table_next()}
				</Button>
			</div>
		</div>
	{:else if showFilter && totalRows !== data.length}
		<p class="text-sm text-muted-foreground tabular-nums">
			{m.table_result_count({ count: String(totalRows), total: String(data.length) })}
		</p>
	{/if}
</div>
