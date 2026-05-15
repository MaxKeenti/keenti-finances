<script lang="ts">
	import { superForm } from 'sveltekit-superforms';
	import { zod4Client } from 'sveltekit-superforms/adapters';
	import { z } from 'zod';
	import { toast } from 'svelte-sonner';
	import { invalidateAll } from '$app/navigation';
	import * as Form from '$lib/components/ui/form';
	import * as Card from '$lib/components/ui/card';
	import * as Table from '$lib/components/ui/table';
	import { Input } from '$lib/components/ui/input';
	import { Textarea } from '$lib/components/ui/textarea';
	import { Button } from '$lib/components/ui/button';
	import { Badge } from '$lib/components/ui/badge';
	import { Progress } from '$lib/components/ui/progress';
	import { NativeSelect } from '$lib/components/native-select';
	import { NativeDatePicker } from '$lib/components/native-date-picker';
	import type { PageData } from './$types';

	const paymentSchema = z.object({
		amount: z.coerce.number().positive('Amount must be greater than 0'),
		paymentDate: z.string().min(1, 'Payment date is required'),
		categoryId: z.coerce.number().positive('Category is required'),
		notes: z.string().optional(),
	});

	type DebtPayment = {
		id: number;
		debtId: number;
		amount: number;
		paymentDate: string;
		transactionId: number | null;
		notes: string | null;
		createdAt: string;
	};

	type Category = { id: number; name: string; type: string };

	let { data }: { data: PageData } = $props();

	const fmt = new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' });

	const statusBadgeVariant: Record<string, 'warning' | 'success'> = {
		ACTIVE: 'warning',
		PAID: 'success',
	};

	const paidPercent = $derived(
		data.debt.totalAmount > 0
			? Math.min(100, Math.round((data.debt.totalPaid / data.debt.totalAmount) * 100))
			: 0,
	);

	const isPaid = $derived(data.debt.status === 'PAID');

	const sf = superForm(data.form, {
		validators: zod4Client(paymentSchema),
		onResult({ result }) {
			if (result.type === 'success') {
				toast.success('Payment recorded.');
				invalidateAll();
			} else if (result.type === 'failure') {
				const msg = (result.data as Record<string, unknown> | undefined)?.form as
					| { message?: string }
					| undefined;
				if (msg?.message) toast.error(msg.message);
				else toast.error('Failed to record payment.');
			}
		},
	});

	const { form, errors, enhance, submitting } = sf;

	const ingressCategories = $derived(
		(data.categories as Category[])
			.filter((c) => c.type === 'INGRESS' || c.type === 'BOTH')
			.sort((a, b) => a.name.localeCompare(b.name)),
	);

</script>

<div class="space-y-6 max-w-3xl">
	<!-- Back link -->
	<Button variant="link" href="/debts" class="h-auto p-0 text-muted-foreground hover:text-foreground">
		← Back to Debts
	</Button>

	<!-- Header card -->
	<Card.Root>
		<Card.Content class="space-y-4">
			<div class="flex flex-wrap items-start justify-between gap-3">
				<div class="min-w-0">
					<h1 class="text-2xl font-semibold tracking-tight">
						{data.debt.contactName ?? `Contact #${data.debt.contactId}`}
					</h1>
					<p class="text-sm text-muted-foreground mt-0.5">{data.debt.description}</p>
				</div>
				<Badge variant={statusBadgeVariant[data.debt.status]}>{data.debt.status}</Badge>
			</div>

			<!-- Balance breakdown -->
			<div class="grid grid-cols-3 gap-4 text-sm">
				<div>
					<p class="text-muted-foreground">Total</p>
					<p class="text-lg font-semibold">{fmt.format(data.debt.totalAmount)}</p>
				</div>
				<div>
					<p class="text-muted-foreground">Paid</p>
					<p class="text-lg font-semibold text-green-600 dark:text-green-400">
						{fmt.format(data.debt.totalPaid)}
					</p>
				</div>
				<div>
					<p class="text-muted-foreground">Remaining</p>
					<p class="text-lg font-semibold text-amber-600 dark:text-amber-400">
						{fmt.format(data.debt.remaining)}
					</p>
				</div>
			</div>

			<!-- Progress bar -->
			<div class="space-y-1">
				<div class="flex justify-between text-xs text-muted-foreground">
					<span>Progress</span>
					<span>{paidPercent}%</span>
				</div>
				<Progress value={paidPercent} class="h-2 bg-green-500" />
			</div>
		</Card.Content>
	</Card.Root>

	<!-- Payment history card -->
	<Card.Root>
		<Card.Content class="space-y-4">
			<h2 class="font-semibold text-base">Payment History</h2>

			{#if data.payments.length === 0}
				<p class="text-sm text-muted-foreground">No payments recorded yet.</p>
			{:else}
				<div class="rounded-md border overflow-hidden">
					<Table.Root>
						<Table.Header class="bg-muted/50">
							<Table.Row>
								<Table.Head>Date</Table.Head>
								<Table.Head class="text-right">Amount</Table.Head>
								<Table.Head>Notes</Table.Head>
								<Table.Head class="text-right">Transaction</Table.Head>
							</Table.Row>
						</Table.Header>
						<Table.Body>
							{#each data.payments as payment (payment.id)}
								<Table.Row>
									<Table.Cell class="tabular-nums">{(payment as DebtPayment).paymentDate}</Table.Cell>
									<Table.Cell class="text-right font-medium tabular-nums">
										{fmt.format((payment as DebtPayment).amount)}
									</Table.Cell>
									<Table.Cell class="text-muted-foreground">
										{(payment as DebtPayment).notes ?? '—'}
									</Table.Cell>
									<Table.Cell class="text-right">
										{#if (payment as DebtPayment).transactionId}
											<span class="text-xs text-muted-foreground font-mono">
												#{(payment as DebtPayment).transactionId}
											</span>
										{:else}
											<span class="text-xs text-muted-foreground">—</span>
										{/if}
									</Table.Cell>
								</Table.Row>
							{/each}
						</Table.Body>
					</Table.Root>
				</div>
			{/if}
		</Card.Content>
	</Card.Root>

	<!-- Record payment card -->
	<Card.Root>
		<Card.Content class="space-y-4">
			<div class="flex items-center justify-between">
				<h2 class="font-semibold text-base">Record Payment</h2>
				{#if isPaid}
					<span class="text-xs text-muted-foreground">Debt is fully paid</span>
				{/if}
			</div>

			<form method="POST" action="?/recordPayment" use:enhance class="space-y-4">
				<fieldset disabled={isPaid || $submitting} class="contents">
					<div class="grid gap-4 sm:grid-cols-2">
						<Form.Field form={sf} name="amount">
							<Form.Control>
								{#snippet children({ props })}
									<Form.Label>Amount (MXN)</Form.Label>
									<Input
										{...props}
										type="number"
										step="0.01"
										min="0.01"
										max={data.debt.remaining}
										bind:value={$form.amount}
									/>
								{/snippet}
							</Form.Control>
							<Form.FieldErrors />
						</Form.Field>

						<Form.Field form={sf} name="paymentDate">
							<Form.Control>
								{#snippet children({ props })}
									{@const { name: fieldName, ...triggerProps } = props}
									<Form.Label>Payment Date</Form.Label>
									<NativeDatePicker
										name={fieldName}
										value={$form.paymentDate}
										onValueChange={(v) => { $form.paymentDate = v; }}
										{...triggerProps}
									/>
								{/snippet}
							</Form.Control>
							<Form.FieldErrors />
						</Form.Field>
					</div>

					<Form.Field form={sf} name="categoryId">
						<Form.Control>
							{#snippet children({ props })}
								{@const { name: fieldName, ...triggerProps } = props}
								<Form.Label>Ingress Category</Form.Label>
								<NativeSelect
									name={fieldName}
									value={$form.categoryId > 0 ? String($form.categoryId) : ''}
									onValueChange={(v) => { $form.categoryId = v ? Number(v) : 0; }}
									placeholder="Select a category…"
									items={ingressCategories.map(c => ({ value: String(c.id), label: c.name }))}
									{...triggerProps}
								/>
							{/snippet}
						</Form.Control>
						{#if $errors.categoryId}
							<p class="text-destructive text-sm">{$errors.categoryId}</p>
						{/if}
					</Form.Field>

					<Form.Field form={sf} name="notes">
						<Form.Control>
							{#snippet children({ props })}
								<Form.Label>Notes <span class="text-muted-foreground">(optional)</span></Form.Label>
								<Textarea
									{...props}
									bind:value={$form.notes}
									rows={2}
									placeholder="Optional payment notes"
								/>
							{/snippet}
						</Form.Control>
						<Form.FieldErrors />
					</Form.Field>

					<Button type="submit" disabled={isPaid || $submitting} class="w-full sm:w-auto">
						{$submitting ? 'Recording…' : 'Record Payment'}
					</Button>
				</fieldset>
			</form>
		</Card.Content>
	</Card.Root>
</div>
