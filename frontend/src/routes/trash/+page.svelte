<script lang="ts">
	import { enhance as kitEnhance, deserialize } from '$app/forms';
	import { invalidateAll } from '$app/navigation';
	import { SvelteSet } from 'svelte/reactivity';
	import { RotateCcw, Trash2 } from '@lucide/svelte';
	import { toast } from 'svelte-sonner';
	import { adaptiveConfirm, submitWithAdaptiveConfirm } from '$lib/components/adaptive-confirm';
	import { dockActionStore } from '$lib/components/app-shell/dock-action.svelte';
	import * as Table from '$lib/components/ui/table';
	import * as Empty from '$lib/components/ui/empty';
	import { Button } from '$lib/components/ui/button';
	import { Badge } from '$lib/components/ui/badge';
	import { Checkbox } from '$lib/components/ui/checkbox';
	import { shortDateFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
	import type { ActionResult } from '@sveltejs/kit';
	import type { PageData } from './$types';
	import type { TrashItem } from './+page.server';

	let { data }: { data: PageData } = $props();

	let deleteTargetId = $state<number | null>(null);
	let deleteTargetType = $state('');
	let permanentDeleteForm = $state<HTMLFormElement | null>(null);

	async function openPermanentDelete(item: { id: number; entityType: string; label: string }) {
		deleteTargetId = item.id;
		deleteTargetType = item.entityType;
		await submitWithAdaptiveConfirm(permanentDeleteForm, {
			title: m.trash_permanently_delete_title(),
			description: `${m.delete_confirm_permanent_prefix()} ${item.label}${m.delete_confirm_suffix()}`,
			confirmLabel: m.common_delete_permanently(),
			cancelLabel: m.common_cancel(),
			destructive: true,
		});
	}

	// --- Bulk selection ---------------------------------------------------
	const itemKey = (item: { id: number; entityType: string }) => `${item.entityType}-${item.id}`;

	const selectedKeys = new SvelteSet<string>();
	let busy = $state(false);

	const selectedItems = $derived(data.items.filter((item) => selectedKeys.has(itemKey(item))));
	const allSelected = $derived(
		data.items.length > 0 && data.items.every((item) => selectedKeys.has(itemKey(item))),
	);
	const someSelected = $derived(selectedKeys.size > 0 && !allSelected);

	function toggleItem(item: TrashItem) {
		const key = itemKey(item);
		if (selectedKeys.has(key)) selectedKeys.delete(key);
		else selectedKeys.add(key);
	}

	function toggleAll() {
		if (allSelected) selectedKeys.clear();
		else for (const item of data.items) selectedKeys.add(itemKey(item));
	}

	async function runBulk(action: 'bulkRestore' | 'bulkPermanentDelete', items: TrashItem[]) {
		const body = new FormData();
		body.set('items', JSON.stringify(items.map((i) => ({ id: i.id, entityType: i.entityType }))));
		const res = await fetch(`?/${action}`, { method: 'POST', body });
		return deserialize(await res.text()) as ActionResult<{
			done: number;
			failed: number;
			total: number;
		}>;
	}

	function reportBulk(result: ActionResult, successMessage: (count: number) => string) {
		if (result.type === 'success' && result.data) {
			const { done, failed, total } = result.data as {
				done: number;
				failed: number;
				total: number;
			};
			if (failed > 0) toast.warning(m.trash_bulk_partial({ done, total, failed }));
			else toast.success(successMessage(done));
		} else if (result.type === 'failure') {
			toast.error((result.data as { message?: string })?.message ?? m.trash_delete_failed());
		} else {
			toast.error(m.trash_delete_failed());
		}
	}

	async function handleBulkRestore() {
		if (busy) return;
		const items = selectedItems;
		if (items.length === 0) return;
		busy = true;
		try {
			const result = await runBulk('bulkRestore', items);
			reportBulk(result, (count) => m.trash_bulk_restored({ count }));
			selectedKeys.clear();
			await invalidateAll();
		} finally {
			busy = false;
		}
	}

	async function handleBulkDelete() {
		if (busy) return;
		const items = selectedItems;
		if (items.length === 0) return;
		const confirmed = await adaptiveConfirm({
			title: m.trash_bulk_delete_title(),
			description: m.trash_bulk_delete_description({ count: items.length }),
			confirmLabel: m.common_delete_permanently(),
			cancelLabel: m.common_cancel(),
			destructive: true,
		});
		if (!confirmed) return;
		busy = true;
		try {
			const result = await runBulk('bulkPermanentDelete', items);
			reportBulk(result, (count) => m.trash_bulk_deleted({ count }));
			selectedKeys.clear();
			await invalidateAll();
		} finally {
			busy = false;
		}
	}

	// Register the contextual bulk-action bar (which swaps the dock) whenever a
	// selection is active; clear it when empty or on navigating away.
	$effect(() => {
		const count = selectedItems.length;
		if (count === 0) {
			dockActionStore.clear();
			return;
		}
		dockActionStore.set({
			count,
			actions: [
				{
					label: m.common_restore(),
					icon: RotateCcw,
					variant: 'outline',
					disabled: busy,
					onClick: handleBulkRestore,
				},
				{
					label: m.common_delete(),
					icon: Trash2,
					variant: 'destructive',
					disabled: busy,
					onClick: handleBulkDelete,
				},
			],
			onCancel: () => selectedKeys.clear(),
		});
		return () => dockActionStore.clear();
	});

	const typeLabel: Record<string, () => string> = {
		transaction: m.entity_transaction,
		category: m.entity_category,
		contact: m.entity_contact,
		subscription: m.entity_subscription,
		debt: m.entity_debt,
	};

	const typeBadgeVariant: Record<string, 'default' | 'secondary' | 'destructive' | 'outline'> = {
		transaction: 'secondary',
		category: 'outline',
		contact: 'outline',
		subscription: 'secondary',
		debt: 'destructive',
	};

	const shortDate = $derived(shortDateFormatter(data.preferences.locale));

	function formatDate(iso: string) {
		return shortDate.format(new Date(iso));
	}
</script>

<svelte:head><title>{m.trash_title()} · Keenti</title></svelte:head>

<div class="space-y-6">
	<div>
		<h1 class="text-2xl font-semibold tracking-tight">{m.trash_title()}</h1>
		<p class="text-sm text-muted-foreground">
			{m.trash_description()}
		</p>
	</div>

	{#if data.items.length === 0}
		<Empty.Root class="border">
			<Empty.Title>{m.trash_empty_title()}</Empty.Title>
			<Empty.Description>{m.trash_empty_description()}</Empty.Description>
		</Empty.Root>
	{:else}
		<div class="rounded-lg border">
			<Table.Root>
				<Table.Header>
					<Table.Row>
						<Table.Head class="w-[40px]">
							<Checkbox
								checked={allSelected}
								indeterminate={someSelected}
								onclick={toggleAll}
								aria-label={m.trash_select_all()}
							/>
						</Table.Head>
						<Table.Head>{m.common_name()}</Table.Head>
						<Table.Head>{m.common_type()}</Table.Head>
						<Table.Head>{m.common_deleted()}</Table.Head>
						<Table.Head class="w-[160px] text-right">{m.common_actions()}</Table.Head>
					</Table.Row>
				</Table.Header>
				<Table.Body>
					{#each data.items as item (item.entityType + '-' + item.id)}
						<Table.Row data-state={selectedKeys.has(item.entityType + '-' + item.id) ? 'selected' : undefined}>
							<Table.Cell>
								<Checkbox
									checked={selectedKeys.has(item.entityType + '-' + item.id)}
									onclick={() => toggleItem(item)}
									aria-label={m.trash_select_row()}
								/>
							</Table.Cell>
							<Table.Cell class="font-medium">{item.label}</Table.Cell>
							<Table.Cell>
								<Badge variant={typeBadgeVariant[item.entityType] ?? 'secondary'}>
									{typeLabel[item.entityType]?.() ?? item.entityType}
								</Badge>
							</Table.Cell>
							<Table.Cell class="text-muted-foreground text-sm">{formatDate(item.deletedAt)}</Table.Cell>
							<Table.Cell class="text-right">
								<div class="flex justify-end gap-2">
									<form
										method="POST"
										action="?/restore"
										use:kitEnhance={async () => {
											return async ({ result, update }) => {
												if (result.type === 'success') {
													toast.success(m.trash_item_restored({
														type: typeLabel[item.entityType]?.() ?? m.entity_item(),
													}));
													await update();
												} else {
													const msg =
														(result as { data?: { message?: string } }).data?.message ??
														m.trash_restore_failed();
													toast.error(msg);
												}
											};
										}}
									>
										<input type="hidden" name="id" value={item.id} />
										<input type="hidden" name="entityType" value={item.entityType} />
										<Button type="submit" variant="outline" size="sm">{m.common_restore()}</Button>
									</form>
									<Button
										variant="destructive"
										size="sm"
										onclick={() => openPermanentDelete(item)}
									>{m.common_delete()}</Button>
								</div>
							</Table.Cell>
						</Table.Row>
					{/each}
				</Table.Body>
			</Table.Root>
		</div>
	{/if}
</div>

<form
	bind:this={permanentDeleteForm}
	method="POST"
	action="?/permanentDelete"
	class="hidden"
	aria-hidden="true"
	use:kitEnhance={async () => {
		return async ({ result, update }) => {
			if (result.type === 'success') {
				deleteTargetId = null;
				deleteTargetType = '';
				toast.success(m.trash_item_permanently_deleted());
				await update();
			} else {
				const msg =
					(result as { data?: { message?: string } }).data?.message ??
					m.trash_delete_failed();
				toast.error(msg);
			}
		};
	}}
>
	<input type="hidden" name="id" value={deleteTargetId} />
	<input type="hidden" name="entityType" value={deleteTargetType} />
</form>
