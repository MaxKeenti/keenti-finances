/**
 * Ported from anotame-microservices (anotame-web) so the two projects share one
 * table implementation. Adapted for Keenti: the wrapper that consumes this also
 * renders a mobile card layout from the same row model, so search, sorting and
 * pagination apply to both views rather than only the desktop table.
 */
import { untrack } from 'svelte';
import {
	createTable,
	getCoreRowModel,
	getSortedRowModel,
	getFilteredRowModel,
	getPaginationRowModel,
	type ColumnDef,
	type SortingState,
	type PaginationState,
	type ColumnPinningState,
	type RowSelectionState,
	type Row,
	type Table,
	type Updater,
	type RowData,
} from '@tanstack/table-core';

/**
 * Where a column belongs in the mobile card: `header` is the card's top line,
 * `body` is a labelled row beneath it, `hidden` is desktop-table only. Declared
 * on the module so every column definition gets `meta.cardGroup` for free.
 */
export type CardGroup = 'header' | 'body' | 'hidden';

declare module '@tanstack/table-core' {
	// eslint-disable-next-line @typescript-eslint/no-unused-vars
	interface ColumnMeta<TData extends RowData, TValue> {
		cardGroup?: CardGroup;
	}
}

/** Props shared by DataTableWrapper and CardGridWrapper. */
export type ResponsiveTableProps<TData> = {
	columns: ColumnDef<TData>[];
	data: TData[];
	pageSize?: number;
	loading?: boolean;
	emptyMessage?: string;
  filterPlaceholder?: string;
  showFilter?: boolean;
  showPagination?: boolean;
	actionCell?: import('svelte').Snippet<[Row<TData>]>;
	cellRenders?: Record<string, import('svelte').Snippet<[Row<TData>]>>;
	bulkActions?: boolean;
	bulkMode?: boolean;
	onSelectionChange?: (selectedRows: TData[]) => void;
	manualPagination?: boolean;
	pageIndex?: number;
	pageCount?: number;
	onPageChange?: (pageIndex: number) => void;
};

/** Column ids that, when present, are picked as the default sort. */
const NAME_COLUMN_IDS = ['name', 'title', 'customer', 'ticketNumber', 'nombre'];

/** Resolve a column's identifier from `id` or `accessorKey`. */
export function getColumnId<TData>(col: ColumnDef<TData>): string {
	const c = col as ColumnDef<TData> & { id?: string; accessorKey?: string | number };
	return c.id ?? (c.accessorKey != null ? String(c.accessorKey) : '');
}

/** Resolve a column's header text, falling back to '' for non-string headers. */
export function getColumnHeader<TData>(col: ColumnDef<TData>): string {
	return typeof col.header === 'string' ? col.header : '';
}

/** Apply a tanstack updater (value or callback) against the current value. */
function resolveUpdater<T>(updater: Updater<T>, current: T): T {
	return typeof updater === 'function' ? (updater as (old: T) => T)(current) : updater;
}

/** Pick the initial sort: a recognised "name" column, else the first sortable one. */
function computeInitialSorting<TData>(columns: ColumnDef<TData>[]): SortingState {
	const sortableCols = columns.filter(
		(c) => c.enableSorting !== false && getColumnId(c) !== '__select__'
	);
	const nameCol = sortableCols.find((c) => NAME_COLUMN_IDS.includes(getColumnId(c)));
	const targetCol = nameCol ?? sortableCols[0];
	return targetCol ? [{ id: getColumnId(targetCol), desc: false }] : [];
}

export interface ResponsiveTableConfig<TData> {
	/** Reactive getters so the table tracks prop changes across the module boundary. */
	columns: () => ColumnDef<TData>[];
	data: () => TData[];
	/** Page size used while `manualPagination` is on. */
	pageSize: () => number;
	/** Page size seeded into local pagination state (may differ, e.g. a persisted pref). */
	initialPageSize: number;
	bulkActions: () => boolean;
	bulkMode: () => boolean;
	manualPagination: () => boolean;
	pageIndex: () => number;
	pageCount: () => number | undefined;
	/** Maintain a columnPinning slice of state (desktop table only). */
	enableColumnPinning?: boolean;
	onPageChange?: () => ((pageIndex: number) => void) | undefined;
	onSelectionChange?: () => ((selectedRows: TData[]) => void) | undefined;
}

export interface ResponsiveTableState<TData> {
	readonly table: Table<TData>;
	readonly effectiveColumns: ColumnDef<TData>[];
	globalFilter: string;
	sorting: SortingState;
}

/**
 * Shared sorting / filtering / pagination / selection machinery for the responsive
 * table wrappers. Call once from a component `<script>`; the runes here run inside
 * the calling component's reactive scope.
 */
export function createResponsiveTable<TData>(
	config: ResponsiveTableConfig<TData>
): ResponsiveTableState<TData> {
	let sorting = $state<SortingState>(untrack(() => computeInitialSorting(config.columns())));
	let globalFilter = $state('');
	let pagination = $state<PaginationState>({ pageIndex: 0, pageSize: config.initialPageSize });
	let columnPinning = $state<ColumnPinningState>({ left: [], right: [] });
	let rowSelection = $state<RowSelectionState>({});

	const effectivePagination = $derived(
		config.manualPagination()
			? { pageIndex: config.pageIndex(), pageSize: config.pageSize() }
			: pagination
	);

	const selectionColumn = {
		id: '__select__',
		size: 48,
		enableSorting: false,
		header: '__select__',
		cell: '__select__',
	} as unknown as ColumnDef<TData>;

	const effectiveColumns = $derived(
		config.bulkActions() && config.bulkMode()
			? [selectionColumn, ...config.columns()]
			: config.columns()
	);

	// Reset pagination whenever the filter changes.
	$effect(() => {
		void globalFilter;
		untrack(() => {
			if (config.manualPagination()) {
				config.onPageChange?.()?.(0);
			} else {
				pagination = { pageIndex: 0, pageSize: pagination.pageSize };
			}
		});
	});

	// Clear selection when bulk mode is turned off.
	$effect(() => {
		if (!config.bulkMode()) {
			rowSelection = {};
		}
	});

	// Clear selection when the underlying data changes.
	$effect(() => {
		void config.data();
		if (config.bulkActions()) {
			untrack(() => {
				rowSelection = {};
			});
		}
	});

	const table = $derived.by(() => {
		const bulk = config.bulkActions();
		const manual = config.manualPagination();
		return createTable<TData>({
			data: config.data(),
			columns: effectiveColumns,
			state: {
				sorting,
				globalFilter,
				pagination: effectivePagination,
				...(config.enableColumnPinning ? { columnPinning } : {}),
				...(bulk ? { rowSelection } : {}),
			},
			manualPagination: manual,
			pageCount: manual ? config.pageCount() : undefined,
			onStateChange: () => {},
			onSortingChange: (updater) => {
				sorting = resolveUpdater(updater, sorting);
			},
			onGlobalFilterChange: (updater) => {
				globalFilter = resolveUpdater(updater, globalFilter);
			},
			onPaginationChange: (updater) => {
				const next = resolveUpdater(updater, effectivePagination);
				if (manual) {
					config.onPageChange?.()?.(next.pageIndex);
				} else {
					pagination = next;
				}
			},
			...(config.enableColumnPinning
				? {
						onColumnPinningChange: (updater) => {
							columnPinning = resolveUpdater(updater, columnPinning);
						},
					}
				: {}),
			...(bulk
				? {
						enableRowSelection: true,
						onRowSelectionChange: (updater) => {
							rowSelection = resolveUpdater(updater, rowSelection);
						},
					}
				: {}),
			getCoreRowModel: getCoreRowModel(),
			getSortedRowModel: getSortedRowModel(),
			getFilteredRowModel: getFilteredRowModel(),
			getPaginationRowModel: getPaginationRowModel(),
			renderFallbackValue: null,
		});
	});

	// Notify when the selection changes.
	$effect(() => {
		const notifySelectionChange = config.onSelectionChange?.();
		if (!config.bulkActions() || !notifySelectionChange) return;
		const selected = table.getSelectedRowModel().rows.map((r) => r.original);
		untrack(() => notifySelectionChange(selected));
	});

	return {
		get table() {
			return table;
		},
		get effectiveColumns() {
			return effectiveColumns;
		},
		get globalFilter() {
			return globalFilter;
		},
		set globalFilter(v: string) {
			globalFilter = v;
		},
		get sorting() {
			return sorting;
		},
		set sorting(v: SortingState) {
			sorting = v;
		},
	};
}
