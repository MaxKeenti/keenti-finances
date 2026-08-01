<script lang="ts">
	import CirclePlusIcon from '@lucide/svelte/icons/circle-plus';
	import Trash2Icon from '@lucide/svelte/icons/trash-2';
	import * as Alert from '$lib/components/ui/alert';
	import { Button } from '$lib/components/ui/button';
	import { Input } from '$lib/components/ui/input';
	import { NativeSelect } from '$lib/components/native-select';
	import { mxnFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
	import {
		allocationTotal,
		amountToCents,
		centsToAmount,
		hasAtMostTwoDecimalPlaces,
		type BoxAllocationInput,
		type BoxFundingDto,
		type TransactionDirection,
	} from '$lib/types/transactions';

	type AllocationKind = 'funding' | 'distribution';
	type BoxOption = {
		id: number;
		name: string;
		balance: number;
		archived?: boolean;
	};

	let {
		kind,
		boxes,
		allocations,
		onChange,
		transactionAmount,
		transactionDate,
		today,
		availableBefore,
		locale,
		originalAmount = 0,
		originalDirection = null,
		originalFunding = [],
		categoryName = null,
		disabled = false,
	}: {
		kind: AllocationKind;
		boxes: BoxOption[];
		allocations: BoxAllocationInput[];
		onChange: (allocations: BoxAllocationInput[]) => void;
		transactionAmount: number;
		transactionDate: string;
		today: string;
		availableBefore: number;
		locale: string;
		originalAmount?: number;
		originalDirection?: TransactionDirection | null;
		originalFunding?: BoxFundingDto[];
		categoryName?: string | null;
		disabled?: boolean;
	} = $props();

	const fmt = $derived(mxnFormatter(locale));
	const total = $derived(allocationTotal(allocations));
	const originalFundingTotal = $derived(allocationTotal(originalFunding));
	const isFutureDated = $derived(Boolean(transactionDate && transactionDate > today));
	const maximumTotal = $derived(
		kind === 'funding'
			? Math.max(0, Number(transactionAmount) || 0)
			: Number(transactionAmount) > 0
				? Math.max(0, availableBefore + Number(transactionAmount))
				: 0,
	);
	const oldNetContribution = $derived(
		originalDirection === 'INGRESS'
			? originalAmount
			: originalDirection === 'EGRESS'
				? -originalAmount
				: 0,
	);
	const projectedAvailable = $derived(
		kind === 'funding'
			? availableBefore - transactionAmount - oldNetContribution - originalFundingTotal + total
			: availableBefore + transactionAmount - total,
	);
	const unallocated = $derived(
		Math.max(0, centsToAmount(amountToCents(maximumTotal) - amountToCents(total))),
	);

	const knownBoxes = $derived.by(() => {
		const result: BoxOption[] = [...boxes];
		const activeIds = new Set(boxes.map((box) => box.id));
		for (const funding of originalFunding) {
			if (!activeIds.has(funding.boxId)) {
				result.push({
					id: funding.boxId,
					name: funding.boxName,
					balance: 0,
					archived: true,
				});
			}
		}
		return result;
	});

	const selectedIds = $derived(new Set(allocations.map((allocation) => allocation.boxId)));
	const unusedBoxes = $derived(knownBoxes.filter((box) => !selectedIds.has(box.id) && !box.archived));
	const hasLineOverBalance = $derived(
		kind === 'funding' &&
			allocations.some((allocation) => amountToCents(allocation.amount) > amountToCents(maxForBox(allocation.boxId))),
	);
	const overTotal = $derived(amountToCents(total) > amountToCents(maximumTotal));
	const canAdd = $derived(
		!disabled && !isFutureDated && unusedBoxes.length > 0 && amountToCents(unallocated) > 0,
	);
	const suggestedBoxes = $derived(
		kind === 'funding' && categoryName
			? unusedBoxes
					.filter((box) => maxForBox(box.id) > 0 && namesAreRelated(box.name, categoryName))
					.slice(0, 3)
			: [],
	);

	function normalizedName(value: string): string {
		return value
			.normalize('NFD')
			.replace(/\p{Diacritic}/gu, '')
			.toLocaleLowerCase(locale)
			.replace(/[^\p{Letter}\p{Number}]+/gu, ' ')
			.trim();
	}

	function namesAreRelated(boxName: string, category: string): boolean {
		const box = normalizedName(boxName);
		const categoryValue = normalizedName(category);
		return Boolean(
			box &&
				categoryValue &&
				(box === categoryValue || box.includes(categoryValue) || categoryValue.includes(box)),
		);
	}

	function boxFor(boxId: number) {
		return knownBoxes.find((box) => box.id === boxId);
	}

	function originalAmountForBox(boxId: number) {
		return originalFunding.find((funding) => funding.boxId === boxId)?.amount ?? 0;
	}

	function maxForBox(boxId: number) {
		const box = boxFor(boxId);
		if (!box) return 0;
		return centsToAmount(amountToCents(box.balance) + amountToCents(originalAmountForBox(boxId)));
	}

	function itemsFor(index: number) {
		const currentId = allocations[index]?.boxId;
		const otherSelected = new Set(
			allocations
				.filter((_, allocationIndex) => allocationIndex !== index)
				.map((allocation) => allocation.boxId),
		);
		return knownBoxes
			.filter((box) => !otherSelected.has(box.id))
			.map((box) => ({
				value: String(box.id),
				label: box.archived
					? m.transactions_box_archived_option({ name: box.name })
					: `${box.name} · ${fmt.format(box.balance)}`,
			}))
			.sort((left, right) => {
				if (left.value === String(currentId)) return -1;
				if (right.value === String(currentId)) return 1;
				return left.label.localeCompare(right.label);
			});
	}

	function addAllocation() {
		const box = unusedBoxes[0];
		if (!box) return;
		onChange([...allocations, { boxId: box.id, amount: 0 }]);
	}

	function applySuggestedBox(box: BoxOption) {
		const amount = centsToAmount(
			Math.min(amountToCents(unallocated), amountToCents(maxForBox(box.id))),
		);
		if (amount <= 0) return;
		onChange([...allocations, { boxId: box.id, amount }]);
	}

	function changeBox(index: number, value: string) {
		const boxId = Number(value);
		if (!Number.isInteger(boxId)) return;
		onChange(allocations.map((allocation, current) => (current === index ? { ...allocation, boxId } : allocation)));
	}

	function changeAmount(index: number, event: Event) {
		const input = event.currentTarget as HTMLInputElement;
		const amount = Number.isFinite(input.valueAsNumber) ? input.valueAsNumber : 0;
		onChange(allocations.map((allocation, current) => (current === index ? { ...allocation, amount } : allocation)));
	}

	function removeAllocation(index: number) {
		onChange(allocations.filter((_, current) => current !== index));
	}
</script>

<section class="space-y-3 rounded-lg border bg-muted/25 p-3" aria-label={kind === 'funding' ? m.transactions_box_funding_title() : m.transactions_box_distribution_title()}>
	<div class="space-y-1">
		<h3 class="text-sm font-medium">
			{kind === 'funding' ? m.transactions_box_funding_title() : m.transactions_box_distribution_title()}
		</h3>
		<p class="text-xs text-muted-foreground">
			{kind === 'funding'
				? m.transactions_box_funding_description()
				: m.transactions_box_distribution_description()}
		</p>
	</div>

	{#if knownBoxes.length === 0}
		<div class="flex flex-wrap items-center justify-between gap-2 rounded-md border border-dashed p-3">
			<p class="text-xs text-muted-foreground">{m.transactions_boxes_empty()}</p>
			<Button href="/boxes" variant="outline" size="sm">{m.transactions_manage_boxes()}</Button>
		</div>
	{:else}
		{#if suggestedBoxes.length > 0}
			<div class="rounded-md border border-dashed bg-background p-3">
				<p class="text-xs text-muted-foreground">
					{m.transactions_box_category_suggestion({ category: categoryName ?? '' })}
				</p>
				<div class="mt-2 flex flex-wrap gap-2">
					{#each suggestedBoxes as box (box.id)}
						{@const suggestedAmount = centsToAmount(
							Math.min(amountToCents(unallocated), amountToCents(maxForBox(box.id))),
						)}
						<Button
							type="button"
							variant="outline"
							size="sm"
							onclick={() => applySuggestedBox(box)}
							disabled={disabled || isFutureDated || suggestedAmount <= 0}
						>
							{m.transactions_box_use_suggestion({
								name: box.name,
								amount: fmt.format(suggestedAmount),
							})}
						</Button>
					{/each}
				</div>
			</div>
		{/if}

		{#each allocations as allocation, index (`${allocation.boxId}-${index}`)}
			{@const selectedBox = boxFor(allocation.boxId)}
			{@const archived = Boolean(selectedBox?.archived)}
			{@const lineAmountInvalid = allocation.amount <= 0}
			{@const linePrecisionInvalid = !lineAmountInvalid && !hasAtMostTwoDecimalPlaces(allocation.amount)}
			{@const lineOverBalance = kind === 'funding' && amountToCents(allocation.amount) > amountToCents(maxForBox(allocation.boxId))}
			<div class="grid gap-2 rounded-md border bg-background p-2 sm:grid-cols-[minmax(0,1fr)_9rem_auto] sm:items-start">
				<div class="space-y-1">
					<label class="text-xs font-medium" for={`${kind}-box-${index}`}>{m.boxes_title()}</label>
					<NativeSelect
						id={`${kind}-box-${index}`}
						name={`${kind}-box-${index}`}
						value={String(allocation.boxId)}
						onValueChange={(value) => changeBox(index, value)}
						items={itemsFor(index)}
						disabled={disabled || archived}
						aria-label={m.transactions_box_select_aria({ number: index + 1 })}
					/>
					{#if archived}
						<p class="text-xs text-amber-700 dark:text-amber-400">{m.transactions_box_archived_locked()}</p>
					{:else if kind === 'funding' && selectedBox}
						<p class="text-xs text-muted-foreground">
							{m.transactions_box_available({ amount: fmt.format(maxForBox(allocation.boxId)) })}
						</p>
					{/if}
				</div>

				<div class="space-y-1">
					<label class="text-xs font-medium" for={`${kind}-amount-${index}`}>{m.common_amount_mxn()}</label>
					<Input
						id={`${kind}-amount-${index}`}
						type="number"
						step="0.01"
						min="0.01"
						max={kind === 'funding' ? maxForBox(allocation.boxId) : maximumTotal}
						value={allocation.amount || ''}
						oninput={(event) => changeAmount(index, event)}
						disabled={disabled || archived}
						aria-invalid={lineAmountInvalid || linePrecisionInvalid || lineOverBalance || undefined}
						aria-label={m.transactions_box_amount_aria({ number: index + 1 })}
						placeholder="0.00"
					/>
					{#if lineAmountInvalid}
						<p class="text-xs text-destructive">{m.validation_amount_positive()}</p>
					{:else if linePrecisionInvalid}
						<p class="text-xs text-destructive">{m.transactions_box_amount_precision()}</p>
					{:else if lineOverBalance}
						<p class="text-xs text-destructive">{m.transactions_box_insufficient()}</p>
					{/if}
				</div>

				<Button
					type="button"
					variant="ghost"
					size="icon"
					class="mt-5 justify-self-end text-muted-foreground hover:text-destructive"
					onclick={() => removeAllocation(index)}
					disabled={disabled || archived}
					aria-label={m.transactions_box_remove_aria({ number: index + 1 })}
				>
					<Trash2Icon />
				</Button>
			</div>
		{/each}

		<Button type="button" variant="outline" size="sm" onclick={addAllocation} disabled={!canAdd}>
			<CirclePlusIcon data-icon="inline-start" />
			{kind === 'funding' ? m.transactions_box_add_funding() : m.transactions_box_add_distribution()}
		</Button>
	{/if}

	<div class="grid gap-2 rounded-md bg-background p-3 text-sm {kind === 'funding' ? 'sm:grid-cols-3' : 'sm:grid-cols-2'}">
		<div>
			<p class="text-xs text-muted-foreground">{m.transactions_box_allocated()}</p>
			<p class="font-mono font-medium">{fmt.format(total)}</p>
		</div>
		{#if kind === 'funding'}
			<div>
				<p class="text-xs text-muted-foreground">{m.transactions_box_free_remainder()}</p>
				<p class="font-mono font-medium">{fmt.format(unallocated)}</p>
			</div>
		{/if}
		<div>
			<p class="text-xs text-muted-foreground">{m.transactions_box_projected_available()}</p>
			<p class="font-mono font-medium {projectedAvailable < 0 ? 'text-destructive' : ''}">
				{fmt.format(projectedAvailable)}
			</p>
		</div>
	</div>

	{#if isFutureDated && allocations.length > 0}
		<Alert.Root variant="destructive">
			<Alert.Description>{m.transactions_box_future_date()}</Alert.Description>
		</Alert.Root>
	{:else if overTotal}
		<Alert.Root variant="destructive">
			<Alert.Description>
				{kind === 'funding'
					? m.transactions_box_funding_over_total()
					: m.transactions_box_distribution_over_available()}
			</Alert.Description>
		</Alert.Root>
	{:else if hasLineOverBalance}
		<Alert.Root variant="destructive">
			<Alert.Description>{m.transactions_box_insufficient_description()}</Alert.Description>
		</Alert.Root>
	{/if}
</section>
