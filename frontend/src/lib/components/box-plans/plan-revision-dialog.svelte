<script lang="ts">
	import { Calculator, CalendarClock } from '@lucide/svelte';
	import { toast } from 'svelte-sonner';
	import * as Alert from '$lib/components/ui/alert';
	import { Button } from '$lib/components/ui/button';
	import { Checkbox } from '$lib/components/ui/checkbox';
	import * as Dialog from '$lib/components/ui/dialog';
	import { Input } from '$lib/components/ui/input';
	import { Label } from '$lib/components/ui/label';
	import { NativeDatePicker } from '$lib/components/native-date-picker';
	import { NativeSelect } from '$lib/components/native-select';
	import { formatLocale, mxnFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
	import type {
		BoxPlan,
		BoxPlanRevisionPreview,
		PlanCadence,
		SavingGoalRevisionPreview,
		SpendingBudgetRevisionPreview,
	} from '$lib/types/box-plans';

	let {
		open = $bindable(false),
		plan,
		locale,
		onApplied,
	}: {
		open?: boolean;
		plan: BoxPlan;
		locale: string;
		onApplied: (plan: BoxPlan) => void | Promise<void>;
	} = $props();

	let targetAmount = $state(0);
	let targetDate = $state('');
	let desiredBalance = $state(0);
	let cadence = $state<PlanCadence>('MONTHLY');
	let anchorWeekday = $state(1);
	let anchorDayOfMonth = $state(1);
	let autoCommitment = $state(true);
	let customCommitment = $state(0);
	let preview = $state<BoxPlanRevisionPreview | null>(null);
	let previewLoading = $state(false);
	let previewError = $state('');
	let submitting = $state(false);

	const fmt = $derived(mxnFormatter(locale));
	const dateFmt = $derived(
		new Intl.DateTimeFormat(formatLocale(locale), { dateStyle: 'medium', timeZone: 'UTC' }),
	);
	const cadenceItems = $derived([
		{ value: 'DAILY', label: m.box_plan_daily() },
		{ value: 'WEEKLY', label: m.box_plan_weekly() },
		{ value: 'BIWEEKLY', label: m.box_plan_biweekly() },
		{ value: 'MONTHLY', label: m.box_plan_monthly() },
	]);
	const weekdayItems = $derived(weekdays(locale));
	const monthDayItems = Array.from({ length: 31 }, (_, index) => ({
		value: String(index + 1),
		label: String(index + 1),
	}));

	function weekdays(currentLocale: string): { value: string; label: string }[] {
		const formatter = new Intl.DateTimeFormat(formatLocale(currentLocale), {
			weekday: 'long',
			timeZone: 'UTC',
		});
		return Array.from({ length: 7 }, (_, index) => ({
			value: String(index + 1),
			label: formatter.format(new Date(Date.UTC(2024, 0, index + 1))),
		}));
	}

	function initialize() {
		cadence = plan.cadence;
		anchorWeekday = plan.anchorWeekday ?? 1;
		anchorDayOfMonth = plan.anchorDayOfMonth ?? 1;
		if (plan.type === 'SAVING_GOAL') {
			targetAmount = plan.targetAmount;
			targetDate =
				plan.status === 'OVERDUE' && plan.suggestedExtensionDate
					? plan.suggestedExtensionDate
					: plan.targetDate;
			customCommitment = plan.regularCommitment;
			autoCommitment = true;
		} else desiredBalance = plan.desiredBalance;
		preview = null;
		previewError = '';
	}

	function validMoney(value: number, allowZero = false): boolean {
		return (
			Number.isFinite(value) &&
			(allowZero ? value >= 0 : value > 0) &&
			value <= 9_999_999_999.99 &&
			Math.abs(value * 100 - Math.round(value * 100)) < 1e-7
		);
	}

	function currentRequest(): { payload: Record<string, unknown> | null; error: string } {
		if (['WEEKLY', 'BIWEEKLY'].includes(cadence) && (anchorWeekday < 1 || anchorWeekday > 7)) {
			return { payload: null, error: m.box_plan_invalid_anchor() };
		}
		if (cadence === 'MONTHLY' && (anchorDayOfMonth < 1 || anchorDayOfMonth > 31)) {
			return { payload: null, error: m.box_plan_invalid_anchor() };
		}

		if (plan.type === 'SAVING_GOAL') {
			if (!validMoney(targetAmount)) return { payload: null, error: m.box_plan_invalid_amount() };
			if (!/^\d{4}-\d{2}-\d{2}$/.test(targetDate)) {
				return { payload: null, error: m.box_plan_invalid_date() };
			}
			if (!autoCommitment && !validMoney(customCommitment, true)) {
				return { payload: null, error: m.box_plan_invalid_amount() };
			}
			return {
				payload: {
					targetAmount,
					targetDate,
					cadence,
					anchorWeekday: ['WEEKLY', 'BIWEEKLY'].includes(cadence) ? anchorWeekday : null,
					anchorDayOfMonth: cadence === 'MONTHLY' ? anchorDayOfMonth : null,
					regularCommitment: autoCommitment ? null : customCommitment,
				},
				error: '',
			};
		}

		if (!validMoney(desiredBalance)) return { payload: null, error: m.box_plan_invalid_amount() };
		return {
			payload: {
				desiredBalance,
				cadence,
				anchorWeekday: ['WEEKLY', 'BIWEEKLY'].includes(cadence) ? anchorWeekday : null,
				anchorDayOfMonth: cadence === 'MONTHLY' ? anchorDayOfMonth : null,
			},
			error: '',
		};
	}

	function endpoint(suffix: string): string {
		const path = plan.type === 'SAVING_GOAL' ? 'saving-goal' : 'spending-budget';
		return `/api/boxes/${plan.boxId}/plans/${path}/${plan.id}/${suffix}`;
	}

	$effect(() => {
		// Parent controls this dialog, so initialize on externally driven opens too.
		if (open) initialize();
	});

	$effect(() => {
		if (!open) return;
		// Reading every input here makes the preview follow each valid parameter change.
		const signature = JSON.stringify({
			targetAmount,
			targetDate,
			desiredBalance,
			cadence,
			anchorWeekday,
			anchorDayOfMonth,
			autoCommitment,
			customCommitment,
		});
		const request = currentRequest();
		preview = null;
		previewError = request.error;
		if (!request.payload) return;
		previewLoading = true;
		const controller = new AbortController();
		const timer = window.setTimeout(async () => {
			try {
				const response = await fetch(endpoint('revision-preview'), {
					method: 'POST',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify(request.payload),
					signal: controller.signal,
				});
				if (!response.ok) throw new Error(String(response.status));
				void signature;
				preview = (await response.json()) as BoxPlanRevisionPreview;
				previewError = '';
			} catch (cause) {
				if ((cause as { name?: string }).name !== 'AbortError') {
					previewError = m.box_plan_preview_error();
				}
			} finally {
				if (!controller.signal.aborted) previewLoading = false;
			}
		}, 350);

		return () => {
			window.clearTimeout(timer);
			controller.abort();
			previewLoading = false;
		};
	});

	async function applyRevision() {
		const request = currentRequest();
		if (!request.payload || !preview) return;
		submitting = true;
		try {
			const response = await fetch(endpoint('revisions'), {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify(request.payload),
			});
			if (!response.ok) {
				previewError = m.box_plan_action_error();
				return;
			}
			const updated = (await response.json()) as BoxPlan;
			toast.success(m.box_plan_update_success());
			open = false;
			await onApplied(updated);
		} catch {
			previewError = m.box_plan_action_error();
		} finally {
			submitting = false;
		}
	}

	function date(value: string | null): string {
		return value ? dateFmt.format(new Date(`${value}T00:00:00Z`)) : m.box_plan_none_value();
	}
</script>

<Dialog.Root
	{open}
	onOpenChange={(value) => (open = value)}
>
	<Dialog.Content class="max-h-[90dvh] overflow-y-auto sm:max-w-xl">
		<Dialog.Header>
			<Dialog.Title>{m.box_plan_revision_title()}</Dialog.Title>
			<Dialog.Description>{m.box_plan_revision_description()}</Dialog.Description>
		</Dialog.Header>

		<div class="grid gap-4">
			{#if plan.type === 'SAVING_GOAL'}
				<div class="grid gap-4 sm:grid-cols-2">
					<div class="grid gap-2">
						<Label for="revision-target-amount">{m.box_plan_target_amount()}</Label>
						<Input id="revision-target-amount" type="number" min="0.01" max="9999999999.99" step="0.01" bind:value={targetAmount} />
					</div>
					<div class="grid gap-2">
						<Label for="revision-target-date">{m.box_plan_target_date()}</Label>
						<NativeDatePicker id="revision-target-date" name="revision-target-date" value={targetDate} onValueChange={(value) => (targetDate = value)} />
					</div>
				</div>
			{:else}
				<div class="grid gap-2">
					<Label for="revision-desired-balance">{m.box_plan_desired_balance()}</Label>
					<Input id="revision-desired-balance" type="number" min="0.01" max="9999999999.99" step="0.01" bind:value={desiredBalance} />
				</div>
			{/if}

			<div class="grid gap-4 sm:grid-cols-2">
				<div class="grid gap-2">
					<Label for="revision-cadence">{m.box_plan_cadence()}</Label>
					<NativeSelect id="revision-cadence" name="revision-cadence" value={cadence} onValueChange={(value) => (cadence = value as PlanCadence)} items={cadenceItems} />
				</div>
				{#if cadence === 'WEEKLY' || cadence === 'BIWEEKLY'}
					<div class="grid gap-2">
						<Label for="revision-weekday">{m.box_plan_weekday()}</Label>
						<NativeSelect id="revision-weekday" name="revision-weekday" value={String(anchorWeekday)} onValueChange={(value) => (anchorWeekday = Number(value))} items={weekdayItems} />
					</div>
				{:else if cadence === 'MONTHLY'}
					<div class="grid gap-2">
						<Label for="revision-month-day">{m.box_plan_month_day()}</Label>
						<NativeSelect id="revision-month-day" name="revision-month-day" value={String(anchorDayOfMonth)} onValueChange={(value) => (anchorDayOfMonth = Number(value))} items={monthDayItems} />
					</div>
				{/if}
			</div>

			{#if plan.type === 'SAVING_GOAL'}
				<div class="rounded-lg border p-3">
					<div class="flex items-start gap-3">
						<Checkbox id="revision-auto-commitment" bind:checked={autoCommitment} />
						<div class="grid gap-1">
							<Label for="revision-auto-commitment">{m.box_plan_auto_commitment()}</Label>
							<p class="text-xs text-muted-foreground">{m.box_plan_auto_commitment_description()}</p>
						</div>
					</div>
					{#if !autoCommitment}
						<div class="mt-3 grid gap-2">
							<Label for="revision-commitment">{m.box_plan_regular_commitment()}</Label>
							<Input id="revision-commitment" type="number" min="0" max="9999999999.99" step="0.01" bind:value={customCommitment} />
						</div>
					{/if}
				</div>
			{/if}

			<div class="rounded-lg border bg-muted/30 p-4" aria-live="polite" aria-busy={previewLoading}>
				<div class="flex items-center gap-2">
					<Calculator class="size-4" aria-hidden="true" />
					<p class="font-medium">{m.box_plan_revision_preview()}</p>
				</div>
				{#if previewLoading}
					<p class="mt-2 text-sm text-muted-foreground">{m.box_plan_preview_loading()}</p>
				{:else if preview}
					<div class="mt-3 grid gap-3 text-sm sm:grid-cols-2">
						<div><p class="text-muted-foreground">{m.box_plan_effective_from()}</p><p class="font-medium">{date(preview.effectiveFrom)}</p></div>
						{#if plan.type === 'SAVING_GOAL'}
							{@const goalPreview = preview as SavingGoalRevisionPreview}
							<div><p class="text-muted-foreground">{m.box_plan_suggested_commitment()}</p><p class="font-medium tabular-nums">{fmt.format(goalPreview.regularCommitment)}</p></div>
							<div><p class="text-muted-foreground">{m.box_plan_remaining()}</p><p class="font-medium tabular-nums">{fmt.format(goalPreview.remainingAmount)}</p></div>
							<div><p class="text-muted-foreground">{m.box_plan_remaining_periods()}</p><p class="font-medium tabular-nums">{goalPreview.remainingPeriods}</p></div>
							<div><p class="text-muted-foreground">{m.box_plan_projected_date()}</p><p class="font-medium">{date(goalPreview.projectedCompletionDate)}</p></div>
							<div><p class="text-muted-foreground">{m.box_plan_suggested_extension()}</p><p class="font-medium">{date(goalPreview.suggestedExtensionDate)}</p></div>
						{:else}
							{@const budgetPreview = preview as SpendingBudgetRevisionPreview}
							<div><p class="text-muted-foreground">{m.box_plan_desired_balance()}</p><p class="font-medium tabular-nums">{fmt.format(budgetPreview.desiredBalance)}</p></div>
							<div><p class="text-muted-foreground">{m.box_plan_current_balance()}</p><p class="font-medium tabular-nums">{fmt.format(budgetPreview.currentBalance)}</p></div>
							<div><p class="text-muted-foreground">{m.box_plan_suggested_top_up()}</p><p class="font-medium tabular-nums">{fmt.format(budgetPreview.suggestedTopUp)}</p></div>
						{/if}
					</div>
					<p class="mt-3 flex items-center gap-1 text-xs text-muted-foreground"><CalendarClock class="size-3" aria-hidden="true" />{m.box_plan_preview_hint()}</p>
				{:else if previewError}
					<p class="mt-2 text-sm text-destructive">{previewError}</p>
				{/if}
			</div>

			{#if previewError && preview}
				<Alert.Root variant="destructive"><Alert.Description>{previewError}</Alert.Description></Alert.Root>
			{/if}
		</div>

		<Dialog.Footer>
			<Button variant="outline" onclick={() => (open = false)}>{m.common_cancel()}</Button>
			<Button onclick={applyRevision} disabled={submitting || previewLoading || !preview}>
				{submitting ? m.common_processing() : m.box_plan_apply_changes()}
			</Button>
		</Dialog.Footer>
	</Dialog.Content>
</Dialog.Root>
