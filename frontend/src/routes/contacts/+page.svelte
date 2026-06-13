<script lang="ts">
	import { superForm } from 'sveltekit-superforms';
	import { zod4Client } from 'sveltekit-superforms/adapters';
	import { z } from 'zod';
	import { toast } from 'svelte-sonner';
	import { enhance as kitEnhance } from '$app/forms';
	import { submitWithAdaptiveConfirm } from '$lib/components/adaptive-confirm';
	import * as Table from '$lib/components/ui/table';
	import * as Dialog from '$lib/components/ui/dialog';
	import * as Form from '$lib/components/ui/form';
	import * as Alert from '$lib/components/ui/alert';
	import * as Empty from '$lib/components/ui/empty';
	import { Input } from '$lib/components/ui/input';
	import { Button } from '$lib/components/ui/button';
	import { m } from '$lib/paraglide/messages.js';
	import type { PageData } from './$types';

	const contactSchema = z.object({
		id: z.coerce.number().optional(),
		name: z.string().min(1, m.validation_name_required()),
		phone: z.string().optional(),
		email: z.string().email(m.validation_email_invalid()).optional().or(z.literal('')),
	});

	let { data }: { data: PageData } = $props();

	let dialogOpen = $state(false);
	let editMode = $state(false);
	let deleteTargetId = $state<number | null>(null);
	let deleteForm = $state<HTMLFormElement | null>(null);

	const sf = superForm(data.form, {
		dataType: 'json',
		validators: zod4Client(contactSchema),
		onResult({ result }) {
			if (result.type === 'success') {
				dialogOpen = false;
				toast.success(editMode ? m.contacts_updated() : m.contacts_created());
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

	async function openDelete(contact: { id: number; name: string }) {
		deleteTargetId = contact.id;
		await submitWithAdaptiveConfirm(deleteForm, {
			title: m.contacts_delete_title(),
			description: `${m.delete_confirm_prefix()} ${contact.name}${m.delete_confirm_suffix()}`,
			confirmLabel: m.common_delete(),
			cancelLabel: m.common_cancel(),
			destructive: true,
		});
	}
</script>

<div class="space-y-6">
	<div class="flex items-center justify-between">
		<div>
			<h1 class="text-2xl font-semibold tracking-tight">{m.contacts_title()}</h1>
			<p class="text-sm text-muted-foreground">{m.contacts_description()}</p>
		</div>
		<Button onclick={openCreate}>{m.contacts_new()}</Button>
	</div>

	{#if data.contacts.length === 0}
		<Empty.Root class="border">
			<Empty.Title>{m.contacts_empty_title()}</Empty.Title>
			<Empty.Description>{m.contacts_empty_description()}</Empty.Description>
		</Empty.Root>
	{:else}
		<div class="rounded-lg border">
			<Table.Root>
				<Table.Header>
					<Table.Row>
						<Table.Head>{m.common_name()}</Table.Head>
						<Table.Head>{m.common_phone()}</Table.Head>
						<Table.Head>{m.common_email()}</Table.Head>
						<Table.Head class="w-[120px] text-right">{m.common_actions()}</Table.Head>
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
									<Button variant="outline" size="sm" onclick={() => openEdit(contact)}>{m.common_edit()}</Button>
									<Button variant="destructive" size="sm" onclick={() => openDelete(contact)}
										>{m.common_delete()}</Button
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
			<Dialog.Title>{editMode ? m.contacts_edit_title() : m.contacts_new_title()}</Dialog.Title>
			<Dialog.Description>
				{editMode ? m.contacts_edit_description() : m.contacts_new_description()}
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
						<Form.Label>{m.common_name()}</Form.Label>
						<Input {...props} bind:value={$form.name} placeholder={m.contacts_placeholder_name()} />
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Form.Field form={sf} name="phone">
				<Form.Control>
					{#snippet children({ props })}
						<Form.Label>{m.common_phone()} <span class="text-muted-foreground">{m.common_optional()}</span></Form.Label>
						<Input {...props} bind:value={$form.phone} placeholder={m.contacts_placeholder_phone()} />
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Form.Field form={sf} name="email">
				<Form.Control>
					{#snippet children({ props })}
						<Form.Label>{m.common_email()} <span class="text-muted-foreground">{m.common_optional()}</span></Form.Label>
						<Input {...props} type="email" bind:value={$form.email} placeholder={m.contacts_placeholder_email()} />
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

<form
	bind:this={deleteForm}
	method="POST"
	action="?/delete"
	class="hidden"
	aria-hidden="true"
	use:kitEnhance={async () => {
		return async ({ result, update }) => {
			if (result.type === 'success') {
				deleteTargetId = null;
				toast.success(m.contacts_trashed());
				await update();
			} else {
				const msg =
					(result as { data?: { message?: string } }).data?.message ??
					m.contacts_delete_failed();
				toast.error(msg);
			}
		};
	}}
>
	<input type="hidden" name="id" value={deleteTargetId} />
</form>
