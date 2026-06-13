<script lang="ts">
	import { superForm } from 'sveltekit-superforms';
	import { zod4Client } from 'sveltekit-superforms/adapters';
	import { z } from 'zod';
	import { toast } from 'svelte-sonner';
	import { enhance as kitEnhance } from '$app/forms';
	import { goto } from '$app/navigation';
	import { submitWithAdaptiveConfirm } from '$lib/components/adaptive-confirm';
	import * as Card from '$lib/components/ui/card';
	import * as Dialog from '$lib/components/ui/dialog';
	import * as Form from '$lib/components/ui/form';
	import * as Alert from '$lib/components/ui/alert';
	import { Input } from '$lib/components/ui/input';
	import { Button } from '$lib/components/ui/button';
	import { NativeSelect } from '$lib/components/native-select';
	import { NativeDatePicker } from '$lib/components/native-date-picker';
	import { CategoryBadge } from '$lib/components/ui/category-badge';
	import { mxnFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
	import type { PageData } from './$types';

	const transactionSchema = z.object({
		id: z.coerce.number().optional(),
		amount: z.coerce.number().positive(m.validation_amount_positive()),
		direction: z.enum(['INGRESS', 'EGRESS']),
		description: z.string().max(500).optional(),
		transactionDate: z.string().min(1, m.validation_date_required()),
		categoryId: z.coerce.number().min(1, m.validation_category_required()),
		contactId: z.union([z.coerce.number(), z.literal('')]).optional(),
	});

	let { data }: { data: PageData } = $props();

	let editDialogOpen = $state(false);
	let deleteForm = $state<HTMLFormElement | null>(null);

	const fmt = $derived(mxnFormatter(data.preferences.locale));

	function formatAmount(amount: number, direction: 'INGRESS' | 'EGRESS'): string {
		const prefix = direction === 'INGRESS' ? '+' : '-';
		return `${prefix}${fmt.format(amount)}`;
	}

	async function confirmDelete() {
		await submitWithAdaptiveConfirm(deleteForm, {
			title: m.transactions_delete_title(),
			description: `${m.delete_confirm_prefix()} ${
				tx.description || formatAmount(tx.amount, tx.direction as 'INGRESS' | 'EGRESS')
			}${m.delete_confirm_suffix()}`,
			confirmLabel: m.common_delete(),
			cancelLabel: m.common_cancel(),
			destructive: true,
		});
	}

	const sf = superForm(data.form, {
		dataType: 'json',
		validators: zod4Client(transactionSchema),
		onResult({ result }) {
			if (result.type === 'success') {
				editDialogOpen = false;
				toast.success(m.transactions_updated());
			} else if (result.type === 'failure') {
				const msg = (result.data as Record<string, unknown> | undefined)?.form as
					| { message?: string }
					| undefined;
				if (msg?.message) toast.error(msg.message);
				else toast.error(m.transactions_update_failed());
			}
		},
	});
	const { form, enhance, submitting, message } = sf;

	const filteredCategories = $derived(
		data.categories
			.filter((c) => c.type === $form.direction || c.type === 'BOTH')
			.sort((a, b) => a.name.localeCompare(b.name)),
	);

	const sortedContacts = $derived(
		[...data.contacts].sort((a, b) => a.name.localeCompare(b.name)),
	);

	$effect(() => {
		const ids = filteredCategories.map((c) => c.id);
		if ($form.categoryId && !ids.includes($form.categoryId)) {
			$form.categoryId = ids[0] ?? 0;
		}
	});

	const tx = $derived(data.transaction);
	const amountClass = $derived(
		tx.direction === 'INGRESS'
			? 'text-green-600 dark:text-green-400'
			: 'text-red-600 dark:text-red-400',
	);

	const directionLabel = $derived(
		tx.direction === 'INGRESS'
			? m.direction_ingress()
			: tx.direction === 'EGRESS'
				? m.direction_egress()
				: tx.direction,
	);
</script>

<div class="space-y-6 max-w-2xl">
	<!-- Back link -->
	<Button variant="link" href="/transactions" class="h-auto p-0 text-muted-foreground hover:text-foreground">
		{m.common_back_to_transactions()}
	</Button>

	<!-- Detail card -->
	<Card.Root>
		<Card.Content class="space-y-5 pt-6">
			<!-- Amount + direction -->
			<div class="flex flex-wrap items-start justify-between gap-3">
				<div>
					<p class="text-sm text-muted-foreground">{m.common_amount()}</p>
					<p class="text-3xl font-bold tabular-nums {amountClass}">
						{formatAmount(tx.amount, tx.direction as 'INGRESS' | 'EGRESS')}
					</p>
				</div>
				<span
					class="text-xs font-medium rounded-full px-2.5 py-0.5 border {tx.direction === 'INGRESS'
						? 'border-green-500 text-green-700 dark:text-green-400'
						: 'border-red-500 text-red-700 dark:text-red-400'}"
				>
					{directionLabel}
				</span>
			</div>

			<!-- Description -->
			<div>
				<p class="text-sm text-muted-foreground">{m.common_description()}</p>
				<p class="text-base">{tx.description ?? '—'}</p>
			</div>

			<!-- Date -->
			<div>
				<p class="text-sm text-muted-foreground">{m.common_date()}</p>
				<p class="text-base tabular-nums">{tx.transactionDate}</p>
			</div>

			<!-- Category -->
			<div>
				<p class="text-sm text-muted-foreground">{m.common_category()}</p>
				<div class="mt-1">
					{#if tx.categoryName}
						<CategoryBadge hue={tx.categoryHue} name={tx.categoryName} direction={tx.direction} />
					{:else}
						<span class="text-muted-foreground">—</span>
					{/if}
				</div>
			</div>

			<!-- Contact -->
			<div>
				<p class="text-sm text-muted-foreground">{m.common_contact()}</p>
				<p class="text-base">{tx.contactName ?? '—'}</p>
			</div>

			<!-- Actions -->
			<div class="flex gap-3 pt-2">
				<Button onclick={() => (editDialogOpen = true)}>{m.common_edit()}</Button>
				<Button variant="destructive" onclick={confirmDelete}>{m.common_delete()}</Button>
			</div>
		</Card.Content>
	</Card.Root>
</div>

<!-- Edit dialog -->
<Dialog.Root bind:open={editDialogOpen}>
	<Dialog.Content class="sm:max-w-lg">
		<Dialog.Header>
			<Dialog.Title>{m.transactions_edit_title()}</Dialog.Title>
			<Dialog.Description>{m.transactions_edit_description()}</Dialog.Description>
		</Dialog.Header>

		{#if $message}
			<Alert.Root variant="destructive">
				<Alert.Description>{$message}</Alert.Description>
			</Alert.Root>
		{/if}

		<form method="POST" action="?/update" use:enhance class="grid gap-4">
			<input type="hidden" name="id" value={$form.id} />

			<div class="grid grid-cols-2 gap-4">
				<Form.Field form={sf} name="amount">
					<Form.Control>
						{#snippet children({ props })}
							<Form.Label>{m.common_amount_mxn()}</Form.Label>
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
							{@const { name: fieldName, ...triggerProps } = props}
							<Form.Label>{m.common_direction()}</Form.Label>
							<NativeSelect
								name={fieldName}
								value={$form.direction}
								onValueChange={(v) => { $form.direction = v as 'INGRESS' | 'EGRESS'; }}
								placeholder={m.common_select_direction()}
								items={[
									{ value: 'INGRESS', label: m.direction_ingress_income() },
									{ value: 'EGRESS', label: m.direction_egress_expense() },
								]}
								{...triggerProps}
							/>
						{/snippet}
					</Form.Control>
					<Form.FieldErrors />
				</Form.Field>
			</div>

			<Form.Field form={sf} name="description">
				<Form.Control>
					{#snippet children({ props })}
						<Form.Label>{m.common_description()}</Form.Label>
						<Input {...props} bind:value={$form.description} placeholder={m.transactions_placeholder_description()} />
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Form.Field form={sf} name="transactionDate">
				<Form.Control>
					{#snippet children({ props })}
						{@const { name: fieldName, ...triggerProps } = props}
						<Form.Label>{m.common_date()}</Form.Label>
						<NativeDatePicker
							name={fieldName}
							value={$form.transactionDate}
							onValueChange={(v) => { $form.transactionDate = v; }}
							{...triggerProps}
						/>
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Form.Field form={sf} name="categoryId">
				<Form.Control>
					{#snippet children({ props })}
						{@const { name: fieldName, ...triggerProps } = props}
						<Form.Label>{m.common_category()}</Form.Label>
						<NativeSelect
							name={fieldName}
							value={$form.categoryId ? String($form.categoryId) : ''}
							onValueChange={(v) => { $form.categoryId = v ? Number(v) : 0; }}
							placeholder={m.common_select_category()}
							items={filteredCategories.map(c => ({ value: String(c.id), label: c.name }))}
							{...triggerProps}
						/>
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Form.Field form={sf} name="contactId">
				<Form.Control>
					{#snippet children({ props })}
						{@const { name: fieldName, ...triggerProps } = props}
						<Form.Label>{m.common_contact_optional()}</Form.Label>
						<NativeSelect
							name={fieldName}
							value={$form.contactId !== '' ? String($form.contactId) : ''}
							onValueChange={(v) => { $form.contactId = v ? Number(v) : ''; }}
							placeholder={m.common_none()}
							items={sortedContacts.map(c => ({ value: String(c.id), label: c.name }))}
							{...triggerProps}
						/>
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Dialog.Footer>
				<Button type="button" variant="outline" onclick={() => (editDialogOpen = false)}>{m.common_cancel()}</Button>
				<Button type="submit" disabled={$submitting}>
					{$submitting ? m.common_saving() : m.common_update()}
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
		return async ({ result }) => {
			if (result.type === 'redirect') {
				toast.success(m.transactions_trashed());
				goto(result.location);
			} else if (result.type === 'failure') {
				const msg =
					(result as { data?: { message?: string } }).data?.message ??
					m.transactions_delete_failed();
				toast.error(msg);
			}
		};
	}}
></form>
