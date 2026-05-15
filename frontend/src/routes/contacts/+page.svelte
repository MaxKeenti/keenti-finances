<script lang="ts">
	import { superForm } from 'sveltekit-superforms';
	import { zod4Client } from 'sveltekit-superforms/adapters';
	import { z } from 'zod';
	import { toast } from 'svelte-sonner';
	import { enhance as kitEnhance } from '$app/forms';
	import * as Table from '$lib/components/ui/table';
	import * as Dialog from '$lib/components/ui/dialog';
	import * as Form from '$lib/components/ui/form';
	import * as Alert from '$lib/components/ui/alert';
	import * as Empty from '$lib/components/ui/empty';
	import { Input } from '$lib/components/ui/input';
	import { Button } from '$lib/components/ui/button';
	import type { PageData } from './$types';

	const contactSchema = z.object({
		id: z.coerce.number().optional(),
		name: z.string().min(1, 'Name is required'),
		phone: z.string().optional(),
		email: z.string().email('Invalid email format').optional().or(z.literal('')),
	});

	let { data }: { data: PageData } = $props();

	let dialogOpen = $state(false);
	let deleteDialogOpen = $state(false);
	let editMode = $state(false);
	let deleteTargetId = $state<number | null>(null);
	let deleteTargetName = $state('');

	const sf = superForm(data.form, {
		dataType: 'json',
		validators: zod4Client(contactSchema),
		onResult({ result }) {
			if (result.type === 'success') {
				dialogOpen = false;
				toast.success(editMode ? 'Contact updated.' : 'Contact created.');
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
		sf.reset({ data: { name: '', phone: '', email: '' } });
		dialogOpen = true;
	}

	function openEdit(contact: {
		id: number;
		name: string;
		phone: string | null;
		email: string | null;
	}) {
		editMode = true;
		form.set({
			id: contact.id,
			name: contact.name,
			phone: contact.phone ?? '',
			email: contact.email ?? '',
		});
		dialogOpen = true;
	}

	function openDelete(contact: { id: number; name: string }) {
		deleteTargetId = contact.id;
		deleteTargetName = contact.name;
		deleteDialogOpen = true;
	}
</script>

<div class="space-y-6">
	<div class="flex items-center justify-between">
		<div>
			<h1 class="text-2xl font-semibold tracking-tight">Contacts</h1>
			<p class="text-sm text-muted-foreground">Manage your payees and recipients.</p>
		</div>
		<Button onclick={openCreate}>New Contact</Button>
	</div>

	{#if data.contacts.length === 0}
		<Empty.Root class="border">
			<Empty.Title>No contacts yet.</Empty.Title>
			<Empty.Description>Create one to get started.</Empty.Description>
		</Empty.Root>
	{:else}
		<div class="rounded-lg border">
			<Table.Root>
				<Table.Header>
					<Table.Row>
						<Table.Head>Name</Table.Head>
						<Table.Head>Phone</Table.Head>
						<Table.Head>Email</Table.Head>
						<Table.Head class="w-[120px] text-right">Actions</Table.Head>
					</Table.Row>
				</Table.Header>
				<Table.Body>
					{#each data.contacts as contact (contact.id)}
						<Table.Row>
							<Table.Cell class="font-medium">{contact.name}</Table.Cell>
							<Table.Cell class="text-muted-foreground">{contact.phone ?? '—'}</Table.Cell>
							<Table.Cell class="text-muted-foreground">{contact.email ?? '—'}</Table.Cell>
							<Table.Cell class="text-right">
								<div class="flex justify-end gap-2">
									<Button variant="outline" size="sm" onclick={() => openEdit(contact)}>Edit</Button>
									<Button variant="destructive" size="sm" onclick={() => openDelete(contact)}
										>Delete</Button
									>
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
	<Dialog.Content class="sm:max-w-md">
		<Dialog.Header>
			<Dialog.Title>{editMode ? 'Edit Contact' : 'New Contact'}</Dialog.Title>
			<Dialog.Description>
				{editMode ? 'Update the contact details below.' : 'Fill in the details for the new contact.'}
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

			<Form.Field form={sf} name="name">
				<Form.Control>
					{#snippet children({ props })}
						<Form.Label>Name</Form.Label>
						<Input {...props} bind:value={$form.name} placeholder="e.g. John Doe" />
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Form.Field form={sf} name="phone">
				<Form.Control>
					{#snippet children({ props })}
						<Form.Label>Phone <span class="text-muted-foreground">(optional)</span></Form.Label>
						<Input {...props} bind:value={$form.phone} placeholder="e.g. +1 555 0100" />
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Form.Field form={sf} name="email">
				<Form.Control>
					{#snippet children({ props })}
						<Form.Label>Email <span class="text-muted-foreground">(optional)</span></Form.Label>
						<Input {...props} type="email" bind:value={$form.email} placeholder="e.g. john@example.com" />
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
			<Dialog.Title>Delete Contact</Dialog.Title>
			<Dialog.Description>
				Are you sure you want to delete <strong>{deleteTargetName}</strong>? This action cannot be
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
						toast.success('Contact deleted.');
						await update();
					} else {
						const msg =
							(result as { data?: { message?: string } }).data?.message ??
							'Failed to delete contact.';
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
