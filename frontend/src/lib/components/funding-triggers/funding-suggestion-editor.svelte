<script lang="ts">
	import { untrack } from 'svelte';
	import SparklesIcon from '@lucide/svelte/icons/sparkles';
	import * as Alert from '$lib/components/ui/alert';
	import { Button } from '$lib/components/ui/button';
	import { Checkbox } from '$lib/components/ui/checkbox';
	import { Input } from '$lib/components/ui/input';
	import { mxnFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
	import {
		fundingExcess,
		mergeFundingSuggestionAllocations,
		selectedFundingTotal,
		type EditableFundingSuggestion,
		type FundingSuggestionSetDto,
	} from '$lib/types/funding-triggers';
	import {
		allocationTotal,
		hasAtMostTwoDecimalPlaces,
		type BoxAllocationInput,
	} from '$lib/types/transactions';

	let {
		categoryId,
		ingressAmount,
		availableBefore,
		allocations,
		onChange,
		locale,
		active = true,
	}: {
		categoryId: number;
		ingressAmount: number;
		availableBefore: number;
		allocations: BoxAllocationInput[];
		onChange: (allocations: BoxAllocationInput[]) => void;
		locale: string;
		active?: boolean;
	} = $props();

	let suggestions = $state<EditableFundingSuggestion[]>([]);
	let combinedTotal = $state(0);
	let loading = $state(false);
	let loadError = $state(false);
	let loadedCategoryId = $state<number | null>(null);
	let loadedRequestKey = $state<string | null>(null);
	let managedBoxIds = $state<Set<number>>(new Set());
	let dismissedKey = $state<string | null>(null);
	let retryNonce = $state(0);

	const fmt = $derived(mxnFormatter(locale));
	const availableAfterIngress = $derived(
		Math.max(0, Number(availableBefore) + Math.max(0, Number(ingressAmount) || 0)),
	);
	const selectedTotal = $derived(selectedFundingTotal(suggestions));
	const excess = $derived(fundingExcess(allocationTotal(allocations), availableAfterIngress));
	const allSelected = $derived(
		suggestions.length > 0 && suggestions.every((suggestion) => suggestion.selected),
	);
	const hasInvalidSelection = $derived(
		suggestions.some(
			(suggestion) =>
				suggestion.selected &&
				(suggestion.amount <= 0 || !hasAtMostTwoDecimalPlaces(suggestion.amount)),
		),
	);

	function syncAllocations(next: EditableFundingSuggestion[]) {
		onChange(mergeFundingSuggestionAllocations(allocations, managedBoxIds, next));
		managedBoxIds = new Set(
			next.filter((suggestion) => suggestion.selected).map((suggestion) => suggestion.boxId),
		);
		suggestions = next;
	}

	function resetSuggestions() {
		if (managedBoxIds.size > 0) {
			onChange(mergeFundingSuggestionAllocations(allocations, managedBoxIds, []));
		}
		managedBoxIds = new Set();
		suggestions = [];
		combinedTotal = 0;
		loadedCategoryId = null;
		loadedRequestKey = null;
	}

	function toggleSuggestion(triggerId: number) {
		syncAllocations(
			suggestions.map((suggestion) =>
				suggestion.triggerId === triggerId
					? { ...suggestion, selected: !suggestion.selected }
					: suggestion,
			),
		);
	}

	function changeAmount(triggerId: number, event: Event) {
		const input = event.currentTarget as HTMLInputElement;
		const amount = Number.isFinite(input.valueAsNumber) ? input.valueAsNumber : 0;
		syncAllocations(
			suggestions.map((suggestion) =>
				suggestion.triggerId === triggerId
					? { ...suggestion, amount, edited: true }
					: suggestion,
			),
		);
	}

	function selectAll() {
		syncAllocations(suggestions.map((suggestion) => ({ ...suggestion, selected: true })));
	}

	function dismiss() {
		dismissedKey = `${categoryId}:${ingressAmount}`;
		resetSuggestions();
		loadError = false;
		loading = false;
	}

	$effect(() => {
		const requestedCategoryId = Number(categoryId);
		const requestedAmount = Number(ingressAmount);
		const requestKey = `${requestedCategoryId}:${requestedAmount}`;
		retryNonce;

		if (!active) {
			dismissedKey = null;
			untrack(resetSuggestions);
			loading = false;
			loadError = false;
			return;
		}
		if (
			!Number.isInteger(requestedCategoryId) ||
			requestedCategoryId <= 0 ||
			!Number.isFinite(requestedAmount) ||
			requestedAmount <= 0 ||
			dismissedKey === requestKey
		) {
			untrack(resetSuggestions);
			loading = false;
			loadError = false;
			return;
		}
		const previousRequestKey = untrack(() => loadedRequestKey);
		if (previousRequestKey !== null && previousRequestKey !== requestKey) {
			untrack(resetSuggestions);
		}

		loading = true;
		loadError = false;
		const controller = new AbortController();
		const timer = window.setTimeout(async () => {
			try {
				const query = new URLSearchParams({
					categoryId: String(requestedCategoryId),
					ingressAmount: String(requestedAmount),
				});
				const response = await fetch(`/api/transactions/funding-suggestions?${query}`, {
					cache: 'no-store',
					signal: controller.signal,
				});
				if (!response.ok) throw new Error(`Funding suggestions returned ${response.status}`);
				const result = (await response.json()) as FundingSuggestionSetDto;
				const canPreserve = loadedCategoryId === requestedCategoryId;
				const prior = new Map(suggestions.map((suggestion) => [suggestion.triggerId, suggestion]));
				const next = (Array.isArray(result.suggestions) ? result.suggestions : []).map(
					(suggestion): EditableFundingSuggestion => {
						const previous = canPreserve ? prior.get(suggestion.triggerId) : undefined;
						return {
							...suggestion,
							selected: previous?.selected ?? false,
							amount: previous?.edited ? previous.amount : suggestion.suggestedAmount,
							edited: previous?.edited ?? false,
						};
					},
				);
				syncAllocations(next);
				combinedTotal = Number(result.combinedTotal) || 0;
				loadedCategoryId = requestedCategoryId;
				loadedRequestKey = requestKey;
				loading = false;
			} catch (error) {
				if (controller.signal.aborted) return;
				console.error(
					'[funding-suggestions] load failed',
					error instanceof Error ? error.message : error,
				);
				untrack(resetSuggestions);
				loading = false;
				loadError = true;
			}
		}, 300);

		return () => {
			window.clearTimeout(timer);
			controller.abort();
		};
	});

	$effect(() => {
		const currentByBox = new Map(allocations.map((allocation) => [allocation.boxId, allocation]));
		if (managedBoxIds.size === 0) return;

		let changed = false;
		const stillManaged = new Set<number>();
		const next = suggestions.map((suggestion) => {
			if (!managedBoxIds.has(suggestion.boxId)) return suggestion;
			const allocation = currentByBox.get(suggestion.boxId);
			if (!allocation) {
				changed = changed || suggestion.selected;
				return { ...suggestion, selected: false };
			}
			stillManaged.add(suggestion.boxId);
			if (allocation.amount !== suggestion.amount) {
				changed = true;
				return { ...suggestion, amount: allocation.amount, edited: true };
			}
			return suggestion;
		});
		if (stillManaged.size !== managedBoxIds.size) changed = true;
		if (changed) {
			suggestions = next;
			managedBoxIds = stillManaged;
		}
	});
</script>

{#if active && (loading || loadError || suggestions.length > 0)}
	<section
		class="space-y-3 rounded-lg border border-primary/20 bg-primary/5 p-3"
		aria-labelledby="funding-suggestions-title"
		aria-busy={loading}
	>
		<div class="flex flex-wrap items-start justify-between gap-2">
			<div class="space-y-1">
				<h3 id="funding-suggestions-title" class="flex items-center gap-2 text-sm font-medium">
					<SparklesIcon class="size-4 text-primary" aria-hidden="true" />
					{m.funding_trigger_suggestions_title()}
				</h3>
				<p class="text-xs text-muted-foreground">{m.funding_trigger_suggestions_description()}</p>
			</div>
			{#if suggestions.length > 0}
				<div class="flex gap-2">
					<Button type="button" variant="outline" size="sm" onclick={selectAll} disabled={allSelected}>
						{m.funding_trigger_select_all()}
					</Button>
					<Button type="button" variant="ghost" size="sm" onclick={dismiss}>
						{m.funding_trigger_dismiss()}
					</Button>
				</div>
			{/if}
		</div>

		{#if loading && suggestions.length === 0}
			<p class="text-sm text-muted-foreground" role="status">{m.funding_trigger_loading_suggestions()}</p>
		{:else if loadError}
			<Alert.Root variant="destructive">
				<Alert.Description class="flex flex-wrap items-center justify-between gap-2">
					<span>{m.funding_trigger_suggestions_error()}</span>
					<Button type="button" size="sm" variant="outline" onclick={() => (retryNonce += 1)}>
						{m.funding_trigger_retry()}
					</Button>
				</Alert.Description>
			</Alert.Root>
		{:else}
			<div class="space-y-2">
				{#each suggestions as suggestion (suggestion.triggerId)}
					<div class="grid gap-2 rounded-md border bg-background p-3 sm:grid-cols-[auto_minmax(0,1fr)_9rem] sm:items-center">
						<Checkbox
							checked={suggestion.selected}
							onclick={() => toggleSuggestion(suggestion.triggerId)}
							aria-label={m.funding_trigger_select_suggestion({ box: suggestion.boxName })}
						/>
						<div class="min-w-0">
							<p class="truncate text-sm font-medium">{suggestion.boxName}</p>
							<p class="text-xs text-muted-foreground">
								{m.funding_trigger_suggested_amount({ amount: fmt.format(suggestion.suggestedAmount) })}
							</p>
						</div>
						<div class="space-y-1">
							<label class="sr-only" for={`funding-suggestion-${suggestion.triggerId}`}>
								{m.funding_trigger_amount_for_box({ box: suggestion.boxName })}
							</label>
							<Input
								id={`funding-suggestion-${suggestion.triggerId}`}
								type="number"
								min="0.01"
								step="0.01"
								value={suggestion.amount || ''}
								oninput={(event) => changeAmount(suggestion.triggerId, event)}
								aria-invalid={suggestion.selected && (suggestion.amount <= 0 || !hasAtMostTwoDecimalPlaces(suggestion.amount)) || undefined}
							/>
						</div>
					</div>
				{/each}
			</div>

			<div class="grid gap-2 rounded-md bg-background p-3 text-sm sm:grid-cols-3" aria-live="polite">
				<div>
					<p class="text-xs text-muted-foreground">{m.funding_trigger_suggested_total()}</p>
					<p class="font-mono font-medium">{fmt.format(combinedTotal)}</p>
				</div>
				<div>
					<p class="text-xs text-muted-foreground">{m.funding_trigger_selected_total()}</p>
					<p class="font-mono font-medium">{fmt.format(selectedTotal)}</p>
				</div>
				<div>
					<p class="text-xs text-muted-foreground">{m.funding_trigger_available_after_income()}</p>
					<p class="font-mono font-medium">{fmt.format(availableAfterIngress)}</p>
				</div>
			</div>

			{#if excess > 0}
				<Alert.Root variant="destructive">
					<Alert.Description>
						{m.funding_trigger_excess({ amount: fmt.format(excess) })}
					</Alert.Description>
				</Alert.Root>
			{:else if hasInvalidSelection}
				<Alert.Root variant="destructive">
					<Alert.Description>{m.funding_trigger_invalid_selected_amount()}</Alert.Description>
				</Alert.Root>
			{/if}
		{/if}
	</section>
{/if}
