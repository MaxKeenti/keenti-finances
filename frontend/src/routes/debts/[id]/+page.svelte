<script lang="ts">
	import { superForm } from 'sveltekit-superforms';
	import { zod4Client } from 'sveltekit-superforms/adapters';
	import { z } from 'zod';
	import { toast } from 'svelte-sonner';
	import { invalidateAll } from '$app/navigation';
	import * as Form from '$lib/components/ui/form';
	import { Input } from '$lib/components/ui/input';
	import { Button } from '$lib/components/ui/button';

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

	const statusBadgeClass: Record<string, string> = {
		ACTIVE: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400',
		PAID: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
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
		(data.categories as Category[]).filter((c) => c.type === 'INGRESS'),
	);
</script>

<div class="space-y-6 max-w-3xl">
	<!-- Back link -->
	<a
		href="/debts"
		class="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
	>
		← Back to Debts
	</a>

	<!-- Header -->
	<div class="rounded-lg border bg-card p-6 space-y-4">
		<div class="flex flex-wrap items-start justify-between gap-3">
			<div class="min-w-0">
				<h1 class="text-2xl font-semibold tracking-tight">
					{data.debt.contactName ?? `Contact #${data.debt.contactId}`}
				</h1>
				<p class="text-sm text-muted-foreground mt-0.5">{data.debt.description}</p>
			</div>
			<span
				class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium {statusBadgeClass[data.debt.status] ?? ''}"
			>
				{data.debt.status}
			</span>
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
			<div class="h-2 rounded-full bg-muted overflow-hidden">
				<div
					class="h-full rounded-full bg-green-500 transition-all"
					style="width: {paidPercent}%"
				></div>
			</div>
		</div>
	</div>

	<!-- Payment history -->
	<div class="rounded-lg border bg-card p-5 space-y-4">
		<h2 class="font-semibold text-base">Payment History</h2>

		{#if data.payments.length === 0}
			<p class="text-sm text-muted-foreground">No payments recorded yet.</p>
		{:else}
			<div class="rounded-md border overflow-hidden">
				<table class="w-full text-sm">
					<thead class="bg-muted/50">
						<tr>
							<th class="px-4 py-2 text-left font-medium text-muted-foreground">Date</th>
							<th class="px-4 py-2 text-right font-medium text-muted-foreground">Amount</th>
							<th class="px-4 py-2 text-left font-medium text-muted-foreground">Notes</th>
							<th class="px-4 py-2 text-right font-medium text-muted-foreground">Transaction</th>
						</tr>
					</thead>
					<tbody class="divide-y">
						{#each data.payments as payment (payment.id)}
							<tr class="hover:bg-muted/30 transition-colors">
								<td class="px-4 py-2.5 tabular-nums">{(payment as DebtPayment).paymentDate}</td>
								<td class="px-4 py-2.5 text-right font-medium tabular-nums">
									{fmt.format((payment as DebtPayment).amount)}
								</td>
								<td class="px-4 py-2.5 text-muted-foreground">
									{(payment as DebtPayment).notes ?? '—'}
								</td>
								<td class="px-4 py-2.5 text-right">
									{#if (payment as DebtPayment).transactionId}
										<span class="text-xs text-muted-foreground font-mono">
											#{(payment as DebtPayment).transactionId}
										</span>
									{:else}
										<span class="text-xs text-muted-foreground">—</span>
									{/if}
								</td>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
		{/if}
	</div>

	<!-- Record payment form -->
	<div class="rounded-lg border bg-card p-5 space-y-4">
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
								<Form.Label>Payment Date</Form.Label>
								<Input {...props} type="date" bind:value={$form.paymentDate} />
							{/snippet}
						</Form.Control>
						<Form.FieldErrors />
					</Form.Field>
				</div>

				<Form.Field form={sf} name="categoryId">
					<Form.Control>
						{#snippet children({ props })}
							<Form.Label>Ingress Category</Form.Label>
							<select
								{...props}
								bind:value={$form.categoryId}
								class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:opacity-50"
							>
								<option value={0} disabled>Select a category…</option>
								{#each ingressCategories as cat (cat.id)}
									<option value={cat.id}>{cat.name}</option>
								{/each}
							</select>
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
							<textarea
								{...props}
								bind:value={$form.notes}
								rows={2}
								class="flex min-h-[60px] w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:opacity-50 resize-none"
							></textarea>
						{/snippet}
					</Form.Control>
					<Form.FieldErrors />
				</Form.Field>

				<Button type="submit" disabled={isPaid || $submitting} class="w-full sm:w-auto">
					{$submitting ? 'Recording…' : 'Record Payment'}
				</Button>
			</fieldset>
		</form>
	</div>
</div>
