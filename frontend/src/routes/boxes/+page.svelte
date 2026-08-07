<script lang="ts">
	import { enhance as kitEnhance } from '$app/forms';
	import { invalidateAll } from '$app/navigation';
	import { tick, untrack } from 'svelte';
	import { superForm } from 'sveltekit-superforms';
	import { zod4Client } from 'sveltekit-superforms/adapters';
	import { z } from 'zod';
	import { toast } from 'svelte-sonner';
	import {
		AlertTriangle,
		Archive,
		ArrowDown,
		ArrowUp,
		PackageOpen,
		Pencil,
		Plus,
		RotateCcw,
	} from '@lucide/svelte';
	import { submitWithAdaptiveConfirm } from '$lib/components/adaptive-confirm';
	import { BoxCard } from '$lib/components/boxes';
	import * as Alert from '$lib/components/ui/alert';
	import * as Card from '$lib/components/ui/card';
	import * as Dialog from '$lib/components/ui/dialog';
	import * as Empty from '$lib/components/ui/empty';
	import * as Form from '$lib/components/ui/form';
	import { Badge } from '$lib/components/ui/badge';
	import { Button } from '$lib/components/ui/button';
	import { ColorPicker } from '$lib/components/color-picker';
	import { Input } from '$lib/components/ui/input';
	import { Textarea } from '$lib/components/ui/textarea';
	import { mxnFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
	import type { BoxDto } from '$lib/types/boxes';
	import type { PageData } from './$types';

	const boxSchema = z.object({
		id: z.coerce.number().int().positive().optional(),
		name: z.string().trim().min(1, m.validation_name_required()).max(100, m.validation_box_name_too_long()),
		hue: z.coerce.number().int().min(0).max(359),
		icon: z.string().trim().max(16, m.validation_box_icon_too_long()).optional(),
		description: z.string().trim().max(500, m.validation_box_description_too_long()).optional(),
	});

	let { data }: { data: PageData } = $props();

	let dialogOpen = $state(false);
	let editMode = $state(false);
	let showArchived = $state(false);
	let archiveTargetId = $state<number | null>(null);
	let archiveForm = $state<HTMLFormElement | null>(null);
	let reorderForm = $state<HTMLFormElement | null>(null);
	let reorderedIds = $state('');

	const fmt = $derived(mxnFormatter(data.preferences.locale));
	const isUnreconciled = $derived(data.balanceSummary.availableToSpend < 0);

	const sf = superForm(untrack(() => data.form), {
		validators: zod4Client(boxSchema),
		onResult({ result }) {
			if (result.type === 'success') {
				dialogOpen = false;
				toast.success(editMode ? m.boxes_updated() : m.boxes_created());
				void invalidateAll();
			} else if (result.type === 'failure') {
				const resultForm = (result.data as { form?: { message?: string } } | undefined)?.form;
				toast.error(resultForm?.message ?? m.error_box_save());
			}
		},
	});
	const { form, enhance, submitting, message } = sf;

	function openCreate() {
		editMode = false;
		sf.reset({ data: { name: '', hue: 220, icon: '', description: '' } });
		dialogOpen = true;
	}

	function openEdit(box: BoxDto) {
		editMode = true;
		form.set({
			id: box.id,
			name: box.name,
			hue: box.hue,
			icon: box.icon ?? '',
			description: box.description ?? '',
		});
		dialogOpen = true;
	}

	function isZero(value: number) {
		return Math.abs(value) < 0.005;
	}

	async function reorder(index: number, direction: -1 | 1) {
		const nextIndex = index + direction;
		if (nextIndex < 0 || nextIndex >= data.boxes.length) return;
		const next = data.boxes.map((box) => box.id);
		[next[index], next[nextIndex]] = [next[nextIndex], next[index]];
		reorderedIds = next.join(',');
		await tick();
		reorderForm?.requestSubmit();
	}

	async function openArchive(box: BoxDto) {
		if (!isZero(box.balance)) return;
		archiveTargetId = box.id;
		await submitWithAdaptiveConfirm(archiveForm, {
			title: m.boxes_archive_title(),
			description: m.boxes_archive_confirmation({ name: box.name }),
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

<div class="space-y-6">
	<div class="flex flex-wrap items-start justify-between gap-4">
		<div>
			<h1 class="text-2xl font-semibold tracking-tight">{m.boxes_title()}</h1>
			<p class="text-sm text-muted-foreground">{m.boxes_description()}</p>
		</div>
		<Button onclick={openCreate}>
			<Plus data-icon="inline-start" />
			{m.boxes_new()}
		</Button>
	</div>

	{#if data.loadFailed}
		<Alert.Root variant="destructive">
			<AlertTriangle aria-hidden="true" />
			<Alert.Title>{m.boxes_load_failed_title()}</Alert.Title>
			<Alert.Description>{m.boxes_load_failed_description()}</Alert.Description>
		</Alert.Root>
	{/if}

	{#if isUnreconciled}
		<Alert.Root variant="destructive">
			<AlertTriangle aria-hidden="true" />
			<Alert.Title>{m.balance_reconciliation_required()}</Alert.Title>
			<Alert.Description>
				{m.balance_reconciliation_boxes_description({
					amount: fmt.format(Math.abs(data.balanceSummary.availableToSpend)),
				})}
			</Alert.Description>
			<Alert.Action>
				<Button href="/transactions" size="sm" variant="outline">{m.balance_review_transactions()}</Button>
			</Alert.Action>
		</Alert.Root>
	{/if}

	<section aria-label={m.balance_summary()} class="grid gap-3 sm:grid-cols-3">
		<Card.Root size="sm">
			<Card.Header>
				<Card.Description>{m.dashboard_net_balance()}</Card.Description>
				<Card.Title class="text-xl tabular-nums">{fmt.format(data.balanceSummary.netBalance)}</Card.Title>
			</Card.Header>
		</Card.Root>
		<Card.Root size="sm">
			<Card.Header>
				<Card.Description>{m.balance_in_boxes()}</Card.Description>
				<Card.Title class="text-xl tabular-nums">{fmt.format(data.balanceSummary.inBoxes)}</Card.Title>
			</Card.Header>
		</Card.Root>
		<Card.Root size="sm" class={isUnreconciled ? 'ring-destructive/60' : ''}>
			<Card.Header>
				<Card.Description>{m.balance_available_to_spend()}</Card.Description>
				<Card.Title class="text-xl tabular-nums {isUnreconciled ? 'text-destructive' : ''}">
					{fmt.format(data.balanceSummary.availableToSpend)}
				</Card.Title>
			</Card.Header>
		</Card.Root>
	</section>

	<div class="flex items-center gap-2 border-b" role="tablist" aria-label={m.boxes_view_filter()}>
		<button
			type="button"
			role="tab"
			aria-selected={!showArchived}
			onclick={() => (showArchived = false)}
			class="border-b-2 px-3 py-2 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring {!showArchived ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'}"
		>
			{m.boxes_active()} <Badge variant="secondary">{data.boxes.length}</Badge>
		</button>
		<button
			type="button"
			role="tab"
			aria-selected={showArchived}
			onclick={() => (showArchived = true)}
			class="border-b-2 px-3 py-2 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring {showArchived ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'}"
		>
			{m.boxes_archived()} <Badge variant="secondary">{data.archivedBoxes.length}</Badge>
		</button>
	</div>

	{#if !showArchived && data.boxes.length === 0}
		<Empty.Root class="border">
			<Empty.Media variant="icon"><PackageOpen /></Empty.Media>
			<Empty.Header>
				<Empty.Title>{m.boxes_empty_title()}</Empty.Title>
				<Empty.Description>{m.boxes_empty_description()}</Empty.Description>
			</Empty.Header>
			<Empty.Content>
				<Button onclick={openCreate}>{m.boxes_create_first()}</Button>
			</Empty.Content>
		</Empty.Root>
	{:else if showArchived && data.archivedBoxes.length === 0}
		<Empty.Root class="border">
			<Empty.Media variant="icon"><Archive /></Empty.Media>
			<Empty.Header>
				<Empty.Title>{m.boxes_archived_empty_title()}</Empty.Title>
				<Empty.Description>{m.boxes_archived_empty_description()}</Empty.Description>
			</Empty.Header>
		</Empty.Root>
	{:else}
		<div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" role="tabpanel">
			{#each (showArchived ? data.archivedBoxes : data.boxes) as box, index (box.id)}
				<BoxCard box={box} formattedBalance={fmt.format(box.balance)} archived={showArchived}>
					{#snippet actions()}
						{#if showArchived}
							<form
								method="POST"
								action="?/restore"
								use:kitEnhance={() => async ({ result, update }) => {
									if (result.type === 'success') {
										toast.success(m.boxes_restored());
										await update();
									} else {
										toast.error(actionError(result, m.error_box_restore()));
									}
								}}
							>
								<input type="hidden" name="id" value={box.id} />
								<Button type="submit" size="sm" variant="outline">
									<RotateCcw data-icon="inline-start" />
									{m.common_restore()}
								</Button>
							</form>
						{:else}
							<div class="flex items-center gap-1">
								<Button
									size="icon-sm"
									variant="ghost"
									onclick={() => reorder(index, -1)}
									disabled={index === 0}
									aria-label={m.boxes_move_up({ name: box.name })}
								>
									<ArrowUp />
								</Button>
								<Button
									size="icon-sm"
									variant="ghost"
									onclick={() => reorder(index, 1)}
									disabled={index === data.boxes.length - 1}
									aria-label={m.boxes_move_down({ name: box.name })}
								>
									<ArrowDown />
								</Button>
							</div>
							<div class="flex items-center gap-1">
								<Button size="icon-sm" variant="ghost" onclick={() => openEdit(box)} aria-label={m.boxes_edit_aria({ name: box.name })}>
									<Pencil />
								</Button>
								<Button
									size="icon-sm"
									variant="ghost"
									onclick={() => openArchive(box)}
									disabled={!isZero(box.balance)}
									title={!isZero(box.balance) ? m.boxes_withdraw_before_archive() : m.boxes_archive()}
									aria-label={m.boxes_archive_aria({ name: box.name })}
								>
									<Archive />
								</Button>
							</div>
						{/if}
					{/snippet}
				</BoxCard>
			{/each}
		</div>
	{/if}
</div>

<Dialog.Root bind:open={dialogOpen}>
	<Dialog.Content class="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
		<Dialog.Header>
			<Dialog.Title>{editMode ? m.boxes_edit_title() : m.boxes_new_title()}</Dialog.Title>
			<Dialog.Description>
				{editMode ? m.boxes_edit_description() : m.boxes_new_description()}
			</Dialog.Description>
		</Dialog.Header>

		{#if $message}
			<Alert.Root variant="destructive"><Alert.Description>{$message}</Alert.Description></Alert.Root>
		{/if}

		<form method="POST" action={editMode ? '?/update' : '?/create'} use:enhance class="grid gap-4">
			{#if editMode && $form.id}<input type="hidden" name="id" value={$form.id} />{/if}

			<div class="grid gap-4 sm:grid-cols-[1fr_6rem]">
				<Form.Field form={sf} name="name">
					<Form.Control>
						{#snippet children({ props })}
							<Form.Label>{m.common_name()}</Form.Label>
							<Input {...props} bind:value={$form.name} maxlength={100} placeholder={m.boxes_name_placeholder()} />
						{/snippet}
					</Form.Control>
					<Form.FieldErrors />
				</Form.Field>

				<Form.Field form={sf} name="icon">
					<Form.Control>
						{#snippet children({ props })}
							<Form.Label>{m.boxes_icon()}</Form.Label>
							<Input {...props} bind:value={$form.icon} maxlength={16} placeholder="🏖️" />
						{/snippet}
					</Form.Control>
					<Form.FieldErrors />
				</Form.Field>
			</div>

			<Form.Field form={sf} name="description">
				<Form.Control>
					{#snippet children({ props })}
						<Form.Label>{m.common_description()} <span class="text-muted-foreground">{m.common_optional()}</span></Form.Label>
						<Textarea {...props} bind:value={$form.description} maxlength={500} rows={3} placeholder={m.boxes_description_placeholder()} />
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<div class="grid gap-1.5">
				<span class="text-sm font-medium">{m.common_colour()}</span>
				<input type="hidden" name="hue" value={$form.hue} />
				<ColorPicker name={$form.name || m.boxes_sample_name()} hue={$form.hue} onchange={(hue) => ($form.hue = hue)} />
			</div>

			<Dialog.Footer>
				<Button type="button" variant="outline" onclick={() => (dialogOpen = false)}>{m.common_cancel()}</Button>
				<Button type="submit" disabled={$submitting}>
					{$submitting ? m.common_saving() : editMode ? m.common_update() : m.common_create()}
				</Button>
			</Dialog.Footer>
		</form>
	</Dialog.Content>
</Dialog.Root>

<form
	bind:this={reorderForm}
	method="POST"
	action="?/reorder"
	class="hidden"
	aria-hidden="true"
	use:kitEnhance={() => async ({ result, update }) => {
		if (result.type === 'success') {
			toast.success(m.boxes_reordered());
			await update();
		} else {
			toast.error(actionError(result, m.error_box_reorder()));
		}
	}}
>
	<input type="hidden" name="boxIds" value={reorderedIds} />
</form>

<form
	bind:this={archiveForm}
	method="POST"
	action="?/archive"
	class="hidden"
	aria-hidden="true"
	use:kitEnhance={() => async ({ result, update }) => {
		if (result.type === 'success') {
			archiveTargetId = null;
			toast.success(m.boxes_archived_success());
			await update();
		} else {
			toast.error(actionError(result, m.error_box_archive()));
		}
	}}
>
	<input type="hidden" name="id" value={archiveTargetId} />
</form>
