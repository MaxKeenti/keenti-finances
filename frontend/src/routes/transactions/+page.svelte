<script lang="ts">
	import { superForm } from 'sveltekit-superforms';
	import { zod4Client } from 'sveltekit-superforms/adapters';
	import { z } from 'zod';
	import { toast } from 'svelte-sonner';
	import { enhance as kitEnhance } from '$app/forms';
	import * as Table from '$lib/components/ui/table';
	import * as Dialog from '$lib/components/ui/dialog';
	import * as Form from '$lib/components/ui/form';
	import { Input } from '$lib/components/ui/input';
	import { Button } from '$lib/components/ui/button';
	import type { PageData } from './$types';

	const transactionSchema = z.object({
		id: z.coerce.number().optional(),
		amount: z.coerce.number().positive('Amount must be greater than 0'),
		direction: z.enum(['INGRESS', 'EGRESS']),
		description: z.string().max(500).optional(),
		transactionDate: z.string().min(1, 'Date is required'),
		categoryId: z.coerce.number().min(1, 'Category is required'),
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
		validators: zod4Client(transactionSchema),
		onResult({ result }) {
			if (result.type === 'success') {
				dialogOpen = false;
				toast.success(editMode ? 'Transaction updated.' : 'Transaction created.');
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

	function formatAmount(amount: number, direction: 'INGRESS' | 'EGRESS'): string {
		const prefix = direction === 'INGRESS' ? '+' : '-';
		return `${prefix}${fmt.format(amount)}`;
	}
</script>

<div class="space-y-6">
	<div class="flex items-center justify-between">
		<div>
			<h1 class="text-2xl font-semibold tracking-tight">Transactions</h1>
			<p class="text-sm text-muted-foreground">Track your income and expenses.</p>
		</div>
		<Button onclick={openCreate} disabled={data.categories.length === 0}>New Transaction</Button>
	</div>

	{#if data.transactions.length === 0}
		<div class="rounded-lg border border-dashed p-8 text-center text-muted-foreground">
			No transactions yet. Create one to get started.
		</div>
	{:else}
		<div class="rounded-lg border">
			<Table.Root>
				<Table.Header>
					<Table.Row>
						<Table.Head>Date</Table.Head>
						<Table.Head>Description</Table.Head>
						<Table.Head>Amount</Table.Head>
						<Table.Head>Category</Table.Head>
						<Table.Head>Contact</Table.Head>
						<Table.Head class="w-[120px] text-right">Actions</Table.Head>
					</Table.Row>
				</Table.Header>
				<Table.Body>
					{#each data.transactions as tx (tx.id)}
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
							<Table.Cell>{tx.categoryName ?? '—'}</Table.Cell>
							<Table.Cell>{tx.contactName ?? '—'}</Table.Cell>
							<Table.Cell class="text-right">
								<div class="flex justify-end gap-2">
									<Button variant="outline" size="sm" onclick={() => openEdit(tx)}>Edit</Button>
									<Button variant="destructive" size="sm" onclick={() => openDelete(tx)}>Delete</Button>
								</div>
							</Table.Cell>
						</Table.Row>
					{/each}
				</Table.Body>
			</Table.Root>
		</div>
	{/if}
</div>

<!-- Create / Edit dialog -->
<Dialog.Root bind:open={dialogOpen}>
	<Dialog.Content class="sm:max-w-lg">
		<Dialog.Header>
			<Dialog.Title>{editMode ? 'Edit Transaction' : 'New Transaction'}</Dialog.Title>
			<Dialog.Description>
				{editMode ? 'Update the transaction details below.' : 'Fill in the details for the new transaction.'}
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

			<div class="grid grid-cols-2 gap-4">
				<Form.Field form={sf} name="amount">
					<Form.Control>
						{#snippet children({ props })}
							<Form.Label>Amount (MXN)</Form.Label>
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
							<Form.Label>Direction</Form.Label>
							<select
								{...props}
								bind:value={$form.direction}
								class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
							>
								<option value="INGRESS">Ingress (income)</option>
								<option value="EGRESS">Egress (expense)</option>
							</select>
						{/snippet}
					</Form.Control>
					<Form.FieldErrors />
				</Form.Field>
			</div>

			<Form.Field form={sf} name="description">
				<Form.Control>
					{#snippet children({ props })}
						<Form.Label>Description</Form.Label>
						<Input {...props} bind:value={$form.description} placeholder="e.g. Monthly salary" />
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Form.Field form={sf} name="transactionDate">
				<Form.Control>
					{#snippet children({ props })}
						<Form.Label>Date</Form.Label>
						<Input {...props} type="date" bind:value={$form.transactionDate} />
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Form.Field form={sf} name="categoryId">
				<Form.Control>
					{#snippet children({ props })}
						<Form.Label>Category</Form.Label>
						<select
							{...props}
							bind:value={$form.categoryId}
							class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
						>
							{#each data.categories as cat (cat.id)}
								<option value={cat.id}>{cat.name}</option>
							{/each}
						</select>
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Form.Field form={sf} name="contactId">
				<Form.Control>
					{#snippet children({ props })}
						<Form.Label>Contact (optional)</Form.Label>
						<select
							{...props}
							bind:value={$form.contactId}
							class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
						>
							<option value="">— None —</option>
							{#each data.contacts as con (con.id)}
								<option value={con.id}>{con.name}</option>
							{/each}
						</select>
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
			<Dialog.Title>Delete Transaction</Dialog.Title>
			<Dialog.Description>
				Are you sure you want to delete <strong>{deleteTargetDesc}</strong>? This action cannot be
				undone.
			</Dialog.Description>
		</Dialog.Header>
		<form
			method="POST"
			action="?/delete"
			use:kitEnhance={async () => {
				return async ({ result, update }) => {
					if (result.type === 'success') {
						deleteDialogOpen = false;
						toast.success('Transaction deleted.');
						await update();
					} else {
						const msg =
							(result as { data?: { message?: string } }).data?.message ??
							'Failed to delete transaction.';
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
