<script lang="ts">
	import { Target, WalletCards } from '@lucide/svelte';
	import { untrack } from 'svelte';
	import { toast } from 'svelte-sonner';
	import * as Alert from '$lib/components/ui/alert';
	import { Button } from '$lib/components/ui/button';
	import * as Dialog from '$lib/components/ui/dialog';
	import { Input } from '$lib/components/ui/input';
	import { Label } from '$lib/components/ui/label';
	import { NativeDatePicker } from '$lib/components/native-date-picker';
	import { NativeSelect } from '$lib/components/native-select';
	import { mxnFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
	import type { BoxPlan, BoxPlanType, PlanCadence } from '$lib/types/box-plans';

	let {
		open = $bindable(false),
		boxId,
		boxBalance,
		today,
		locale,
		onCreated,
	}: {
		open?: boolean;
		boxId: number;
		boxBalance: number;
		today: string;
		locale: string;
		onCreated: (plan: BoxPlan) => void | Promise<void>;
	} = $props();

	let planType = $state<BoxPlanType>('SAVING_GOAL');
	let targetAmount = $state(0);
	let targetDate = $state('');
	let desiredBalance = $state(0);
	let cadence = $state<PlanCadence>('MONTHLY');
	let anchorWeekday = $state(untrack(() => isoWeekday(today)));
	let anchorDayOfMonth = $state(untrack(() => Number(today.slice(8, 10)) || 1));
	let regularCommitment = $state<number | undefined>(undefined);
	let submitting = $state(false);
	let formError = $state('');

	const fmt = $derived(mxnFormatter(locale));
	const remaining = $derived(Math.max(targetAmount - boxBalance, 0));
	const estimatedPeriods = $derived(periodEstimate(today, targetDate, cadence));
	const estimatedCommitment = $derived(
		estimatedPeriods > 0 ? Math.ceil((remaining / estimatedPeriods) * 100) / 100 : remaining,
	);
	const topUp = $derived(Math.max(desiredBalance - boxBalance, 0));
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

	function isoWeekday(date: string): number {
		const value = new Date(`${date}T00:00:00Z`).getUTCDay();
		return value === 0 ? 7 : value;
	}

	function weekdays(currentLocale: string): { value: string; label: string }[] {
		const formatter = new Intl.DateTimeFormat(currentLocale === 'en' ? 'en-US' : 'es-MX', {
			weekday: 'long',
			timeZone: 'UTC',
		});
		return Array.from({ length: 7 }, (_, index) => ({
			value: String(index + 1),
			label: formatter.format(new Date(Date.UTC(2024, 0, index + 1))),
		}));
	}

	function periodEstimate(start: string, end: string, value: PlanCadence): number {
		if (!/^\d{4}-\d{2}-\d{2}$/.test(end) || end < start) return 0;
		const startDate = new Date(`${start}T00:00:00Z`);
		const endDate = new Date(`${end}T00:00:00Z`);
		const days = Math.floor((endDate.getTime() - startDate.getTime()) / 86_400_000) + 1;
		if (value === 'DAILY') return days;
		if (value === 'WEEKLY') return Math.max(1, Math.ceil(days / 7));
		if (value === 'BIWEEKLY') return Math.max(1, Math.ceil(days / 14));
		return Math.max(
			1,
			(endDate.getUTCFullYear() - startDate.getUTCFullYear()) * 12 +
				endDate.getUTCMonth() -
				startDate.getUTCMonth() +
				1,
		);
	}

	function validMoney(value: number | undefined, allowZero = false): boolean {
		if (value === undefined || !Number.isFinite(value)) return false;
		if (allowZero ? value < 0 : value <= 0) return false;
		return value <= 9_999_999_999.99 && Math.abs(value * 100 - Math.round(value * 100)) < 1e-7;
	}

	function requestPayload(): Record<string, unknown> | null {
		formError = '';
		if (planType === 'SAVING_GOAL') {
			if (!validMoney(targetAmount)) {
				formError = m.box_plan_invalid_amount();
				return null;
			}
			if (!/^\d{4}-\d{2}-\d{2}$/.test(targetDate) || targetDate < today) {
				formError = m.box_plan_invalid_date();
				return null;
			}
			if (regularCommitment !== undefined && !validMoney(regularCommitment, true)) {
				formError = m.box_plan_invalid_amount();
				return null;
			}
			return {
				targetAmount,
				targetDate,
				cadence,
				anchorWeekday: ['WEEKLY', 'BIWEEKLY'].includes(cadence) ? anchorWeekday : null,
				anchorDayOfMonth: cadence === 'MONTHLY' ? anchorDayOfMonth : null,
				regularCommitment: regularCommitment ?? null,
			};
		}

		if (!validMoney(desiredBalance)) {
			formError = m.box_plan_invalid_amount();
			return null;
		}
		return {
			desiredBalance,
			cadence,
			anchorWeekday: ['WEEKLY', 'BIWEEKLY'].includes(cadence) ? anchorWeekday : null,
			anchorDayOfMonth: cadence === 'MONTHLY' ? anchorDayOfMonth : null,
		};
	}

	async function submit() {
		const payload = requestPayload();
		if (!payload) return;
		submitting = true;
		try {
			const path = planType === 'SAVING_GOAL' ? 'saving-goal' : 'spending-budget';
			const response = await fetch(`/api/boxes/${boxId}/plans/${path}`, {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify(payload),
			});
			if (!response.ok) {
				formError = m.box_plan_action_error();
				return;
			}
			const plan = (await response.json()) as BoxPlan;
			toast.success(m.box_plan_create_success());
			open = false;
			await onCreated(plan);
		} catch {
			formError = m.box_plan_action_error();
		} finally {
			submitting = false;
		}
	}
</script>

<Dialog.Root bind:open>
	<Dialog.Content class="max-h-[90dvh] overflow-y-auto sm:max-w-xl">
		<Dialog.Header>
			<Dialog.Title>{m.box_plan_create_title()}</Dialog.Title>
			<Dialog.Description>{m.box_plan_create_description()}</Dialog.Description>
		</Dialog.Header>

		<div class="grid gap-4">
			<div class="grid gap-2 sm:grid-cols-2" role="group" aria-label={m.common_type()}>
				<Button
					variant={planType === 'SAVING_GOAL' ? 'secondary' : 'outline'}
					class="h-auto items-start justify-start gap-3 p-3 text-left whitespace-normal"
					aria-pressed={planType === 'SAVING_GOAL'}
					onclick={() => (planType = 'SAVING_GOAL')}
				>
					<Target class="mt-0.5 size-4" aria-hidden="true" />
					<span><strong class="block">{m.box_plan_saving_goal()}</strong><span class="text-xs font-normal text-muted-foreground">{m.box_plan_saving_goal_description()}</span></span>
				</Button>
				<Button
					variant={planType === 'SPENDING_BUDGET' ? 'secondary' : 'outline'}
					class="h-auto items-start justify-start gap-3 p-3 text-left whitespace-normal"
					aria-pressed={planType === 'SPENDING_BUDGET'}
					onclick={() => (planType = 'SPENDING_BUDGET')}
				>
					<WalletCards class="mt-0.5 size-4" aria-hidden="true" />
					<span><strong class="block">{m.box_plan_spending_budget()}</strong><span class="text-xs font-normal text-muted-foreground">{m.box_plan_spending_budget_description()}</span></span>
				</Button>
			</div>

			{#if planType === 'SAVING_GOAL'}
				<div class="grid gap-4 sm:grid-cols-2">
					<div class="grid gap-2">
						<Label for="plan-target-amount">{m.box_plan_target_amount()}</Label>
						<Input id="plan-target-amount" type="number" min="0.01" max="9999999999.99" step="0.01" bind:value={targetAmount} />
					</div>
					<div class="grid gap-2">
						<Label for="plan-target-date">{m.box_plan_target_date()}</Label>
						<NativeDatePicker id="plan-target-date" name="plan-target-date" value={targetDate} onValueChange={(value) => (targetDate = value)} min={today} />
					</div>
				</div>
			{:else}
				<div class="grid gap-2">
					<Label for="plan-desired-balance">{m.box_plan_desired_balance()}</Label>
					<Input id="plan-desired-balance" type="number" min="0.01" max="9999999999.99" step="0.01" bind:value={desiredBalance} />
				</div>
			{/if}

			<div class="grid gap-4 sm:grid-cols-2">
				<div class="grid gap-2">
					<Label for="plan-cadence">{m.box_plan_cadence()}</Label>
					<NativeSelect id="plan-cadence" name="plan-cadence" value={cadence} onValueChange={(value) => (cadence = value as PlanCadence)} items={cadenceItems} />
				</div>
				{#if cadence === 'WEEKLY' || cadence === 'BIWEEKLY'}
					<div class="grid gap-2">
						<Label for="plan-weekday">{m.box_plan_weekday()}</Label>
						<NativeSelect id="plan-weekday" name="plan-weekday" value={String(anchorWeekday)} onValueChange={(value) => (anchorWeekday = Number(value))} items={weekdayItems} />
					</div>
				{:else if cadence === 'MONTHLY'}
					<div class="grid gap-2">
						<Label for="plan-month-day">{m.box_plan_month_day()}</Label>
						<NativeSelect id="plan-month-day" name="plan-month-day" value={String(anchorDayOfMonth)} onValueChange={(value) => (anchorDayOfMonth = Number(value))} items={monthDayItems} />
					</div>
				{/if}
			</div>

			{#if planType === 'SAVING_GOAL'}
				<div class="grid gap-2">
					<Label for="plan-commitment">{m.box_plan_regular_commitment_optional()}</Label>
					<Input id="plan-commitment" type="number" min="0" max="9999999999.99" step="0.01" bind:value={regularCommitment} placeholder={m.box_plan_auto_commitment()} />
					<p class="text-xs text-muted-foreground">{m.box_plan_auto_commitment_description()}</p>
				</div>
			{/if}

			<div class="rounded-lg border bg-muted/30 p-3">
				<p class="text-sm font-medium">{m.box_plan_guidance_title()}</p>
				<p class="mt-1 text-sm text-muted-foreground">
					{planType === 'SAVING_GOAL'
						? m.box_plan_guidance_goal({ amount: fmt.format(regularCommitment ?? estimatedCommitment), periods: estimatedPeriods })
						: m.box_plan_guidance_budget({ amount: fmt.format(topUp) })}
				</p>
			</div>

			{#if formError}
				<Alert.Root variant="destructive"><Alert.Description>{formError}</Alert.Description></Alert.Root>
			{/if}
		</div>

		<Dialog.Footer>
			<Button variant="outline" onclick={() => (open = false)}>{m.common_cancel()}</Button>
			<Button onclick={submit} disabled={submitting}>{submitting ? m.common_processing() : m.box_plan_create()}</Button>
		</Dialog.Footer>
	</Dialog.Content>
</Dialog.Root>
