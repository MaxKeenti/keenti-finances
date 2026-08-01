<script lang="ts">
	import PencilIcon from '@lucide/svelte/icons/pencil';
	import PlusIcon from '@lucide/svelte/icons/plus';
	import SparklesIcon from '@lucide/svelte/icons/sparkles';
	import Trash2Icon from '@lucide/svelte/icons/trash-2';
	import { toast } from 'svelte-sonner';
	import { adaptiveConfirm } from '$lib/components/adaptive-confirm';
	import * as Alert from '$lib/components/ui/alert';
	import { Badge } from '$lib/components/ui/badge';
	import { Button } from '$lib/components/ui/button';
	import * as Card from '$lib/components/ui/card';
	import { Checkbox } from '$lib/components/ui/checkbox';
	import * as Dialog from '$lib/components/ui/dialog';
	import * as Empty from '$lib/components/ui/empty';
	import { Input } from '$lib/components/ui/input';
	import { Label } from '$lib/components/ui/label';
	import { NativeSelect } from '$lib/components/native-select';
	import { mxnFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
	import type {
		FundingTriggerDto,
		FundingTriggerInput,
		FundingTriggerStrategy,
	} from '$lib/types/funding-triggers';
	import { hasAtMostTwoDecimalPlaces } from '$lib/types/transactions';

	type IngressCategory = {
		id: number;
		name: string;
		type: string;
	};

	let {
		boxId,
		archived = false,
		hasActivePlan = false,
		locale,
	}: {
		boxId: number;
		archived?: boolean;
		hasActivePlan?: boolean;
		locale: string;
	} = $props();

	let triggers = $state<FundingTriggerDto[]>([]);
	let categories = $state<IngressCategory[]>([]);
	let loading = $state(true);
	let loadError = $state(false);
	let refreshNonce = $state(0);
	let editorOpen = $state(false);
	let editingTrigger = $state<FundingTriggerDto | null>(null);
	let categoryId = $state(0);
	let strategy = $state<FundingTriggerStrategy>('FIXED_AMOUNT');
	let fixedAmount = $state(0);
	let percentage = $state(0);
	let enabled = $state(true);
	let saving = $state(false);
	let formError = $state<string | null>(null);

	const fmt = $derived(mxnFormatter(locale));
	const availableCategories = $derived(
		categories.filter(
			(category) =>
				!triggers.some(
					(trigger) =>
						trigger.categoryId === category.id && trigger.id !== editingTrigger?.id,
				),
		),
	);
	const fixedAmountInvalid = $derived(
		strategy === 'FIXED_AMOUNT' &&
		(fixedAmount <= 0 || fixedAmount > 9_999_999_999.99 || !hasAtMostTwoDecimalPlaces(fixedAmount)),
	);
	const percentageInvalid = $derived(
		strategy === 'PERCENTAGE' &&
		(percentage <= 0 ||
			percentage > 100 ||
			Math.abs(percentage * 10_000 - Math.round(percentage * 10_000)) >= 1e-7),
	);
	const formInvalid = $derived(
		categoryId <= 0 || fixedAmountInvalid || percentageInvalid,
	);
	const planStrategyVisible = $derived(
		hasActivePlan || editingTrigger?.strategy === 'PLAN_DERIVED',
	);
	const planEnableBlocked = $derived(
		!hasActivePlan && strategy === 'PLAN_DERIVED' && !enabled,
	);

	function strategyLabel(value: FundingTriggerStrategy): string {
		if (value === 'PLAN_DERIVED') return m.funding_trigger_strategy_plan();
		if (value === 'PERCENTAGE') return m.funding_trigger_strategy_percentage();
		return m.funding_trigger_strategy_fixed();
	}

	function triggerValue(trigger: FundingTriggerDto): string {
		if (trigger.strategy === 'PLAN_DERIVED') return m.funding_trigger_plan_current_amount();
		if (trigger.strategy === 'PERCENTAGE') {
			return m.funding_trigger_percentage_value({ percentage: Number(trigger.percentage ?? 0) });
		}
		return fmt.format(trigger.fixedAmount ?? 0);
	}

	function cannotEnable(trigger: FundingTriggerDto): boolean {
		return !trigger.enabled && trigger.strategy === 'PLAN_DERIVED' && !hasActivePlan;
	}

	function openCreate() {
		editingTrigger = null;
		categoryId = availableCategories[0]?.id ?? 0;
		strategy = 'FIXED_AMOUNT';
		fixedAmount = 0;
		percentage = 0;
		enabled = true;
		formError = null;
		editorOpen = true;
	}

	function openEdit(trigger: FundingTriggerDto) {
		editingTrigger = trigger;
		categoryId = trigger.categoryId;
		strategy = trigger.strategy;
		fixedAmount = Number(trigger.fixedAmount ?? 0);
		percentage = Number(trigger.percentage ?? 0);
		enabled = trigger.enabled;
		formError = null;
		editorOpen = true;
	}

	function changeNumber(field: 'fixed' | 'percentage', event: Event) {
		const input = event.currentTarget as HTMLInputElement;
		const value = Number.isFinite(input.valueAsNumber) ? input.valueAsNumber : 0;
		if (field === 'fixed') fixedAmount = value;
		else percentage = value;
	}

	async function responseError(response: Response, fallback: string): Promise<string> {
		try {
			const body = (await response.json()) as { error?: string; details?: string };
			return body.error || body.details || fallback;
		} catch {
			return fallback;
		}
	}

	async function saveTrigger(event: SubmitEvent) {
		event.preventDefault();
		if (formInvalid || saving) return;
		saving = true;
		formError = null;

		const payload: FundingTriggerInput = {
			categoryId,
			strategy,
			enabled,
			...(strategy === 'FIXED_AMOUNT' ? { fixedAmount } : {}),
			...(strategy === 'PERCENTAGE' ? { percentage } : {}),
		};
		const path = editingTrigger
			? `/api/boxes/${boxId}/funding-triggers/${editingTrigger.id}`
			: `/api/boxes/${boxId}/funding-triggers`;

		try {
			const response = await fetch(path, {
				method: editingTrigger ? 'PUT' : 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify(payload),
			});
			if (!response.ok) {
				formError = await responseError(response, m.funding_trigger_save_error());
				return;
			}
			editorOpen = false;
			toast.success(
				editingTrigger ? m.funding_trigger_updated() : m.funding_trigger_created(),
			);
			refreshNonce += 1;
		} catch {
			formError = m.funding_trigger_save_error();
		} finally {
			saving = false;
		}
	}

	async function toggleEnabled(trigger: FundingTriggerDto) {
		try {
			const response = await fetch(
				`/api/boxes/${boxId}/funding-triggers/${trigger.id}/enabled`,
				{
					method: 'PUT',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify({ enabled: !trigger.enabled }),
				},
			);
			if (!response.ok) {
				toast.error(await responseError(response, m.funding_trigger_toggle_error()));
				return;
			}
			const updated = (await response.json()) as FundingTriggerDto;
			triggers = triggers.map((candidate) =>
				candidate.id === updated.id ? updated : candidate,
			);
			toast.success(
				updated.enabled ? m.funding_trigger_enabled() : m.funding_trigger_disabled(),
			);
		} catch {
			toast.error(m.funding_trigger_toggle_error());
		}
	}

	async function deleteTrigger(trigger: FundingTriggerDto) {
		const confirmed = await adaptiveConfirm({
			title: m.funding_trigger_delete_title(),
			description: m.funding_trigger_delete_description({ category: trigger.categoryName }),
			confirmLabel: m.common_delete(),
			cancelLabel: m.common_cancel(),
			destructive: true,
		});
		if (!confirmed) return;

		try {
			const response = await fetch(`/api/boxes/${boxId}/funding-triggers/${trigger.id}`, {
				method: 'DELETE',
			});
			if (!response.ok) {
				toast.error(await responseError(response, m.funding_trigger_delete_error()));
				return;
			}
			triggers = triggers.filter((candidate) => candidate.id !== trigger.id);
			toast.success(m.funding_trigger_deleted());
		} catch {
			toast.error(m.funding_trigger_delete_error());
		}
	}

	$effect(() => {
		const requestedBoxId = boxId;
		refreshNonce;
		if (archived || !Number.isInteger(requestedBoxId) || requestedBoxId <= 0) {
			triggers = [];
			categories = [];
			loading = false;
			return;
		}

		loading = true;
		loadError = false;
		const controller = new AbortController();
		void Promise.all([
			fetch(`/api/boxes/${requestedBoxId}/funding-triggers`, {
				cache: 'no-store',
				signal: controller.signal,
			}),
			fetch('/api/categories', { cache: 'no-store', signal: controller.signal }),
		])
			.then(async ([triggerResponse, categoryResponse]) => {
				if (!triggerResponse.ok || !categoryResponse.ok) throw new Error('Funding Trigger load failed');
				const [loadedTriggers, loadedCategories] = await Promise.all([
					triggerResponse.json() as Promise<FundingTriggerDto[]>,
					categoryResponse.json() as Promise<IngressCategory[]>,
				]);
				triggers = Array.isArray(loadedTriggers) ? loadedTriggers : [];
				categories = (Array.isArray(loadedCategories) ? loadedCategories : [])
					.filter((category) => category.type === 'INGRESS' || category.type === 'BOTH')
					.toSorted((left, right) => left.name.localeCompare(right.name));
				loading = false;
			})
			.catch((error) => {
				if (controller.signal.aborted) return;
				console.error(
					'[funding-triggers] load failed',
					error instanceof Error ? error.message : error,
				);
				loading = false;
				loadError = true;
			});

		return () => controller.abort();
	});
</script>

{#if !archived}
	<section class="space-y-3" aria-labelledby="funding-trigger-settings-title">
		<div class="flex flex-wrap items-start justify-between gap-3">
			<div>
				<h2 id="funding-trigger-settings-title" class="text-lg font-semibold">
					{m.funding_trigger_settings_title()}
				</h2>
				<p class="text-sm text-muted-foreground">{m.funding_trigger_settings_description()}</p>
			</div>
			<Button
				type="button"
				size="sm"
				onclick={openCreate}
				disabled={loading || availableCategories.length === 0}
			>
				<PlusIcon data-icon="inline-start" />{m.funding_trigger_add()}
			</Button>
		</div>

		{#if loading}
			<Card.Root aria-busy="true"><Card.Content class="py-6 text-sm text-muted-foreground">{m.funding_trigger_loading()}</Card.Content></Card.Root>
		{:else if loadError}
			<Alert.Root variant="destructive">
				<Alert.Description class="flex flex-wrap items-center justify-between gap-2">
					<span>{m.funding_trigger_load_error()}</span>
					<Button type="button" size="sm" variant="outline" onclick={() => (refreshNonce += 1)}>
						{m.funding_trigger_retry()}
					</Button>
				</Alert.Description>
			</Alert.Root>
		{:else if triggers.length === 0}
			<Empty.Root class="border">
				<Empty.Media variant="icon"><SparklesIcon /></Empty.Media>
				<Empty.Header>
					<Empty.Title>{m.funding_trigger_empty_title()}</Empty.Title>
					<Empty.Description>{m.funding_trigger_empty_description()}</Empty.Description>
				</Empty.Header>
			</Empty.Root>
		{:else}
			<div class="grid gap-3 sm:grid-cols-2">
				{#each triggers as trigger (trigger.id)}
					<Card.Root class={trigger.enabled && !(trigger.strategy === 'PLAN_DERIVED' && !hasActivePlan) ? '' : 'opacity-70'}>
						<Card.Header class="gap-2 pb-3">
							<div class="flex min-w-0 items-start justify-between gap-2">
								<div class="min-w-0">
									<Card.Title class="truncate text-base">{trigger.categoryName}</Card.Title>
									<Card.Description>{strategyLabel(trigger.strategy)}</Card.Description>
								</div>
								<Badge variant={trigger.enabled && !(trigger.strategy === 'PLAN_DERIVED' && !hasActivePlan) ? 'default' : 'secondary'}>
									{trigger.enabled && trigger.strategy === 'PLAN_DERIVED' && !hasActivePlan
										? m.funding_trigger_status_paused()
										: trigger.enabled
											? m.funding_trigger_status_enabled()
											: m.funding_trigger_status_disabled()}
								</Badge>
							</div>
						</Card.Header>
						<Card.Content class="space-y-3">
							<p class="text-sm font-medium tabular-nums">{triggerValue(trigger)}</p>
							{#if trigger.strategy === 'PLAN_DERIVED' && !hasActivePlan}
								<p class="text-xs text-amber-700 dark:text-amber-400">
									{trigger.enabled ? m.funding_trigger_plan_paused() : m.funding_trigger_plan_unavailable()}
								</p>
							{/if}
							<div class="flex flex-wrap gap-2">
								<Button
									type="button"
									size="sm"
									variant="outline"
									onclick={() => toggleEnabled(trigger)}
									disabled={cannotEnable(trigger)}
									title={cannotEnable(trigger) ? m.funding_trigger_plan_unavailable() : undefined}
								>
									{trigger.enabled ? m.funding_trigger_disable() : m.funding_trigger_enable()}
								</Button>
								<Button type="button" size="sm" variant="ghost" onclick={() => openEdit(trigger)}>
									<PencilIcon data-icon="inline-start" />{m.common_edit()}
								</Button>
								<Button type="button" size="sm" variant="ghost" class="text-destructive hover:text-destructive" onclick={() => deleteTrigger(trigger)}>
									<Trash2Icon data-icon="inline-start" />{m.common_delete()}
								</Button>
							</div>
						</Card.Content>
					</Card.Root>
				{/each}
			</div>
		{/if}
	</section>
{/if}

<Dialog.Root bind:open={editorOpen}>
	<Dialog.Content class="sm:max-w-md">
		<Dialog.Header>
			<Dialog.Title>
				{editingTrigger ? m.funding_trigger_edit_title() : m.funding_trigger_create_title()}
			</Dialog.Title>
			<Dialog.Description>{m.funding_trigger_editor_description()}</Dialog.Description>
		</Dialog.Header>

		{#if formError}
			<Alert.Root variant="destructive"><Alert.Description>{formError}</Alert.Description></Alert.Root>
		{/if}

		<form class="grid gap-4" onsubmit={saveTrigger}>
			<div class="grid gap-2">
				<Label for="funding-trigger-category">{m.common_category()}</Label>
				<NativeSelect
					id="funding-trigger-category"
					name="categoryId"
					value={categoryId > 0 ? String(categoryId) : ''}
					onValueChange={(value) => (categoryId = value ? Number(value) : 0)}
					items={availableCategories.map((category) => ({ value: String(category.id), label: category.name }))}
					placeholder={m.common_select_category()}
					required
				/>
			</div>

			<div class="grid gap-2">
				<Label for="funding-trigger-strategy">{m.funding_trigger_strategy()}</Label>
				<NativeSelect
					id="funding-trigger-strategy"
					name="strategy"
					value={strategy}
					onValueChange={(value) => (strategy = value as FundingTriggerStrategy)}
					items={[
						{ value: 'FIXED_AMOUNT', label: m.funding_trigger_strategy_fixed() },
						{ value: 'PERCENTAGE', label: m.funding_trigger_strategy_percentage() },
						...(planStrategyVisible
							? [{ value: 'PLAN_DERIVED', label: m.funding_trigger_strategy_plan() }]
							: []),
					]}
				/>
			</div>

			{#if strategy === 'FIXED_AMOUNT'}
				<div class="grid gap-2">
					<Label for="funding-trigger-fixed">{m.funding_trigger_fixed_amount()}</Label>
					<Input
						id="funding-trigger-fixed"
						type="number"
						min="0.01"
						max="9999999999.99"
						step="0.01"
						value={fixedAmount || ''}
						oninput={(event) => changeNumber('fixed', event)}
						aria-invalid={fixedAmountInvalid || undefined}
						required
					/>
					{#if fixedAmountInvalid && fixedAmount > 0}
						<p class="text-xs text-destructive">{m.validation_amount_two_decimals()}</p>
					{/if}
				</div>
			{:else if strategy === 'PERCENTAGE'}
				<div class="grid gap-2">
					<Label for="funding-trigger-percentage">{m.funding_trigger_percentage()}</Label>
					<Input
						id="funding-trigger-percentage"
						type="number"
						min="0.0001"
						max="100"
						step="0.0001"
						value={percentage || ''}
						oninput={(event) => changeNumber('percentage', event)}
						aria-invalid={percentageInvalid || undefined}
						required
					/>
					<p class="text-xs text-muted-foreground">{m.funding_trigger_percentage_hint()}</p>
				</div>
			{:else}
				<Alert.Root>
					<Alert.Description>
						{hasActivePlan
							? m.funding_trigger_plan_hint()
							: m.funding_trigger_plan_unavailable_editor()}
					</Alert.Description>
				</Alert.Root>
			{/if}

			<label class="flex items-center gap-3 rounded-md border p-3 text-sm {planEnableBlocked ? 'opacity-60' : ''}">
				<Checkbox
					checked={enabled}
					onclick={() => (enabled = !enabled)}
					disabled={planEnableBlocked}
				/>
				<span>
					<span class="block font-medium">{m.funding_trigger_enabled_on_save()}</span>
					<span class="block text-xs text-muted-foreground">{m.funding_trigger_enabled_hint()}</span>
				</span>
			</label>

			<Dialog.Footer>
				<Button type="button" variant="outline" onclick={() => (editorOpen = false)}>{m.common_cancel()}</Button>
				<Button type="submit" disabled={saving || formInvalid}>
					{saving ? m.common_saving() : editingTrigger ? m.common_update() : m.common_create()}
				</Button>
			</Dialog.Footer>
		</form>
	</Dialog.Content>
</Dialog.Root>
