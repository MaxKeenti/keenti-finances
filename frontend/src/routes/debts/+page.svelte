<script lang="ts">
	import { superForm } from 'sveltekit-superforms';
	import { zod4Client } from 'sveltekit-superforms/adapters';
	import { z } from 'zod';
	import { toast } from 'svelte-sonner';
	import { enhance as kitEnhance } from '$app/forms';
	import * as Dialog from '$lib/components/ui/dialog';
	import * as Form from '$lib/components/ui/form';
	import { Input } from '$lib/components/ui/input';
	import { Button } from '$lib/components/ui/button';
	import type { PageData } from './$types';

	const debtSchema = z.object({
		id: z.coerce.number().optional(),
		contactId: z.coerce.number().positive('Contact is required'),
		description: z.string().min(1, 'Description is required'),
		totalAmount: z.coerce.number().positive('Total amount must be greater than 0'),
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
		createdAt: string;
	};

	let { data }: { data: PageData } = $props();

	let dialogOpen = $state(false);
	let deleteDialogOpen = $state(false);
	let editMode = $state(false);
	let deleteTargetId = $state<number | null>(null);
	let deleteTargetDescription = $state('');

	const sf = superForm(data.form, {
		validators: zod4Client(debtSchema),
		onResult({ result }) {
			if (result.type === 'success') {
				dialogOpen = false;
				toast.success(editMode ? 'Debt updated.' : 'Debt created.');
			} else if (result.type === 'failure') {
				const msg = (result.data as Record<string, unknown> | undefined)?.form as
					| { message?: string }
					| undefined;
				if (msg?.message) toast.error(msg.message);
			}
		},
	});
	const { form, errors, enhance, submitting, message } = sf;

	function openCreate() {
		editMode = false;
		sf.reset({
			data: { contactId: 0, description: '', totalAmount: 0 },
		});
		dialogOpen = true;
	}

	function openEdit(debt: Debt) {
		editMode = true;
		form.set({
			id: debt.id,
			contactId: debt.contactId ?? 0,
			description: debt.description,
			totalAmount: debt.totalAmount,
		});
		dialogOpen = true;
	}

	function openDelete(debt: Debt) {
		deleteTargetId = debt.id;
		deleteTargetDescription = debt.description;
		deleteDialogOpen = true;
	}

	const fmt = new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' });

	const statusBadgeClass: Record<string, string> = {
		ACTIVE: 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-400',
		PAID: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
	};
</script>

<div class="space-y-6">
	<div class="flex items-center justify-between">
		<div>
			<h1 class="text-2xl font-semibold tracking-tight">Debts</h1>
			<p class="text-sm text-muted-foreground">Track embroidery job debts and payments per debtor.</p>
		</div>
		<Button onclick={openCreate}>New Debt</Button>
	</div>

	{#if data.debts.length === 0}
		<div class="rounded-lg border border-dashed p-8 text-center text-muted-foreground">
			No debts yet. Create one to get started.
		</div>
	{:else}
		<div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
			{#each data.debts as debt (debt.id)}
				<div class="rounded-lg border bg-card p-5 space-y-3 flex flex-col">
					<div class="flex items-start justify-between gap-2">
						<div class="min-w-0">
							<a href="/debts/{debt.id}" class="hover:underline">
								<p class="font-semibold text-base truncate">
									{debt.contactName ?? `Contact #${debt.contactId}`}
								</p>
							</a>
							<p class="text-sm text-muted-foreground truncate mt-0.5">{debt.description}</p>
						</div>
						<span
							class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium shrink-0 {statusBadgeClass[debt.status] ?? ''}"
						>
							{debt.status}
						</span>
					</div>

					<div class="space-y-1 text-sm">
						<div class="flex justify-between">
							<span class="text-muted-foreground">Total</span>
							<span class="font-medium">{fmt.format(debt.totalAmount)}</span>
						</div>
						<div class="flex justify-between">
							<span class="text-muted-foreground">Paid</span>
							<span class="font-medium text-green-600 dark:text-green-400">
								{fmt.format(debt.totalPaid)}
							</span>
						</div>
						<div class="flex justify-between border-t pt-1">
							<span class="text-muted-foreground font-medium">Remaining</span>
							<span
								class="font-bold {debt.status === 'PAID'
									? 'text-green-600 dark:text-green-400'
									: 'text-amber-600 dark:text-amber-400'}"
							>
								{fmt.format(debt.remaining)}
							</span>
						</div>
					</div>

					<div class="flex gap-2 mt-auto pt-1">
						<Button variant="outline" size="sm" class="flex-1" onclick={() => openEdit(debt)}>
							Edit
						</Button>
						<Button variant="destructive" size="sm" onclick={() => openDelete(debt)}>Delete</Button>
					</div>
				</div>
			{/each}
		</div>
	{/if}
</div>

<!-- Create / Edit dialog -->
<Dialog.Root bind:open={dialogOpen}>
	<Dialog.Content class="sm:max-w-md">
		<Dialog.Header>
			<Dialog.Title>{editMode ? 'Edit Debt' : 'New Debt'}</Dialog.Title>
			<Dialog.Description>
				{editMode ? 'Update the debt details below.' : 'Fill in the details for the new debt.'}
			</Dialog.Description>
		</Dialog.Header>

		{#if $message}
			<div class="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
				{$message}
			</div>
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
						<Form.Label>Debtor (Contact)</Form.Label>
						<select
							{...props}
							bind:value={$form.contactId}
							class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
						>
							<option value={0}>— Select contact —</option>
							{#each data.contacts as c}
								<option value={c.id}>{c.name}</option>
							{/each}
						</select>
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Form.Field form={sf} name="description">
				<Form.Control>
					{#snippet children({ props })}
						<Form.Label>Description</Form.Label>
						<textarea
							{...props}
							bind:value={$form.description}
							rows={3}
							placeholder="e.g. Embroidery job — 5 polo shirts"
							class="flex min-h-[72px] w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring resize-none"
						></textarea>
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Form.Field form={sf} name="totalAmount">
				<Form.Control>
					{#snippet children({ props })}
						<Form.Label>Total Amount (MXN)</Form.Label>
						<Input {...props} type="number" step="0.01" min="0.01" bind:value={$form.totalAmount} />
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Dialog.Footer>
				<Button type="button" variant="outline" onclick={() => (dialogOpen = false)}>Cancel</Button>
				<Button type="submit" disabled={$submitting}>
					{$submitting ? 'Saving…' : editMode ? 'Update' : 'Create'}
				</Button>
			</Dialog.Footer>
		</form>
	</Dialog.Content>
</Dialog.Root>

<!-- Delete confirmation dialog -->
<Dialog.Root bind:open={deleteDialogOpen}>
	<Dialog.Content class="sm:max-w-md">
		<Dialog.Header>
			<Dialog.Title>Delete Debt</Dialog.Title>
			<Dialog.Description>
				Are you sure you want to delete the debt for <strong>{deleteTargetDescription}</strong>? This
				action cannot be undone.
			</Dialog.Description>
		</Dialog.Header>
		<form
			method="POST"
			action="?/delete"
			use:kitEnhance={async () => {
				return async ({ result, update }) => {
					if (result.type === 'success') {
						deleteDialogOpen = false;
						toast.success('Debt deleted.');
						await update();
					} else {
						const msg =
							(result as { data?: { message?: string } }).data?.message ?? 'Failed to delete debt.';
						toast.error(msg);
					}
				};
			}}
		>
			<input type="hidden" name="id" value={deleteTargetId} />
			<Dialog.Footer>
				<Button type="button" variant="outline" onclick={() => (deleteDialogOpen = false)}>
					Cancel
				</Button>
				<Button type="submit" variant="destructive">Delete</Button>
			</Dialog.Footer>
		</form>
	</Dialog.Content>
</Dialog.Root>
