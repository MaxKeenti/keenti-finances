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

	const subscriptionSchema = z.object({
		id: z.coerce.number().optional(),
		name: z.string().min(1, 'Name is required'),
		cost: z.coerce.number().positive('Cost must be greater than 0'),
		billingCycle: z.enum(['MONTHLY', 'YEARLY']),
		type: z.enum(['PERSONAL', 'SHARED']),
		categoryId: z.union([z.coerce.number(), z.literal('')]).optional(),
		nextBillingDate: z.string().min(1, 'Next billing date is required'),
	});

	type MemberResponse = {
		id: number;
		subscriptionId: number;
		contactId: number | null;
		contactName: string | null;
		shareAmount: number | null;
		createdAt: string;
	};

	type Subscription = {
		id: number;
		name: string;
		cost: number;
		billingCycle: string;
		type: string;
		categoryId: number | null;
		nextBillingDate: string;
		tokenUuid: string | null;
		createdAt: string;
		members?: MemberResponse[];
	};

	let { data }: { data: PageData } = $props();

	let dialogOpen = $state(false);
	let deleteDialogOpen = $state(false);
	let memberDialogOpen = $state(false);
	let editMode = $state(false);
	let deleteTargetId = $state<number | null>(null);
	let deleteTargetName = $state('');
	let memberTargetSub = $state<Subscription | null>(null);
	let selectedContactId = $state('');

	const today = new Date().toISOString().split('T')[0];

	const sf = superForm(data.form, {
		dataType: 'json',
		validators: zod4Client(subscriptionSchema),
		onResult({ result }) {
			if (result.type === 'success') {
				dialogOpen = false;
				toast.success(editMode ? 'Subscription updated.' : 'Subscription created.');
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
				name: '',
				cost: 0,
				billingCycle: 'MONTHLY',
				type: 'PERSONAL',
				categoryId: '',
				nextBillingDate: today,
			},
		});
		dialogOpen = true;
	}

	function openEdit(sub: Subscription) {
		editMode = true;
		form.set({
			id: sub.id,
			name: sub.name,
			cost: sub.cost,
			billingCycle: sub.billingCycle as 'MONTHLY' | 'YEARLY',
			type: sub.type as 'PERSONAL' | 'SHARED',
			categoryId: sub.categoryId ?? '',
			nextBillingDate: sub.nextBillingDate,
		});
		dialogOpen = true;
	}

	function openDelete(sub: Subscription) {
		deleteTargetId = sub.id;
		deleteTargetName = sub.name;
		deleteDialogOpen = true;
	}

	function openMembers(sub: Subscription) {
		memberTargetSub = sub;
		selectedContactId = '';
		memberDialogOpen = true;
	}

	const fmt = new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' });

	function availableContacts(sub: Subscription) {
		const memberContactIds = new Set((sub.members ?? []).map((m) => m.contactId));
		return data.contacts.filter((c) => !memberContactIds.has(c.id));
	}

	const cycleBadgeClass: Record<string, string> = {
		MONTHLY: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400',
		YEARLY: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400',
	};

	const typeBadgeClass: Record<string, string> = {
		PERSONAL: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
		SHARED: 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-400',
	};
</script>

<div class="space-y-6">
	<div class="flex items-center justify-between">
		<div>
			<h1 class="text-2xl font-semibold tracking-tight">Subscriptions</h1>
			<p class="text-sm text-muted-foreground">Manage your recurring subscriptions and members.</p>
		</div>
		<Button onclick={openCreate}>New Subscription</Button>
	</div>

	{#if data.subscriptions.length === 0}
		<div class="rounded-lg border border-dashed p-8 text-center text-muted-foreground">
			No subscriptions yet. Create one to get started.
		</div>
	{:else}
		<div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
			{#each data.subscriptions as sub (sub.id)}
				<div class="rounded-lg border bg-card p-5 space-y-3 flex flex-col">
					<div class="flex items-start justify-between gap-2">
						<div class="min-w-0">
							<a href="/subscriptions/{sub.id}" class="hover:underline">
								<p class="font-semibold text-base truncate">{sub.name}</p>
							</a>
							<p class="text-xl font-bold text-foreground mt-0.5">{fmt.format(sub.cost)}</p>
						</div>
						<div class="flex flex-col items-end gap-1 shrink-0">
							<span
								class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium {typeBadgeClass[sub.type] ?? ''}"
							>
								{sub.type === 'PERSONAL' ? 'Personal' : 'Shared'}
							</span>
							<span
								class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium {cycleBadgeClass[sub.billingCycle] ?? ''}"
							>
								{sub.billingCycle === 'MONTHLY' ? 'Monthly' : 'Yearly'}
							</span>
						</div>
					</div>

					<div class="text-xs text-muted-foreground space-y-0.5">
						<p>Next billing: <span class="font-medium text-foreground">{sub.nextBillingDate}</span></p>
						{#if sub.type === 'SHARED'}
							<p>Members: <span class="font-medium text-foreground">{(sub.members ?? []).length}</span></p>
						{/if}
					</div>

					<div class="flex gap-2 mt-auto pt-1">
						<Button variant="outline" size="sm" class="flex-1" onclick={() => openEdit(sub)}>Edit</Button>
						{#if sub.type === 'SHARED'}
							<Button variant="outline" size="sm" class="flex-1" onclick={() => openMembers(sub)}>Members</Button>
						{/if}
						<Button variant="destructive" size="sm" onclick={() => openDelete(sub)}>Delete</Button>
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
			<Dialog.Title>{editMode ? 'Edit Subscription' : 'New Subscription'}</Dialog.Title>
			<Dialog.Description>
				{editMode
					? 'Update the subscription details below.'
					: 'Fill in the details for the new subscription.'}
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

			<Form.Field form={sf} name="name">
				<Form.Control>
					{#snippet children({ props })}
						<Form.Label>Name</Form.Label>
						<Input {...props} bind:value={$form.name} placeholder="e.g. Netflix" />
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Form.Field form={sf} name="cost">
				<Form.Control>
					{#snippet children({ props })}
						<Form.Label>Cost (MXN)</Form.Label>
						<Input {...props} type="number" step="0.01" min="0.01" bind:value={$form.cost} />
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<div class="grid grid-cols-2 gap-4">
				<Form.Field form={sf} name="billingCycle">
					<Form.Control>
						{#snippet children({ props })}
							<Form.Label>Billing Cycle</Form.Label>
							<select
								{...props}
								bind:value={$form.billingCycle}
								class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
							>
								<option value="MONTHLY">Monthly</option>
								<option value="YEARLY">Yearly</option>
							</select>
						{/snippet}
					</Form.Control>
					<Form.FieldErrors />
				</Form.Field>

				<Form.Field form={sf} name="type">
					<Form.Control>
						{#snippet children({ props })}
							<Form.Label>Type</Form.Label>
							<select
								{...props}
								bind:value={$form.type}
								class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
							>
								<option value="PERSONAL">Personal</option>
								<option value="SHARED">Shared</option>
							</select>
						{/snippet}
					</Form.Control>
					<Form.FieldErrors />
				</Form.Field>
			</div>

			<Form.Field form={sf} name="categoryId">
				<Form.Control>
					{#snippet children({ props })}
						<Form.Label>Category (optional)</Form.Label>
						<select
							{...props}
							bind:value={$form.categoryId}
							class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
						>
							<option value="">— None —</option>
							{#each data.categories as cat}
								<option value={cat.id}>{cat.name}</option>
							{/each}
						</select>
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Form.Field form={sf} name="nextBillingDate">
				<Form.Control>
					{#snippet children({ props })}
						<Form.Label>Next Billing Date</Form.Label>
						<Input {...props} type="date" bind:value={$form.nextBillingDate} />
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
			<Dialog.Title>Delete Subscription</Dialog.Title>
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
						toast.success('Subscription deleted.');
						await update();
					} else {
						const msg =
							(result as { data?: { message?: string } }).data?.message ??
							'Failed to delete subscription.';
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

<!-- Member management dialog (SHARED subscriptions only) -->
<Dialog.Root bind:open={memberDialogOpen}>
	<Dialog.Content class="sm:max-w-md">
		<Dialog.Header>
			<Dialog.Title>Members — {memberTargetSub?.name}</Dialog.Title>
			<Dialog.Description>Assign or remove contacts from this shared subscription.</Dialog.Description>
		</Dialog.Header>

		{#if memberTargetSub}
			<div class="space-y-4">
				<!-- Current members list -->
				{#if (memberTargetSub.members ?? []).length === 0}
					<p class="text-sm text-muted-foreground">No members yet.</p>
				{:else}
					<ul class="divide-y rounded-md border">
						{#each memberTargetSub.members ?? [] as member (member.id)}
							<li class="flex items-center justify-between px-3 py-2">
								<span class="text-sm">{member.contactName ?? `Contact #${member.contactId}`}</span>
								<form
									method="POST"
									action="?/removeMember"
									use:kitEnhance={async () => {
										return async ({ result, update }) => {
											if (result.type === 'success') {
												toast.success('Member removed.');
												await update();
												memberDialogOpen = false;
											} else {
												const msg =
													(result as { data?: { message?: string } }).data?.message ??
													'Failed to remove member.';
												toast.error(msg);
											}
										};
									}}
								>
									<input type="hidden" name="subscriptionId" value={memberTargetSub?.id} />
									<input type="hidden" name="memberId" value={member.id} />
									<Button type="submit" variant="destructive" size="sm">Remove</Button>
								</form>
							</li>
						{/each}
					</ul>
				{/if}

				<!-- Add member -->
				{#if availableContacts(memberTargetSub).length > 0}
					<form
						method="POST"
						action="?/addMember"
						use:kitEnhance={async () => {
							return async ({ result, update }) => {
								if (result.type === 'success') {
									selectedContactId = '';
									toast.success('Member added.');
									await update();
									memberDialogOpen = false;
								} else {
									const msg =
										(result as { data?: { message?: string } }).data?.message ??
										'Failed to add member.';
									toast.error(msg);
								}
							};
						}}
						class="flex gap-2"
					>
						<input type="hidden" name="subscriptionId" value={memberTargetSub.id} />
						<select
							name="contactId"
							bind:value={selectedContactId}
							class="flex h-9 flex-1 rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
						>
							<option value="">Select contact…</option>
							{#each availableContacts(memberTargetSub) as c}
								<option value={c.id}>{c.name}</option>
							{/each}
						</select>
						<Button type="submit" disabled={!selectedContactId}>Add</Button>
					</form>
				{:else}
					<p class="text-sm text-muted-foreground">All contacts are already members.</p>
				{/if}
			</div>
		{/if}

		<Dialog.Footer>
			<Button variant="outline" onclick={() => (memberDialogOpen = false)}>Close</Button>
		</Dialog.Footer>
	</Dialog.Content>
</Dialog.Root>
