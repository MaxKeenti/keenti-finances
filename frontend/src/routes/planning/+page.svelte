<script lang="ts">
	/**
	 * The 30-day planning preview (decision D5, slice 5C).
	 *
	 * A stateless what-if: the User types costs they expect and may opt in to
	 * money they might receive, and the server returns a hypothetical result.
	 *
	 * Rules this page keeps:
	 *
	 * - Every figure is the server's. The page never adds up the draft; it
	 *   sends it and renders the response.
	 * - A result is shown only while it still describes the inputs on screen.
	 *   Any edit clears it at once, a response to an older draft is dropped
	 *   (`PlanningPreviewSession`), and hiding the tab or refreshing also
	 *   withdraws every "not yet recorded" confirmation, because the User may
	 *   have recorded one of these costs in the meantime.
	 * - Nothing is written anywhere. The draft lives in this component only and
	 *   is lost on navigation or reload, which the page says before any entry.
	 * - Receipt dates are always typed by the User; nothing assumes when money
	 *   will arrive, and nothing is included unless selected.
	 */
	import { onMount, tick, untrack } from 'svelte';
	import { invalidateAll } from '$app/navigation';
	import { Button } from '$lib/components/ui/button';
	import * as Card from '$lib/components/ui/card';
	import * as Alert from '$lib/components/ui/alert';
	import { Checkbox } from '$lib/components/ui/checkbox';
	import { Input } from '$lib/components/ui/input';
	import { Label } from '$lib/components/ui/label';
	import * as NativeSelect from '$lib/components/ui/native-select';
	import { SectionUnavailable } from '$lib/components/section-status';
	import PlanningResult from '$lib/components/planning/planning-result.svelte';
	import { CircleAlert, Plus, RefreshCw, Trash2 } from '@lucide/svelte';
	import { formatDateOnly, mxnFormatter } from '$lib/formatting';
	import { sectionValue } from '$lib/types/section';
	import {
		MAX_COST_DESCRIPTION,
		MAX_PLANNING_COSTS,
		MAX_PLANNING_RECEIPTS,
		PlanningPreviewSession,
		addCost,
		addReceipt,
		LOCAL_ISSUE_FIELD,
		emptyDraft,
		groupMissingInputs,
		invalidCostFields,
		isReceiptSelected,
		reconcileReceipts,
		removeCost,
		serverCostField,
		removeReceipt,
		updateCost,
		updateReceiptDate,
		validateDraft,
		withdrawForStaleness,
		type CostField,
		type PlanningDraft,
		type PreviewMissingInput,
		type PreviewState,
		type ReceiptDraft,
		type ReceiptKind,
	} from '$lib/planning-preview';
	import {
		clearedMessage,
		failureMessage,
		localIssueMessage,
		missingInputMessage,
		type LabelContext,
	} from '$lib/planning-labels';
	import { m } from '$lib/paraglide/messages.js';
	import type { PageData } from './$types';

	let { data }: { data: PageData } = $props();

	const session = new PlanningPreviewSession();
	let preview = $state<PreviewState>(session.state);
	let draft = $state<PlanningDraft>(emptyDraft());
	/** Local problems are shown once the User has tried to calculate. */
	let attempted = $state(false);
	let refreshing = $state(false);
	let removedNames = $state<string[]>([]);
	let debtChoice = $state('');
	let recordChoice = $state('');
	let resultHeading = $state<HTMLElement | null>(null);

	const locale = $derived(data.preferences.locale);
	const fmt = $derived(mxnFormatter(locale));
	const money = (value: number) => fmt.format(value);
	const date = (value: string) => formatDateOnly(value, locale);

	const planWindow = $derived(data.window);
	const boxes = $derived(sectionValue(data.options.boxes));
	const debts = $derived(sectionValue(data.options.debts));
	const records = $derived(sectionValue(data.options.paymentRecords));
	const boxIds = $derived(boxes === null ? null : new Set(boxes.map((box) => box.id)));
	const validation = $derived(validateDraft(draft, boxIds));

	const result = $derived(preview.kind === 'result' ? preview : null);
	const serverIssues = $derived(result === null ? null : groupMissingInputs(result.response.missingInputs));

	const receiptKey = (kind: ReceiptKind, id: number) => `${kind}:${id}`;
	const currentLabels = $derived.by(() => {
		const labels = new Map<string, { label: string; amount: number }>();
		for (const debt of debts ?? []) {
			labels.set(receiptKey('DEBT', debt.id), {
				amount: debt.remaining,
				label: m.planning_receipt_debt_option({
					description: debt.description || '—',
					contact: debt.contactName ?? m.planning_receipt_no_contact(),
					amount: money(debt.remaining),
				}),
			});
		}
		for (const record of records ?? []) {
			labels.set(receiptKey('PAYMENT_RECORD', record.id), {
				amount: record.amount,
				label: m.planning_receipt_record_option({
					subscription: record.subscriptionName,
					member: record.memberName ?? m.planning_receipt_unnamed_member(),
					date: date(record.billingDate),
					amount: money(record.amount),
				}),
			});
		}
		return labels;
	});
	// Labels seen earlier in this visit, so a receipt that has since left the
	// catalog can still be named when the page explains why it was removed.
	const knownLabels = new Map<string, string>();

	function receiptLabel(receipt: Pick<ReceiptDraft, 'recordKind' | 'recordId'>): string {
		const key = receiptKey(receipt.recordKind, receipt.recordId);
		return (
			currentLabels.get(key)?.label ??
			knownLabels.get(key) ??
			(receipt.recordKind === 'DEBT'
				? m.planning_receipt_unknown_debt({ id: receipt.recordId })
				: m.planning_receipt_unknown_record({ id: receipt.recordId }))
		);
	}

	// Box names seen earlier in this visit, so a Box already chosen keeps its
	// name when a later Box read fails or no longer lists it.
	const knownBoxNames = new Map<number, string>();

	function boxLabel(boxId: number): string {
		return boxes?.find((box) => box.id === boxId)?.name ?? knownBoxNames.get(boxId) ?? m.planning_box_unknown({ id: boxId });
	}

	const labelContext = $derived.by((): LabelContext => {
		const response = result?.response ?? null;
		return {
			window: response?.window ?? planWindow,
			date,
			money,
			boxName: (boxId) =>
				response?.projected?.perBox.find((box) => box.boxId === boxId)?.name ?? boxLabel(boxId),
			accountName: (accountId) => {
				const timing = response?.timing;
				const row = [...(timing?.dated ?? []), ...(timing?.overdue ?? []), ...(timing?.estimates ?? [])].find(
					(entry) => entry.accountId === accountId,
				);
				return row?.accountName ?? m.planning_timing_account_unknown();
			},
		};
	});

	const debtOptions = $derived(
		(debts ?? []).filter((debt) => !isReceiptSelected(draft, 'DEBT', debt.id)),
	);
	const recordOptions = $derived(
		(records ?? []).filter((record) => !isReceiptSelected(draft, 'PAYMENT_RECORD', record.id)),
	);
	const atCostLimit = $derived(draft.costs.length >= MAX_PLANNING_COSTS);
	const atReceiptLimit = $derived(draft.receipts.length >= MAX_PLANNING_RECEIPTS);

	const incompleteRows = $derived<string[]>([
		...draft.costs.flatMap((cost, index) =>
			validation.costs.has(cost.key) ? [m.planning_cost_label({ number: index + 1 })] : [],
		),
		...draft.receipts.flatMap((receipt, index) =>
			validation.receipts.has(receipt.key) ? [m.planning_receipt_label({ number: index + 1 })] : [],
		),
	]);

	/** Every draft change goes through here, so no result outlives its inputs. */
	function edit(next: PlanningDraft) {
		draft = next;
		session.clear('edited');
	}

	function focusById(id: string) {
		void tick().then(() => document.getElementById(id)?.focus());
	}

	function onAddCost() {
		const next = addCost(draft);
		if (next === draft) return;
		edit(next);
		focusById(`cost-${next.costs[next.costs.length - 1].key}-amount`);
	}

	function onRemoveCost(key: number) {
		const index = draft.costs.findIndex((cost) => cost.key === key);
		const next = removeCost(draft, key);
		edit(next);
		const following = next.costs[index] ?? next.costs[index - 1];
		focusById(following ? `cost-${following.key}-amount` : 'planning-add-cost');
	}

	function onAddReceipt(kind: ReceiptKind, value: string) {
		const id = Number(value);
		if (!Number.isInteger(id) || id <= 0) return;
		const next = addReceipt(draft, kind, id);
		if (next === draft) return;
		if (kind === 'DEBT') debtChoice = '';
		else recordChoice = '';
		removedNames = [];
		edit(next);
		focusById(`receipt-${next.receipts[next.receipts.length - 1].key}-date`);
	}

	function onRemoveReceipt(key: number) {
		const index = draft.receipts.findIndex((receipt) => receipt.key === key);
		const next = removeReceipt(draft, key);
		edit(next);
		const following = next.receipts[index] ?? next.receipts[index - 1];
		focusById(following ? `receipt-${following.key}-date` : 'planning-receipts-heading');
	}

	function focusFirstLocalIssue() {
		for (const cost of draft.costs) {
			const issues = validation.costs.get(cost.key);
			if (issues) return focusById(`cost-${cost.key}-${LOCAL_ISSUE_FIELD[issues[0]]}`);
		}
		for (const receipt of draft.receipts) {
			if (validation.receipts.has(receipt.key)) return focusById(`receipt-${receipt.key}-date`);
		}
	}

	/** Server reasons fixed by removing the receipt rather than changing its date. */
	const RECEIPT_REMOVABLE_REASONS = ['RECEIPT_NOT_FOUND', 'RECEIPT_INELIGIBLE', 'DUPLICATE_RECEIPT', 'INVALID_AMOUNT'];

	function goToRow(input: PreviewMissingInput) {
		if (input.itemIndex !== null) {
			const cost = draft.costs[input.itemIndex];
			if (!cost) return;
			const field = serverCostField(input.reason) ?? 'amount';
			// The Box amount input exists only while a Box is chosen.
			focusById(`cost-${cost.key}-${field === 'box-amount' && cost.boxId === '' ? 'box' : field}`);
		} else if (input.receiptIndex !== null) {
			const receipt = draft.receipts[input.receiptIndex];
			if (!receipt) return;
			focusById(`receipt-${receipt.key}-${RECEIPT_REMOVABLE_REASONS.includes(input.reason) ? 'remove' : 'date'}`);
		}
	}

	async function calculate() {
		attempted = true;
		removedNames = [];
		if (planWindow === null) return;
		if (!validation.ready) {
			focusFirstLocalIssue();
			return;
		}
		const applied = await session.calculate(draft, fetch);
		if (applied && session.state.kind === 'result') {
			await tick();
			resultHeading?.focus();
		}
	}

	/** Reloads the option lists and the window (the layout's captured day). */
	async function reloadOptions() {
		refreshing = true;
		try {
			await invalidateAll();
		} finally {
			refreshing = false;
		}
	}

	/** Refresh: withdraw the result and confirmations, then reload the options. */
	function refresh() {
		draft = withdrawForStaleness(session, draft, 'refreshed');
		removedNames = [];
		void reloadOptions();
	}

	$effect(() => session.subscribe((state) => (preview = state)));

	// A reloaded catalog may no longer offer a selected receipt — paid,
	// settled, deleted — so it leaves the draft and the page names it.
	$effect(() => {
		const available = {
			debts: debts === null ? null : new Set(debts.map((debt) => debt.id)),
			paymentRecords: records === null ? null : new Set(records.map((record) => record.id)),
		};
		const labels = currentLabels;
		// Only a new catalog triggers this; editing the draft does not.
		untrack(() => {
			const { draft: next, removed } = reconcileReceipts(draft, available);
			if (removed.length > 0) {
				removedNames = removed.map((receipt) => receiptLabel(receipt));
				edit(next);
			}
		});
		for (const [key, value] of labels) knownLabels.set(key, value.label);
	});

	$effect(() => {
		for (const box of boxes ?? []) knownBoxNames.set(box.id, box.name);
	});

	onMount(() => {
		let away = false;
		function leave() {
			if (away) return;
			away = true;
			// Nothing on screen may survive the absence: the ledger can change
			// while this tab is hidden, and no matching would notice.
			draft = withdrawForStaleness(session, draft, 'returned');
		}
		function back() {
			if (!away) return;
			away = false;
			void reloadOptions();
		}
		const onVisibility = () => (document.visibilityState === 'hidden' ? leave() : back());
		const onPageShow = (event: PageTransitionEvent) => {
			if (!event.persisted) return;
			leave();
			back();
		};
		document.addEventListener('visibilitychange', onVisibility);
		window.addEventListener('pageshow', onPageShow);
		return () => {
			document.removeEventListener('visibilitychange', onVisibility);
			window.removeEventListener('pageshow', onPageShow);
			// Leaving the page abandons any request still in flight.
			session.clear('edited');
		};
	});

	/** A row's messages, and which of its inputs they are actually about. */
	function costIssues(cost: PlanningDraft['costs'][number], index: number) {
		const local = attempted ? (validation.costs.get(cost.key) ?? []) : [];
		const server = serverIssues?.byCost.get(index) ?? [];
		return {
			messages: [
				...local.map((issue) => localIssueMessage(issue, labelContext)),
				...server.map((input) => missingInputMessage(input, labelContext)),
			],
			fields: invalidCostFields(cost, local, server),
		};
	}

	function receiptIssues(key: number, index: number) {
		const local = attempted ? (validation.receipts.get(key) ?? []) : [];
		const server = serverIssues?.byReceipt.get(index) ?? [];
		return {
			messages: [
				...local.map((issue) => localIssueMessage(issue, labelContext)),
				...server.map((input) => missingInputMessage(input, labelContext)),
			],
			// Only a date problem marks the date; a receipt that must be removed
			// is explained by the row's message, not by an invalid date.
			dateInvalid: local.length > 0 || server.some((input) => !RECEIPT_REMOVABLE_REASONS.includes(input.reason)),
		};
	}

	const invalidAttr = (fields: Set<CostField>, field: CostField) => fields.has(field) || undefined;
</script>

<svelte:head><title>{m.nav_planning()} · Keenti</title></svelte:head>

{#snippet issues(id: string, messages: string[])}
	{#if messages.length > 0}
		<ul id={id} class="space-y-1 text-xs text-destructive">
			{#each messages as message}
				<li class="flex gap-1.5">
					<CircleAlert class="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
					<span class="min-w-0 break-words">{message}</span>
				</li>
			{/each}
		</ul>
	{/if}
{/snippet}

<div class="mx-auto max-w-3xl space-y-8">
	<header class="space-y-2">
		<h1 class="font-heading text-2xl font-semibold">{m.planning_title()}</h1>
		<p class="text-sm text-muted-foreground">{m.planning_subtitle()}</p>
		{#if planWindow !== null}
			<p class="text-sm">
				{m.planning_window({
					from: date(planWindow.from),
					to: date(planWindow.to),
					zone: data.preferences.timeZone,
				})}
			</p>
		{/if}
	</header>

	<!-- Before any entry: what happens to the draft, and what is never matched. -->
	<Alert.Root>
		<CircleAlert aria-hidden="true" />
		<Alert.Title>{m.planning_draft_disclosure_title()}</Alert.Title>
		<Alert.Description>
			<p>{m.planning_draft_disclosure()}</p>
			<p>{m.planning_matching_disclosure()}</p>
		</Alert.Description>
	</Alert.Root>

	{#if planWindow === null}
		<div class="space-y-2">
			<SectionUnavailable
				title={m.planning_window_unavailable_title()}
				description={m.planning_window_unavailable_description()}
			/>
			<Button href="/settings" variant="outline" size="sm">{m.planning_open_settings()}</Button>
		</div>
	{:else}
		<!-- Costs -->
		<section class="space-y-4" aria-labelledby="planning-costs-heading">
			<div class="space-y-1">
				<div class="flex flex-wrap items-baseline justify-between gap-2">
					<h2 id="planning-costs-heading" class="font-heading text-lg font-semibold">
						{m.planning_costs_title()}
					</h2>
					<span class="text-xs text-muted-foreground tabular-nums">
						{m.planning_costs_count({ count: draft.costs.length, max: MAX_PLANNING_COSTS })}
					</span>
				</div>
				<p class="text-xs text-muted-foreground">{m.planning_costs_description()}</p>
			</div>

			{#if boxes === null}
				<SectionUnavailable compact title={m.planning_cost_boxes_unavailable()} />
			{/if}

			{#if draft.costs.length === 0}
				<p class="text-sm text-muted-foreground">{m.planning_costs_empty()}</p>
			{/if}

			<ol class="space-y-3">
				{#each draft.costs as cost, index (cost.key)}
					{@const { messages, fields } = costIssues(cost, index)}
					{@const describedBy = messages.length > 0 ? `cost-${cost.key}-issues` : undefined}
					<li>
						<Card.Root class="gap-3 py-4">
							<Card.Content class="px-4">
								<!-- The legend must be the fieldset's first child to name it, so
								     the remove button is positioned beside it rather than wrapped with it. -->
								<fieldset class="relative space-y-3" aria-describedby={describedBy}>
									<legend class="pr-10 text-sm leading-8 font-semibold">{m.planning_cost_label({ number: index + 1 })}</legend>
									<Button
										id="cost-{cost.key}-remove"
										type="button"
										variant="ghost"
										size="sm"
										class="absolute top-0 right-0 !mt-0 h-8 px-2"
										aria-label={m.planning_remove_cost({ number: index + 1 })}
										onclick={() => onRemoveCost(cost.key)}
									>
										<Trash2 class="size-4" aria-hidden="true" />
									</Button>

									<div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
										<div class="space-y-1.5">
											<Label for="cost-{cost.key}-amount">{m.planning_cost_amount()}</Label>
											<Input
												id="cost-{cost.key}-amount"
												type="text"
												inputmode="decimal"
												autocomplete="off"
												value={cost.amount}
												aria-invalid={invalidAttr(fields, 'amount')}
												aria-describedby={describedBy}
												oninput={(event) => edit(updateCost(draft, cost.key, { amount: event.currentTarget.value }))}
											/>
										</div>
										<div class="space-y-1.5">
											<Label for="cost-{cost.key}-date">{m.planning_cost_date()}</Label>
											<Input
												id="cost-{cost.key}-date"
												type="date"
												min={planWindow.from}
												max={planWindow.to}
												value={cost.date}
												aria-invalid={invalidAttr(fields, 'date')}
												aria-describedby={describedBy}
												oninput={(event) => edit(updateCost(draft, cost.key, { date: event.currentTarget.value }))}
											/>
										</div>
									</div>

									<div class="space-y-1.5">
										<Label for="cost-{cost.key}-description">{m.planning_cost_description()}</Label>
										<Input
											id="cost-{cost.key}-description"
											type="text"
											maxlength={MAX_COST_DESCRIPTION}
											autocomplete="off"
											value={cost.description}
											aria-invalid={invalidAttr(fields, 'description')}
											aria-describedby={describedBy}
											oninput={(event) => edit(updateCost(draft, cost.key, { description: event.currentTarget.value }))}
										/>
									</div>

									<div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
										<div class="min-w-0 space-y-1.5">
											<Label for="cost-{cost.key}-box">{m.planning_cost_box()}</Label>
											<NativeSelect.Root
												id="cost-{cost.key}-box"
												class="w-full"
												value={cost.boxId}
												disabled={boxes === null && cost.boxId === ''}
												aria-invalid={invalidAttr(fields, 'box')}
												aria-describedby={describedBy}
												onchange={(event: Event) =>
													edit(updateCost(draft, cost.key, { boxId: (event.currentTarget as HTMLSelectElement).value }))}
											>
												<NativeSelect.Option value="">{m.planning_cost_no_box()}</NativeSelect.Option>
												{#each boxes ?? [] as box (box.id)}
													<NativeSelect.Option value={String(box.id)}>
														{m.planning_cost_box_option({ name: box.name, balance: money(box.balance) })}
													</NativeSelect.Option>
												{/each}
												{#if cost.boxId !== '' && !boxIds?.has(Number(cost.boxId))}
													<NativeSelect.Option value={cost.boxId}>
														{boxLabel(Number(cost.boxId))}
													</NativeSelect.Option>
												{/if}
											</NativeSelect.Root>
										</div>
										{#if cost.boxId !== ''}
											<div class="space-y-1.5">
												<Label for="cost-{cost.key}-box-amount">{m.planning_cost_box_amount()}</Label>
												<Input
													id="cost-{cost.key}-box-amount"
													type="text"
													inputmode="decimal"
													autocomplete="off"
													value={cost.boxAmount}
													aria-invalid={invalidAttr(fields, 'box-amount')}
													aria-describedby={describedBy}
													oninput={(event) =>
														edit(updateCost(draft, cost.key, { boxAmount: event.currentTarget.value }))}
												/>
											</div>
										{/if}
									</div>

									<div class="flex items-start gap-2">
										<Checkbox
											id="cost-{cost.key}-confirm"
											checked={cost.notYetRecordedConfirmed}
											aria-invalid={invalidAttr(fields, 'confirm')}
											aria-describedby={describedBy}
											onCheckedChange={(checked) =>
												edit(updateCost(draft, cost.key, { notYetRecordedConfirmed: checked === true }))}
											class="mt-0.5"
										/>
										<Label for="cost-{cost.key}-confirm" class="text-sm font-normal leading-snug">
											{m.planning_cost_confirm_unrecorded()}
										</Label>
									</div>

									{@render issues(`cost-${cost.key}-issues`, messages)}
								</fieldset>
							</Card.Content>
						</Card.Root>
					</li>
				{/each}
			</ol>

			<div class="flex flex-wrap items-center gap-3">
				<Button id="planning-add-cost" type="button" variant="outline" disabled={atCostLimit} onclick={onAddCost}>
					<Plus class="size-4" aria-hidden="true" />
					{m.planning_add_cost()}
				</Button>
				{#if atCostLimit}
					<span class="text-xs text-muted-foreground">{m.planning_costs_limit({ max: MAX_PLANNING_COSTS })}</span>
				{/if}
			</div>
		</section>

		<!-- Expected receipts: opt-in only -->
		<section class="space-y-4" aria-labelledby="planning-receipts-heading">
			<div class="space-y-1">
				<div class="flex flex-wrap items-baseline justify-between gap-2">
					<h2 id="planning-receipts-heading" tabindex="-1" class="font-heading text-lg font-semibold outline-none">
						{m.planning_receipts_title()}
					</h2>
					<span class="text-xs text-muted-foreground tabular-nums">
						{m.planning_receipts_count({ count: draft.receipts.length, max: MAX_PLANNING_RECEIPTS })}
					</span>
				</div>
				<p class="text-xs text-muted-foreground">{m.planning_receipts_description()}</p>
			</div>

			{#if removedNames.length > 0}
				<p class="rounded-md border border-dashed px-3 py-2 text-sm" role="status">
					{m.planning_receipts_removed({ names: removedNames.join('; ') })}
				</p>
			{/if}

			<div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
				{#each [
					{
						kind: 'DEBT' as const,
						id: 'planning-debt-choice',
						label: m.planning_receipts_debts_label(),
						add: m.planning_receipts_add_debt(),
						catalog: debts,
						none: m.planning_receipts_none_debts(),
						unavailable: m.planning_receipts_debts_unavailable(),
						options: debtOptions.map((debt) => ({ value: String(debt.id), label: receiptLabel({ recordKind: 'DEBT', recordId: debt.id }) })),
					},
					{
						kind: 'PAYMENT_RECORD' as const,
						id: 'planning-record-choice',
						label: m.planning_receipts_records_label(),
						add: m.planning_receipts_add_record(),
						catalog: records,
						none: m.planning_receipts_none_records(),
						unavailable: m.planning_receipts_records_unavailable(),
						options: recordOptions.map((record) => ({ value: String(record.id), label: receiptLabel({ recordKind: 'PAYMENT_RECORD', recordId: record.id }) })),
					},
				] as picker (picker.kind)}
					<div class="min-w-0 space-y-1.5">
						{#if picker.catalog !== null && picker.options.length > 0}
							<Label for={picker.id}>{picker.label}</Label>
						{:else}
							<p class="text-sm font-medium">{picker.label}</p>
						{/if}
						{#if picker.catalog === null}
							<SectionUnavailable
								compact
								title={picker.unavailable}
								description={m.planning_receipts_catalog_unavailable_description()}
							/>
						{:else if picker.catalog.length === 0}
							<p class="text-xs text-muted-foreground">{picker.none}</p>
						{:else if picker.options.length === 0}
							<p class="text-xs text-muted-foreground">{m.planning_receipts_all_selected()}</p>
						{:else}
							<div class="flex gap-2">
								<NativeSelect.Root
									id={picker.id}
									class="min-w-0 flex-1"
									value={picker.kind === 'DEBT' ? debtChoice : recordChoice}
									disabled={atReceiptLimit}
									onchange={(event: Event) => {
										const value = (event.currentTarget as HTMLSelectElement).value;
										if (picker.kind === 'DEBT') debtChoice = value;
										else recordChoice = value;
									}}
								>
									<NativeSelect.Option value="">{m.planning_receipts_choose()}</NativeSelect.Option>
									{#each picker.options as option (option.value)}
										<NativeSelect.Option value={option.value}>{option.label}</NativeSelect.Option>
									{/each}
								</NativeSelect.Root>
								<Button
									type="button"
									variant="outline"
									size="sm"
									class="h-8 shrink-0"
									disabled={atReceiptLimit || (picker.kind === 'DEBT' ? debtChoice : recordChoice) === ''}
									onclick={() => onAddReceipt(picker.kind, picker.kind === 'DEBT' ? debtChoice : recordChoice)}
								>
									{picker.add}
								</Button>
							</div>
						{/if}
					</div>
				{/each}
			</div>
			{#if atReceiptLimit}
				<p class="text-xs text-muted-foreground">{m.planning_receipts_limit({ max: MAX_PLANNING_RECEIPTS })}</p>
			{/if}

			{#if draft.receipts.length > 0}
				<ol class="space-y-3">
					{#each draft.receipts as receipt, index (receipt.key)}
						{@const { messages, dateInvalid } = receiptIssues(receipt.key, index)}
						{@const known = currentLabels.get(receiptKey(receipt.recordKind, receipt.recordId))}
						<li class="space-y-2 rounded-md border px-4 py-3">
							<div class="flex items-start justify-between gap-2">
								<div class="min-w-0 space-y-0.5">
									<p class="text-sm font-semibold">{m.planning_receipt_label({ number: index + 1 })}</p>
									<p class="break-words text-sm">{receiptLabel(receipt)}</p>
									{#if known}
										<p class="text-xs text-muted-foreground">
											{m.planning_receipt_recorded_amount({ amount: money(known.amount) })}
										</p>
									{/if}
								</div>
								<Button
									id="receipt-{receipt.key}-remove"
									type="button"
									variant="ghost"
									size="sm"
									class="h-8 shrink-0 px-2"
									aria-label={m.planning_remove_receipt({ number: index + 1 })}
									onclick={() => onRemoveReceipt(receipt.key)}
								>
									<Trash2 class="size-4" aria-hidden="true" />
								</Button>
							</div>
							<div class="space-y-1.5 sm:max-w-60">
								<Label for="receipt-{receipt.key}-date">{m.planning_receipt_date()}</Label>
								<Input
									id="receipt-{receipt.key}-date"
									type="date"
									min={planWindow.from}
									max={planWindow.to}
									value={receipt.date}
									aria-invalid={dateInvalid || undefined}
									aria-describedby={messages.length > 0 ? `receipt-${receipt.key}-issues` : undefined}
									oninput={(event) => edit(updateReceiptDate(draft, receipt.key, event.currentTarget.value))}
								/>
							</div>
							{@render issues(`receipt-${receipt.key}-issues`, messages)}
						</li>
					{/each}
				</ol>
			{/if}
		</section>

		<!-- Review and calculate -->
		<section class="space-y-4" aria-labelledby="planning-review-heading">
			<h2 id="planning-review-heading" class="font-heading text-lg font-semibold">{m.planning_review_title()}</h2>
			<div class="flex items-start gap-2">
				<Checkbox
					id="planning-essentials"
					checked={draft.essentialsReviewed}
					onCheckedChange={(checked) => edit({ ...draft, essentialsReviewed: checked === true })}
					class="mt-0.5"
				/>
				<Label for="planning-essentials" class="text-sm font-normal leading-snug">
					{m.planning_essentials_reviewed()}
				</Label>
			</div>
			<p class="text-xs text-muted-foreground">{m.planning_essentials_note({ count: draft.costs.length })}</p>

			<div class="flex flex-wrap items-center gap-2">
				<Button type="button" disabled={preview.kind === 'loading'} onclick={calculate}>
					{preview.kind === 'loading' ? m.planning_calculating() : m.planning_calculate()}
				</Button>
				<Button
					type="button"
					variant="outline"
					disabled={refreshing}
					aria-describedby="planning-refresh-description"
					onclick={refresh}
				>
					<RefreshCw class="size-4 {refreshing ? 'animate-spin' : ''}" aria-hidden="true" />
					{m.planning_refresh()}
				</Button>
			</div>
			<p id="planning-refresh-description" class="text-xs text-muted-foreground">
				{m.planning_refresh_description()}
			</p>

			<!-- The draft's local state: outside the live region, because it
			     changes on every keystroke. -->
			{#if incompleteRows.length > 0}
				<p class="text-sm text-muted-foreground">
					{m.planning_draft_incomplete({ rows: incompleteRows.join(', ') })}
				</p>
			{/if}

			<!-- One polite live region for what the calculation reports. A
			     failure is announced by this region alone: a nested alert role
			     would be announced twice, or assertively. -->
			<div aria-live="polite" class="space-y-2 text-sm">
				{#if preview.kind === 'loading'}
					<p>{m.planning_calculating()}</p>
				{:else if preview.kind === 'failed'}
					<p class="rounded-md border border-dashed px-3 py-2">{failureMessage(preview.failure)}</p>
				{:else if preview.kind === 'idle' && preview.cleared !== null}
					<p class="rounded-md border border-dashed px-3 py-2">{clearedMessage(preview.cleared)}</p>
				{/if}
			</div>
		</section>

		{#if result !== null}
			<PlanningResult
				response={result.response}
				submitted={result.submitted}
				context={labelContext}
				{locale}
				fallbackTimeZone={data.preferences.timeZone}
				receiptLabel={(index) => {
					const receipt = result.submitted.receipts[index];
					return receipt ? receiptLabel(receipt) : m.planning_receipt_label({ number: index + 1 });
				}}
				onGoToRow={goToRow}
				bind:heading={resultHeading}
			/>
		{/if}
	{/if}
</div>
