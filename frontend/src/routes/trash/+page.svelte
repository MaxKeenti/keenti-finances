<script lang="ts">
	import { enhance as kitEnhance } from '$app/forms';
	import { toast } from 'svelte-sonner';
	import * as Table from '$lib/components/ui/table';
	import * as Dialog from '$lib/components/ui/dialog';
	import * as Empty from '$lib/components/ui/empty';
	import { Button } from '$lib/components/ui/button';
	import { Badge } from '$lib/components/ui/badge';
	import type { PageData } from './$types';

	let { data }: { data: PageData } = $props();

	let permanentDeleteDialogOpen = $state(false);
	let deleteTargetId = $state<number | null>(null);
	let deleteTargetType = $state('');
	let deleteTargetLabel = $state('');

	function openPermanentDelete(item: { id: number; entityType: string; label: string }) {
		deleteTargetId = item.id;
		deleteTargetType = item.entityType;
		deleteTargetLabel = item.label;
		permanentDeleteDialogOpen = true;
	}

	const typeLabel: Record<string, string> = {
		transaction: 'Transaction',
		category: 'Category',
		contact: 'Contact',
		subscription: 'Subscription',
		debt: 'Debt',
	};

	const typeBadgeVariant: Record<string, 'default' | 'secondary' | 'destructive' | 'outline'> = {
		transaction: 'secondary',
		category: 'outline',
		contact: 'outline',
		subscription: 'secondary',
		debt: 'destructive',
	};

	function formatDate(iso: string) {
		return new Date(iso).toLocaleDateString(undefined, {
			year: 'numeric',
			month: 'short',
			day: 'numeric',
		});
	}
</script>

<div class="space-y-6">
	<div>
		<h1 class="text-2xl font-semibold tracking-tight">Trash</h1>
		<p class="text-sm text-muted-foreground">
			Items moved to trash are kept here. Restore them or permanently delete.
		</p>
	</div>

	{#if data.items.length === 0}
		<Empty.Root class="border">
			<Empty.Title>Trash is empty.</Empty.Title>
			<Empty.Description>Deleted items will appear here.</Empty.Description>
		</Empty.Root>
	{:else}
		<div class="rounded-lg border">
			<Table.Root>
				<Table.Header>
					<Table.Row>
						<Table.Head>Name</Table.Head>
						<Table.Head>Type</Table.Head>
						<Table.Head>Deleted</Table.Head>
						<Table.Head class="w-[160px] text-right">Actions</Table.Head>
					</Table.Row>
				</Table.Header>
				<Table.Body>
					{#each data.items as item (item.entityType + '-' + item.id)}
						<Table.Row>
							<Table.Cell class="font-medium">{item.label}</Table.Cell>
							<Table.Cell>
								<Badge variant={typeBadgeVariant[item.entityType] ?? 'secondary'}>
									{typeLabel[item.entityType] ?? item.entityType}
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
													toast.success(`${typeLabel[item.entityType] ?? 'Item'} restored.`);
													await update();
												} else {
													const msg =
														(result as { data?: { message?: string } }).data?.message ??
														'Failed to restore item.';
													toast.error(msg);
												}
											};
										}}
									>
										<input type="hidden" name="id" value={item.id} />
										<input type="hidden" name="entityType" value={item.entityType} />
										<Button type="submit" variant="outline" size="sm">Restore</Button>
									</form>
									<Button
										variant="destructive"
										size="sm"
										onclick={() => openPermanentDelete(item)}
									>Delete</Button>
								</div>
							</Table.Cell>
						</Table.Row>
					{/each}
				</Table.Body>
			</Table.Root>
		</div>
	{/if}
</div>

<!-- Permanent delete confirmation -->
<Dialog.Root bind:open={permanentDeleteDialogOpen}>
	<Dialog.Content class="sm:max-w-md">
		<Dialog.Header>
			<Dialog.Title>Permanently Delete</Dialog.Title>
			<Dialog.Description>
				Are you sure you want to permanently delete <strong>{deleteTargetLabel}</strong>?
				This action cannot be undone.
			</Dialog.Description>
		</Dialog.Header>
		<form
			method="POST"
			action="?/permanentDelete"
			use:kitEnhance={async () => {
				return async ({ result, update }) => {
					if (result.type === 'success') {
						permanentDeleteDialogOpen = false;
						toast.success('Item permanently deleted.');
						await update();
					} else {
						const msg =
							(result as { data?: { message?: string } }).data?.message ??
							'Failed to delete item.';
						toast.error(msg);
					}
				};
			}}
		>
			<input type="hidden" name="id" value={deleteTargetId} />
			<input type="hidden" name="entityType" value={deleteTargetType} />
			<Dialog.Footer>
				<Button
					type="button"
					variant="outline"
					onclick={() => (permanentDeleteDialogOpen = false)}
				>
					Cancel
				</Button>
				<Button type="submit" variant="destructive">Delete permanently</Button>
			</Dialog.Footer>
		</form>
	</Dialog.Content>
</Dialog.Root>
