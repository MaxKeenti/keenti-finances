<script lang="ts">
	import { enhance as kitEnhance } from '$app/forms';
	import { invalidateAll } from '$app/navigation';
	import { untrack } from 'svelte';
	import { superForm } from 'sveltekit-superforms';
	import { zod4Client } from 'sveltekit-superforms/adapters';
	import { z } from 'zod';
	import { toast } from 'svelte-sonner';
	import {
		AlertTriangle,
		Archive,
		ArrowDownLeft,
		ArrowLeft,
		ArrowRight,
		ArrowUpRight,
		Clock3,
		History,
		Pencil,
		ReceiptText,
		RotateCcw,
		Target,
	} from '@lucide/svelte';
	import { submitWithAdaptiveConfirm } from '$lib/components/adaptive-confirm';
	import { BoxPlanPanel, PlanCreationDialog } from '$lib/components/box-plans';
	import { FundingTriggerSettings } from '$lib/components/funding-triggers';
	import * as Alert from '$lib/components/ui/alert';
	import * as Card from '$lib/components/ui/card';
	import * as Dialog from '$lib/components/ui/dialog';
	import * as Empty from '$lib/components/ui/empty';
	import * as Form from '$lib/components/ui/form';
	import { Badge } from '$lib/components/ui/badge';
	import { Button } from '$lib/components/ui/button';
	import { Input } from '$lib/components/ui/input';
	import { NativeDatePicker } from '$lib/components/native-date-picker';
	import { NativeSelect } from '$lib/components/native-select';
	import { formatLocale, mxnFormatter, shortDateFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
	import {
		boxMovementTransactionSourceState,
		hasClickableBoxMovementTransaction,
		isCorrectableBoxMovement,
		type BoxMovementDto,
		type BoxMovementType,
	} from '$lib/types/boxes';
	import { isActivePlan, type BoxPlan } from '$lib/types/box-plans';
	import type { PageData } from './$types';

	type MovementKind = 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER';
	const MAX_AMOUNT = 9_999_999_999.99;

	const movementSchema = z
		.object({
			kind: z.enum(['DEPOSIT', 'WITHDRAWAL', 'TRANSFER']),
			amount: z.coerce
				.number()
				.positive(m.validation_amount_positive())
				.max(MAX_AMOUNT, m.validation_amount_too_large())
				.refine(
					(value) => Math.abs(value * 100 - Math.round(value * 100)) < 1e-7,
					m.validation_amount_two_decimals(),
				),
			effectiveDate: z
				.string()
				.regex(/^\d{4}-\d{2}-\d{2}$/, m.validation_date_required())
				.refine((date) => date <= data.today, m.validation_box_date_future()),
			targetBoxId: z.coerce.number().int().min(0).default(0),
		})
		.superRefine((value, context) => {
			if (value.kind === 'TRANSFER' && value.targetBoxId <= 0) {
				context.addIssue({
					code: 'custom',
					path: ['targetBoxId'],
					message: m.validation_box_target_required(),
				});
			}
		});

	let { data }: { data: PageData } = $props();

	let movementDialogOpen = $state(false);
	let correctionDialogOpen = $state(false);
	let correctingMovement = $state<BoxMovementDto | null>(null);
	let correctionAmount = $state(0);
	let correctionEffectiveDate = $state('');
	let correctionError = $state<string | null>(null);
	let correctionSubmitting = $state(false);
	let planCreationOpen = $state(false);
	let archiveForm = $state<HTMLFormElement | null>(null);

	const fmt = $derived(mxnFormatter(data.preferences.locale));
	const dateFmt = $derived(shortDateFormatter(data.preferences.locale));
	const dateTimeFmt = $derived(
		new Intl.DateTimeFormat(formatLocale(data.preferences.locale), {
			dateStyle: 'medium',
			timeStyle: 'short',
			timeZone: data.preferences.timeZone,
		}),
	);
	const isUnreconciled = $derived(data.balanceSummary.availableToSpend < 0);
	const hasBalance = $derived(data.box.balance >= 0.005);
	const orderedHistory = $derived(
		[...data.history].sort((a, b) =>
			b.effectiveDate.localeCompare(a.effectiveDate) || b.createdAt.localeCompare(a.createdAt),
		),
	);
	const activePlanSummary = $derived(data.planSummaries.find(isActivePlan));
	const closedPlanSummaries = $derived(data.planSummaries.filter((plan) => !isActivePlan(plan)));

	const sf = superForm(untrack(() => data.form), {
		validators: zod4Client(movementSchema),
		onResult({ result }) {
			if (result.type === 'success') {
				const kind = (result.data as { movementKind?: MovementKind } | undefined)?.movementKind;
				movementDialogOpen = false;
				toast.success(
					kind === 'WITHDRAWAL'
						? m.boxes_withdrawal_recorded()
						: kind === 'TRANSFER'
							? m.boxes_transfer_recorded()
							: m.boxes_deposit_recorded(),
				);
				void invalidateAll();
			} else if (result.type === 'failure') {
				const resultForm = (result.data as { form?: { message?: string } } | undefined)?.form;
				toast.error(resultForm?.message ?? m.error_box_movement());
			}
		},
	});
	const { form, errors, enhance, submitting, message } = sf;

	function openMovement(kind: MovementKind, amount = 0) {
		sf.reset({
			data: {
				kind,
				amount,
				effectiveDate: data.today,
				targetBoxId: 0,
			},
		});
		movementDialogOpen = true;
	}

	function openCorrection(movement: BoxMovementDto) {
		if (data.box.archived || !isCorrectableBoxMovement(movement.type)) return;
		correctingMovement = movement;
		correctionAmount = movement.amount;
		correctionEffectiveDate = movement.effectiveDate;
		correctionError = null;
		correctionDialogOpen = true;
	}

	function isTransferMovement(type: BoxMovementType): boolean {
		return type === 'TRANSFER_IN' || type === 'TRANSFER_OUT';
	}

	async function refreshPlan(_plan?: BoxPlan) {
		await invalidateAll();
	}

	function movementTitle(kind: MovementKind): string {
		if (kind === 'WITHDRAWAL') return m.boxes_withdraw_title();
		if (kind === 'TRANSFER') return m.boxes_transfer_title();
		return m.boxes_deposit_title();
	}

	function movementDescription(kind: MovementKind): string {
		if (kind === 'WITHDRAWAL') return m.boxes_withdraw_description();
		if (kind === 'TRANSFER') return m.boxes_transfer_description();
		return m.boxes_deposit_description();
	}

	function movementLabel(type: BoxMovementType | string): string {
		if (type === 'DEPOSIT') return m.boxes_history_deposit();
		if (type === 'WITHDRAWAL') return m.boxes_history_withdrawal();
		if (type === 'TRANSFER_IN') return m.boxes_history_transfer_in();
		if (type === 'TRANSFER_OUT') return m.boxes_history_transfer_out();
		if (type === 'SPENDING') return m.boxes_history_spending();
		return type.replaceAll('_', ' ').toLocaleLowerCase(data.preferences.locale);
	}

	function movementIsIngress(type: BoxMovementType | string): boolean {
		return type === 'DEPOSIT' || type === 'TRANSFER_IN';
	}

	function effectiveDate(date: string): string {
		return dateFmt.format(new Date(`${date}T00:00:00`));
	}

	function auditDate(date: string): string {
		return dateTimeFmt.format(new Date(date));
	}

	async function confirmArchive() {
		if (hasBalance || activePlanSummary) return;
		await submitWithAdaptiveConfirm(archiveForm, {
			title: m.boxes_archive_title(),
			description: m.boxes_archive_confirmation({ name: data.box.name }),
			confirmLabel: m.boxes_archive(),
			cancelLabel: m.common_cancel(),
		});
	}

	function actionError(result: unknown, fallback: string): string {
		if (result && typeof result === 'object' && 'data' in result) {
			return ((result as { data?: { message?: string } }).data?.message ?? fallback);
		}
		return fallback;
	}
</script>

<div class="mx-auto max-w-4xl space-y-6">
	<Button variant="link" href="/boxes" class="h-auto p-0 text-muted-foreground hover:text-foreground">
		<ArrowLeft data-icon="inline-start" />
		{m.common_back_to_boxes()}
	</Button>

	{#if data.box.archived}
		<Alert.Root>
			<Archive aria-hidden="true" />
			<Alert.Title>{m.boxes_archived_read_only_title()}</Alert.Title>
			<Alert.Description>{m.boxes_archived_read_only_description()}</Alert.Description>
			<Alert.Action>
				<form
					method="POST"
					action="?/restore"
					use:kitEnhance={() => async ({ result, update }) => {
						if (result.type === 'success') {
							toast.success(m.boxes_restored());
							await update();
						} else toast.error(actionError(result, m.error_box_restore()));
					}}
				>
					<Button type="submit" size="sm" variant="outline">
						<RotateCcw data-icon="inline-start" />{m.common_restore()}
					</Button>
				</form>
			</Alert.Action>
		</Alert.Root>
	{:else if isUnreconciled}
		<Alert.Root variant="destructive">
			<AlertTriangle aria-hidden="true" />
			<Alert.Title>{m.balance_reconciliation_required()}</Alert.Title>
			<Alert.Description>
				{m.balance_reconciliation_box_detail({
					amount: fmt.format(Math.abs(data.balanceSummary.availableToSpend)),
				})}
			</Alert.Description>
			{#if hasBalance}
				<Alert.Action>
					<Button type="button" size="sm" variant="outline" onclick={() => openMovement('WITHDRAWAL')}>
						{m.boxes_withdraw()}
					</Button>
				</Alert.Action>
			{/if}
		</Alert.Root>
	{/if}

	<Card.Root
		class="bg-gradient-to-br from-[oklch(0.97_0.025_var(--box-hue))] to-card dark:from-[oklch(0.27_0.035_var(--box-hue))]"
		style={`--box-hue: ${data.box.hue}`}
	>
		<Card.Header>
			<div class="flex flex-wrap items-start justify-between gap-4">
				<div class="flex min-w-0 items-start gap-3">
					<div
						class="flex size-12 shrink-0 items-center justify-center rounded-xl text-2xl shadow-sm ring-1 ring-black/5"
						style={`background: oklch(0.88 0.12 ${data.box.hue}); color: oklch(0.32 0.08 ${data.box.hue})`}
						aria-hidden="true"
					>
						{data.box.icon || '□'}
					</div>
					<div class="min-w-0">
						<div class="flex flex-wrap items-center gap-2">
							<h1 class="truncate text-2xl font-semibold tracking-tight">{data.box.name}</h1>
							{#if data.box.archived}<Badge variant="secondary">{m.boxes_archived()}</Badge>{/if}
						</div>
						{#if data.box.description}<p class="mt-1 text-sm text-muted-foreground">{data.box.description}</p>{/if}
					</div>
				</div>
				{#if !data.box.archived}
					<Button
						variant="ghost"
						size="sm"
						onclick={confirmArchive}
						disabled={hasBalance || !!activePlanSummary}
						title={hasBalance
							? m.boxes_withdraw_before_archive()
							: activePlanSummary
								? m.box_plan_end_before_archive()
								: m.boxes_archive()}
					>
						<Archive data-icon="inline-start" />{m.boxes_archive()}
					</Button>
				{/if}
			</div>
		</Card.Header>
		<Card.Content class="space-y-5">
			<div class="grid gap-4 sm:grid-cols-3">
				<div>
					<p class="text-xs text-muted-foreground">{m.boxes_balance()}</p>
					<p class="text-3xl font-semibold tabular-nums">{fmt.format(data.box.balance)}</p>
				</div>
				<div>
					<p class="text-xs text-muted-foreground">{m.balance_available_to_spend()}</p>
					<p class="text-lg font-medium tabular-nums {isUnreconciled ? 'text-destructive' : ''}">
						{fmt.format(data.balanceSummary.availableToSpend)}
					</p>
				</div>
				<div>
					<p class="text-xs text-muted-foreground">{m.balance_in_boxes()}</p>
					<p class="text-lg font-medium tabular-nums">{fmt.format(data.balanceSummary.inBoxes)}</p>
				</div>
			</div>

			{#if !data.box.archived}
				<div class="flex flex-wrap gap-2 border-t pt-4">
					<Button onclick={() => openMovement('DEPOSIT')} disabled={isUnreconciled} title={isUnreconciled ? m.boxes_deposits_blocked() : undefined}>
						<ArrowDownLeft data-icon="inline-start" />{m.boxes_deposit()}
					</Button>
					<Button variant="outline" onclick={() => openMovement('WITHDRAWAL')} disabled={!hasBalance}>
						<ArrowUpRight data-icon="inline-start" />{m.boxes_withdraw()}
					</Button>
					<Button variant="outline" onclick={() => openMovement('TRANSFER')} disabled={!hasBalance || data.transferTargets.length === 0}>
						<ArrowRight data-icon="inline-start" />{m.boxes_transfer()}
					</Button>
				</div>
				{#if activePlanSummary && !hasBalance}
					<p class="text-xs text-muted-foreground">{m.box_plan_end_before_archive()}</p>
				{/if}
			{/if}
		</Card.Content>
	</Card.Root>

	<section class="space-y-3" aria-labelledby="box-plan-title">
		<div class="flex flex-wrap items-start justify-between gap-3">
			<div>
				<h2 id="box-plan-title" class="text-lg font-semibold">{m.box_plan_section_title()}</h2>
				<p class="max-w-2xl text-sm text-muted-foreground">{m.box_plan_section_description()}</p>
			</div>
			{#if !data.box.archived && !activePlanSummary}
				<Button onclick={() => (planCreationOpen = true)}>
					<Target data-icon="inline-start" />{m.box_plan_create()}
				</Button>
			{/if}
		</div>

		{#if data.planLoadFailed}
			<Alert.Root variant="destructive">
				<AlertTriangle aria-hidden="true" />
				<Alert.Description>{m.box_plan_load_error()}</Alert.Description>
			</Alert.Root>
		{/if}

		{#if data.viewingHistorical && activePlanSummary}
			<div class="flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-muted/30 p-3">
				<p class="text-sm text-muted-foreground">{m.box_plan_historical()}</p>
				<Button href={`?plan=${activePlanSummary.id}`} size="sm" variant="outline">{m.box_plan_view_current()}</Button>
			</div>
		{/if}

		{#if data.planDetail}
			<BoxPlanPanel
				plan={data.planDetail}
				movements={data.history}
				locale={data.preferences.locale}
				timeZone={data.preferences.timeZone}
				viewingHistorical={data.viewingHistorical}
				onChanged={refreshPlan}
				onTopUp={(amount) => openMovement('DEPOSIT', amount)}
				topUpDisabled={isUnreconciled}
			/>
		{:else if !data.planLoadFailed}
			<Empty.Root class="border">
				<Empty.Media variant="icon"><Target /></Empty.Media>
				<Empty.Header>
					<Empty.Title>{m.box_plan_create_title()}</Empty.Title>
					<Empty.Description>{m.box_plan_one_active()}</Empty.Description>
				</Empty.Header>
				{#if !data.box.archived}
					<Empty.Content><Button onclick={() => (planCreationOpen = true)}>{m.box_plan_create()}</Button></Empty.Content>
				{/if}
			</Empty.Root>
		{/if}

		{#if closedPlanSummaries.length > 0}
			<div class="space-y-2">
				<div>
					<h3 class="font-semibold">{m.box_plan_history_title()}</h3>
					<p class="text-sm text-muted-foreground">{m.box_plan_history_description()}</p>
				</div>
				<div class="grid gap-2 sm:grid-cols-2">
					{#each closedPlanSummaries as summary (summary.id)}
						<a
							href={`?plan=${summary.id}`}
							class="flex items-center justify-between gap-3 rounded-lg border bg-card p-3 transition-colors hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
							aria-current={data.planDetail?.id === summary.id ? 'page' : undefined}
						>
							<div class="min-w-0">
								<p class="truncate text-sm font-medium">{summary.type === 'SAVING_GOAL' ? m.box_plan_saving_goal() : m.box_plan_spending_budget()}</p>
								<p class="text-xs text-muted-foreground">{summary.closedAt ? m.box_plan_closed_on({ date: auditDate(summary.closedAt) }) : m.box_plan_created_on({ date: auditDate(summary.createdAt) })}</p>
							</div>
							{#if summary.completionAmount !== null}<span class="shrink-0 text-sm font-medium tabular-nums">{fmt.format(summary.completionAmount)}</span>{/if}
						</a>
					{/each}
				</div>
			</div>
		{/if}
	</section>

	<FundingTriggerSettings
		boxId={data.box.id}
		archived={data.box.archived}
		hasActivePlan={Boolean(activePlanSummary)}
		locale={data.preferences.locale}
	/>

	<section class="space-y-3" aria-labelledby="box-history-title">
		<div>
			<h2 id="box-history-title" class="text-lg font-semibold">{m.boxes_history_title()}</h2>
			<p class="text-sm text-muted-foreground">{m.boxes_history_description()}</p>
		</div>

		{#if orderedHistory.length === 0}
			<Empty.Root class="border">
				<Empty.Media variant="icon"><History /></Empty.Media>
				<Empty.Header>
					<Empty.Title>{m.boxes_history_empty_title()}</Empty.Title>
					<Empty.Description>{m.boxes_history_empty_description()}</Empty.Description>
				</Empty.Header>
			</Empty.Root>
		{:else}
			<div class="divide-y rounded-xl border bg-card">
				{#each orderedHistory as movement (`${movement.type}:${movement.id}`)}
					{@const ingress = movementIsIngress(movement.type)}
					{@const sourceState = boxMovementTransactionSourceState(movement)}
					{@const sourceIsClickable = hasClickableBoxMovementTransaction(movement)}
					{@const transactionDescription = movement.relatedTransactionDescription || (movement.relatedTransactionId ? m.boxes_transaction_number({ id: movement.relatedTransactionId }) : '')}
					<div class="grid gap-3 p-4 sm:grid-cols-[auto_1fr_auto] sm:items-center">
						<div
							class="flex size-9 items-center justify-center rounded-full {ingress ? 'bg-green-500/10 text-green-700 dark:text-green-400' : 'bg-amber-500/10 text-amber-700 dark:text-amber-400'}"
						>
							{#if movement.type === 'SPENDING'}
								<ReceiptText class="size-4" aria-hidden="true" />
							{:else if ingress}
								<ArrowDownLeft class="size-4" aria-hidden="true" />
							{:else}
								<ArrowUpRight class="size-4" aria-hidden="true" />
							{/if}
						</div>
						<div class="min-w-0 space-y-1">
							<div class="flex flex-wrap items-center gap-x-2 gap-y-1">
								<p class="font-medium">{movementLabel(movement.type)}</p>
								<span class="text-xs text-muted-foreground">{effectiveDate(movement.effectiveDate)}</span>
							</div>
							{#if movement.relatedBoxId && movement.relatedBoxName}
								<a href={`/boxes/${movement.relatedBoxId}`} class="text-sm text-muted-foreground underline-offset-4 hover:underline">
									{movement.relatedBoxName}
								</a>
							{:else if movement.relatedTransactionId && movement.type === 'DEPOSIT'}
								<div class="flex flex-wrap items-center gap-1.5 text-sm text-muted-foreground">
									<span>{m.boxes_ingress_source()}:</span>
									{#if sourceIsClickable}
										<a href={`/transactions/${movement.relatedTransactionId}`} class="font-medium underline-offset-4 hover:underline">
											{transactionDescription}
										</a>
									{:else}
										<span class="font-medium">{transactionDescription}</span>
									{/if}
									{#if sourceState === 'CHANGED'}
										<Badge variant="warning">{m.boxes_ingress_source_changed()}</Badge>
									{:else if sourceState === 'REMOVED'}
										<Badge variant="destructive">{m.boxes_ingress_source_removed()}</Badge>
									{/if}
								</div>
								{#if sourceState === 'CHANGED'}
									<p class="text-xs text-amber-700 dark:text-amber-400">{m.boxes_ingress_source_changed_hint()}</p>
								{:else if sourceState === 'REMOVED'}
									<p class="text-xs text-destructive">{m.boxes_ingress_source_removed_hint()}</p>
								{/if}
							{:else if movement.relatedTransactionId && sourceIsClickable}
								<a href={`/transactions/${movement.relatedTransactionId}`} class="text-sm text-muted-foreground underline-offset-4 hover:underline">
									{transactionDescription}
								</a>
							{/if}
							<p class="flex items-center gap-1 text-xs text-muted-foreground" title={auditDate(movement.createdAt)}>
								<Clock3 class="size-3" aria-hidden="true" />
								{m.boxes_recorded_at({ date: auditDate(movement.createdAt) })}
							</p>
						</div>
						<div class="text-left sm:text-right">
							<p class="font-semibold tabular-nums {ingress ? 'text-green-700 dark:text-green-400' : ''}">
								{ingress ? '+' : '−'}{fmt.format(movement.amount)}
							</p>
							<p class="text-xs text-muted-foreground tabular-nums">
								{m.boxes_running_balance({ amount: fmt.format(movement.runningBalance) })}
							</p>
							{#if !data.box.archived && isCorrectableBoxMovement(movement.type)}
								<Button
									type="button"
									variant="ghost"
									size="xs"
									class="mt-1"
									onclick={() => openCorrection(movement)}
									aria-label={m.boxes_correct_movement_aria({
										type: movementLabel(movement.type),
										date: effectiveDate(movement.effectiveDate),
									})}
								>
									<Pencil data-icon="inline-start" />{m.boxes_correct_movement()}
								</Button>
							{/if}
						</div>
					</div>
				{/each}
			</div>
		{/if}
	</section>
</div>

<Dialog.Root bind:open={movementDialogOpen}>
	<Dialog.Content class="sm:max-w-md">
		<Dialog.Header>
			<Dialog.Title>{movementTitle($form.kind as MovementKind)}</Dialog.Title>
			<Dialog.Description>{movementDescription($form.kind as MovementKind)}</Dialog.Description>
		</Dialog.Header>

		{#if $message}
			<Alert.Root variant="destructive"><Alert.Description>{$message}</Alert.Description></Alert.Root>
		{/if}

		<form method="POST" action="?/move" use:enhance class="grid gap-4">
			<input type="hidden" name="kind" value={$form.kind} />

			{#if $form.kind === 'TRANSFER'}
				<Form.Field form={sf} name="targetBoxId">
					<Form.Control>
						{#snippet children({ props })}
							{@const { name: fieldName, ...triggerProps } = props}
							<Form.Label>{m.boxes_transfer_destination()}</Form.Label>
							<NativeSelect
								name={fieldName}
								value={$form.targetBoxId > 0 ? String($form.targetBoxId) : ''}
								onValueChange={(value) => ($form.targetBoxId = value ? Number(value) : 0)}
								placeholder={m.boxes_transfer_select_destination()}
								items={data.transferTargets.map((box) => ({ value: String(box.id), label: box.icon ? `${box.icon} ${box.name}` : box.name }))}
								{...triggerProps}
							/>
						{/snippet}
					</Form.Control>
					{#if $errors.targetBoxId}<p class="text-sm text-destructive">{$errors.targetBoxId}</p>{/if}
				</Form.Field>
			{/if}

			<Form.Field form={sf} name="amount">
				<Form.Control>
					{#snippet children({ props })}
						<Form.Label>{m.common_amount_mxn()}</Form.Label>
						<Input
							{...props}
							type="number"
							step="0.01"
							min="0.01"
							max={$form.kind === 'DEPOSIT' ? Math.max(0, data.balanceSummary.availableToSpend) : data.box.balance}
							bind:value={$form.amount}
						/>
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
				<p class="text-xs text-muted-foreground">
					{$form.kind === 'DEPOSIT'
						? m.boxes_available_hint({ amount: fmt.format(data.balanceSummary.availableToSpend) })
						: m.boxes_box_balance_hint({ amount: fmt.format(data.box.balance) })}
				</p>
			</Form.Field>

			<Form.Field form={sf} name="effectiveDate">
				<Form.Control>
					{#snippet children({ props })}
						{@const { name: fieldName, ...triggerProps } = props}
						<Form.Label>{m.boxes_effective_date()}</Form.Label>
						<NativeDatePicker
							name={fieldName}
							value={$form.effectiveDate}
							onValueChange={(value) => ($form.effectiveDate = value)}
							max={data.today}
							{...triggerProps}
						/>
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
				<p class="text-xs text-muted-foreground">{m.boxes_effective_date_hint()}</p>
			</Form.Field>

			<Dialog.Footer>
				<Button type="button" variant="outline" onclick={() => (movementDialogOpen = false)}>{m.common_cancel()}</Button>
				<Button type="submit" disabled={$submitting}>
					{$submitting ? m.common_processing() : movementTitle($form.kind as MovementKind)}
				</Button>
			</Dialog.Footer>
		</form>
	</Dialog.Content>
</Dialog.Root>

<Dialog.Root bind:open={correctionDialogOpen}>
	<Dialog.Content class="sm:max-w-md">
		{#if correctingMovement}
			<Dialog.Header>
				<Dialog.Title>
					{m.boxes_correction_title({ type: movementLabel(correctingMovement.type) })}
				</Dialog.Title>
				<Dialog.Description>{m.boxes_correction_description()}</Dialog.Description>
			</Dialog.Header>

			<div class="grid gap-2 rounded-lg border bg-muted/30 p-3 text-sm">
				<div class="flex items-center justify-between gap-4">
					<span class="text-muted-foreground">{m.common_type()}</span>
					<span class="font-medium">{movementLabel(correctingMovement.type)}</span>
				</div>
				{#if isTransferMovement(correctingMovement.type) && correctingMovement.relatedBoxName}
					<div class="flex items-center justify-between gap-4">
						<span class="text-muted-foreground">{m.boxes_correction_counterpart()}</span>
						<span class="font-medium">{correctingMovement.relatedBoxName}</span>
					</div>
				{/if}
			</div>

			{#if isTransferMovement(correctingMovement.type)}
				<Alert.Root>
					<ArrowRight aria-hidden="true" />
					<Alert.Title>{m.boxes_correction_transfer_title()}</Alert.Title>
					<Alert.Description>{m.boxes_correction_transfer_description()}</Alert.Description>
				</Alert.Root>
			{:else if correctingMovement.type === 'DEPOSIT' && correctingMovement.relatedTransactionId}
				<p class="text-sm text-muted-foreground">{m.boxes_correction_linked_ingress()}</p>
			{/if}

			{#if correctionError}
				<Alert.Root variant="destructive">
					<AlertTriangle aria-hidden="true" />
					<Alert.Description>{correctionError}</Alert.Description>
				</Alert.Root>
			{/if}

			<form
				method="POST"
				action="?/correct"
				class="grid gap-4"
				aria-busy={correctionSubmitting}
				use:kitEnhance={() => {
					correctionSubmitting = true;
					correctionError = null;
					return async ({ result }) => {
						correctionSubmitting = false;
						if (result.type === 'success') {
							correctionDialogOpen = false;
							toast.success(m.boxes_correction_saved());
							await invalidateAll();
							correctingMovement = null;
							return;
						}

						const errorMessage = actionError(result, m.error_box_movement_correction());
						correctionError = errorMessage;
						toast.error(errorMessage);
					};
				}}
			>
				<input type="hidden" name="movementId" value={correctingMovement.id} />

				<div class="grid gap-2">
					<label for="box-correction-amount" class="text-sm font-medium">
						{m.common_amount_mxn()}
					</label>
					<Input
						id="box-correction-amount"
						name="amount"
						type="number"
						step="0.01"
						min="0.01"
						max={MAX_AMOUNT}
						required
						bind:value={correctionAmount}
						aria-describedby="box-correction-balance-hint"
					/>
					<p id="box-correction-balance-hint" class="text-xs text-muted-foreground">
						{m.boxes_correction_balance_hint()}
					</p>
				</div>

				<div class="grid gap-2">
					<label for="box-correction-date" class="text-sm font-medium">
						{m.boxes_effective_date()}
					</label>
					<NativeDatePicker
						id="box-correction-date"
						name="effectiveDate"
						value={correctionEffectiveDate}
						onValueChange={(value) => (correctionEffectiveDate = value)}
						max={data.today}
						required
						aria-describedby="box-correction-date-hint"
					/>
					<p id="box-correction-date-hint" class="text-xs text-muted-foreground">
						{m.boxes_effective_date_hint()}
					</p>
				</div>

				<Dialog.Footer>
					<Button type="button" variant="outline" onclick={() => (correctionDialogOpen = false)}>
						{m.common_cancel()}
					</Button>
					<Button type="submit" disabled={correctionSubmitting}>
						{correctionSubmitting ? m.common_saving() : m.common_save()}
					</Button>
				</Dialog.Footer>
			</form>
		{/if}
	</Dialog.Content>
</Dialog.Root>

<PlanCreationDialog
	bind:open={planCreationOpen}
	boxId={data.box.id}
	boxBalance={data.box.balance}
	today={data.today}
	locale={data.preferences.locale}
	onCreated={refreshPlan}
/>

<form
	bind:this={archiveForm}
	method="POST"
	action="?/archive"
	class="hidden"
	aria-hidden="true"
	use:kitEnhance={() => async ({ result, update }) => {
		if (result.type === 'success') {
			toast.success(m.boxes_archived_success());
			await update();
		} else toast.error(actionError(result, m.error_box_archive()));
	}}
></form>
