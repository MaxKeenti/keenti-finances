<script lang="ts">
	import { enhance as kitEnhance } from '$app/forms';
	import { toast } from 'svelte-sonner';
	import * as Table from '$lib/components/ui/table';
	import * as Dialog from '$lib/components/ui/dialog';
	import * as Empty from '$lib/components/ui/empty';
	import { Button } from '$lib/components/ui/button';
	import { Badge } from '$lib/components/ui/badge';
	import { m } from '$lib/paraglide/messages.js';
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

	function formatDate(iso: string) {
		return new Date(iso).toLocaleDateString('es-MX', {
			year: 'numeric',
			month: 'short',
			day: 'numeric',
		});
	}
</script>

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
						<Table.Head>{m.common_name()}</Table.Head>
						<Table.Head>{m.common_type()}</Table.Head>
						<Table.Head>{m.common_deleted()}</Table.Head>
						<Table.Head class="w-[160px] text-right">{m.common_actions()}</Table.Head>
					</Table.Row>
				</Table.Header>
				<Table.Body>
					{#each data.items as item (item.entityType + '-' + item.id)}
						<Table.Row>
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

<!-- Permanent delete confirmation -->
<Dialog.Root bind:open={permanentDeleteDialogOpen}>
	<Dialog.Content class="sm:max-w-md">
		<Dialog.Header>
			<Dialog.Title>{m.trash_permanently_delete_title()}</Dialog.Title>
			<Dialog.Description>
				{m.delete_confirm_permanent_prefix()} <strong>{deleteTargetLabel}</strong>{m.delete_confirm_suffix()}
			</Dialog.Description>
		</Dialog.Header>
		<form
			method="POST"
			action="?/permanentDelete"
			use:kitEnhance={async () => {
				return async ({ result, update }) => {
					if (result.type === 'success') {
						permanentDeleteDialogOpen = false;
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
			<Dialog.Footer>
				<Button
					type="button"
					variant="outline"
					onclick={() => (permanentDeleteDialogOpen = false)}
				>
					{m.common_cancel()}
				</Button>
				<Button type="submit" variant="destructive">{m.common_delete_permanently()}</Button>
			</Dialog.Footer>
		</form>
	</Dialog.Content>
</Dialog.Root>
