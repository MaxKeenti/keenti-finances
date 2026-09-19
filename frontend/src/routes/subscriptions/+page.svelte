<script lang="ts">
	import { superForm } from 'sveltekit-superforms';
	import { zod4Client } from 'sveltekit-superforms/adapters';
	import { z } from 'zod';
	import { toast } from 'svelte-sonner';
	import { page } from '$app/state';
	import { enhance as kitEnhance } from '$app/forms';
	import { submitWithAdaptiveConfirm } from '$lib/components/adaptive-confirm';
	import * as Dialog from '$lib/components/ui/dialog';
	import * as Form from '$lib/components/ui/form';
	import * as Alert from '$lib/components/ui/alert';
	import * as Empty from '$lib/components/ui/empty';
	import * as Card from '$lib/components/ui/card';
	import { Input } from '$lib/components/ui/input';
	import { Checkbox } from '$lib/components/ui/checkbox';
	import { Button } from '$lib/components/ui/button';
	import { Badge } from '$lib/components/ui/badge';
	import { NativeSelect } from '$lib/components/native-select';
	import { NativeDatePicker } from '$lib/components/native-date-picker';
	import * as Select from '$lib/components/ui/select';
	import { dateInTimeZone, formatDateOnly, mxnFormatter } from '$lib/formatting';
	import { SectionUnavailable } from '$lib/components/section-status';
	import { sectionValue } from '$lib/types/section';
	import { priceEquivalents } from '$lib/subscription-summary';
	import { billingGenerationStatus } from '$lib/obligation-status';
	import { billingGenerationLabel } from '$lib/subscription-labels';
	import { m } from '$lib/paraglide/messages.js';
	import type { PageData } from './$types';

	const subscriptionSchema = z.object({
		id: z.coerce.number().optional(),
		name: z.string().min(1, m.validation_name_required()),
		cost: z.coerce.number().positive(m.validation_cost_positive()),
		billingCycle: z.enum(['MONTHLY', 'YEARLY']),
		type: z.enum(['PERSONAL', 'SHARED']),
		categoryId: z.union([z.coerce.number(), z.literal('')]).optional(),
		nextBillingDate: z.string().min(1, m.validation_next_billing_required()),
		ownerParticipates: z.boolean().optional(),
	});

	type MemberResponse = {
		id: number;
		subscriptionId: number;
		contactId: number | null;
		contactName: string | null;
		shareAmount: number | null;
		createdAt: string;
	};

	type Subscription = {
		id: number;
		name: string;
		cost: number;
		billingCycle: string;
		type: string;
		categoryId: number | null;
		/** Generation cursor; `null` when the stored date could not be read. */
		nextBillingDate: string | null;
		tokenUuid: string | null;
		ownerParticipates: boolean | null;
		createdAt: string;
		members?: { status: string; data?: MemberResponse[] };
	};

	let { data }: { data: PageData } = $props();

	// `null` means the list could not be read. It is deliberately not an empty
	// array: "you have no subscriptions" and "we could not read them" lead to
	// different totals, and only one of them is a fact.
	const subscriptions = $derived(sectionValue(data.subscriptions));
	const categories = $derived(sectionValue(data.categories));
	const contacts = $derived(sectionValue(data.contacts));

	/** A Shared Subscription's Members, or `null` when that list failed. */
	function membersOf(sub: Subscription): MemberResponse[] | null {
		if (sub.type !== 'SHARED') return [];
		if (!sub.members || sub.members.status !== 'ok') return null;
		return sub.members.data ?? null;
	}

	let dialogOpen = $state(false);
	let memberDialogOpen = $state(false);
	let editMode = $state(false);
	let deleteTargetId = $state<number | null>(null);
	let deleteForm = $state<HTMLFormElement | null>(null);
	// The dialog holds an *id*, not a captured Subscription. Holding the object
	// froze the member list as it was when the dialog opened: adding or removing
	// a Member reloaded the page data, and the dialog kept rendering the stale
	// copy, so the change only appeared after closing and reopening.
	let memberTargetId = $state<number | null>(null);
	const memberTargetSub = $derived(
		memberTargetId === null
			? null
			: ((subscriptions ?? []).find((sub) => sub.id === memberTargetId) ?? null),
	);
	let selectedContactId = $state('');
	// One member write at a time: `addMember` is not idempotent, and a second
	// submit before the first reload lands would ask for the same Member twice.
	let memberActionPending = $state(false);

	const today = $derived(dateInTimeZone(data.preferences.timeZone));

	const sf = superForm(data.form, {
		dataType: 'json',
		validators: zod4Client(subscriptionSchema),
		onResult({ result }) {
			if (result.type === 'success') {
				dialogOpen = false;
				toast.success(editMode ? m.subscriptions_updated() : m.subscriptions_created());
			} else if (result.type === 'failure') {
				const msg = (result.data as Record<string, unknown> | undefined)?.form as
					| { message?: string }
					| undefined;
				if (msg?.message) toast.error(msg.message);
			}
		},
	});
	const { form, enhance, submitting, message } = sf;

	function openCreate() {
		editMode = false;
		sf.reset({
			data: {
				name: '',
				cost: 0,
				billingCycle: 'MONTHLY',
				type: 'PERSONAL',
				categoryId: '',
				nextBillingDate: today,
				ownerParticipates: true,
			},
		});
		dialogOpen = true;
	}

	function openEdit(sub: Subscription) {
		if (typeof sub.ownerParticipates !== 'boolean') {
			toast.error(m.subscriptions_edit_unavailable());
			return;
		}
		editMode = true;
		form.set({
			id: sub.id,
			name: sub.name,
			cost: sub.cost,
			billingCycle: sub.billingCycle as 'MONTHLY' | 'YEARLY',
			type: sub.type as 'PERSONAL' | 'SHARED',
			categoryId: sub.categoryId ?? '',
			// An unreadable stored cursor cannot be echoed back as if it were the
			// User's choice; leave it empty so saving requires a deliberate date.
			nextBillingDate: sub.nextBillingDate ?? '',
			ownerParticipates: sub.ownerParticipates,
		});
		dialogOpen = true;
	}

	async function openDelete(sub: Subscription) {
		deleteTargetId = sub.id;
		await submitWithAdaptiveConfirm(deleteForm, {
			title: m.subscriptions_delete_title(),
			description: `${m.delete_confirm_prefix()} ${sub.name}${m.delete_confirm_suffix()}`,
			confirmLabel: m.common_delete(),
			cancelLabel: m.common_cancel(),
			destructive: true,
		});
	}

	function openMembers(sub: Subscription) {
		memberTargetId = sub.id;
		selectedContactId = '';
		memberDialogOpen = true;
	}

	// `/subscriptions?members=<id>` is where the detail page's "Add members"
	// empty action lands, since member management lives on this page. It only
	// opens a dialog — no write happens until the User adds someone.
	//
	// The request is honoured once per id. The query parameter outlives the
	// dialog, and every add, remove and delete reloads the page data, so a
	// guard of "is the dialog closed" reopened the dialog the User had just
	// dismissed — and did it again on every subsequent reload.
	let handledMemberRequest = $state<number | null>(null);
	$effect(() => {
		const requested = Number(page.url.searchParams.get('members'));
		if (!requested) {
			handledMemberRequest = null;
			return;
		}
		if (handledMemberRequest === requested) return;
		const target = (subscriptions ?? []).find((sub) => sub.id === requested);
		if (!target) return;
		handledMemberRequest = requested;
		openMembers(target);
	});

	// A dialog whose Subscription vanished from the reloaded list (deleted in
	// another tab, or a list read that failed) has nothing left to manage.
	$effect(() => {
		if (memberDialogOpen && memberTargetId !== null && memberTargetSub === null) {
			memberDialogOpen = false;
			memberTargetId = null;
		}
	});

	const fmt = $derived(mxnFormatter(data.preferences.locale));

	// A failed member list cannot tell which Contacts are already Members, so
	// offering "every Contact" would invite a duplicate the backend rejects.
	function availableContacts(sub: Subscription) {
		const members = membersOf(sub);
		if (members === null || contacts === null) return null;
		const memberContactIds = new Set(members.map((m) => m.contactId));
		return contacts.filter((c) => !memberContactIds.has(c.id));
	}

	const cycleBadgeVariant: Record<string, 'info' | 'purple'> = {
		MONTHLY: 'info',
		YEARLY: 'purple',
	};

	const typeBadgeVariant: Record<string, 'secondary' | 'warning'> = {
		PERSONAL: 'secondary',
		SHARED: 'warning',
	};

	// The recurring commitment is the one number this page exists to convey,
	// and it is a *price* equivalent: yearly plans are normalised to a twelfth
	// so the two figures are comparable, which is not the cash due in any
	// single month. A failed list yields `null` rather than a confident 0.00.
	const totals = $derived(priceEquivalents(subscriptions));
</script>

<svelte:head><title>{m.subscriptions_title()} · Keenti</title></svelte:head>

<div class="space-y-6">
	<div class="flex items-center justify-between">
		<div>
			<h1 class="text-2xl font-semibold tracking-tight">{m.subscriptions_title()}</h1>
			<p class="text-sm text-muted-foreground">{m.subscriptions_description()}</p>
		</div>
		<Button onclick={openCreate}>{m.subscriptions_new()}</Button>
	</div>

	{#if totals === null}
		<SectionUnavailable
			title={m.section_subscriptions_unavailable()}
			description={m.section_subscriptions_unavailable_description()}
		/>
	{:else if subscriptions !== null && subscriptions.length > 0}
		<Card.Root>
			<Card.Content class="space-y-3">
				<p class="text-sm font-medium">{m.subscriptions_price_equivalents_title()}</p>
				<div class="grid gap-4 sm:grid-cols-2">
					<div>
						<p class="text-xs text-muted-foreground">{m.subscriptions_monthly_total()}</p>
						<p class="text-2xl font-semibold tabular-nums">{fmt.format(totals.monthly)}</p>
						<p class="mt-1 text-xs text-muted-foreground">{m.subscriptions_monthly_total_description()}</p>
					</div>
					<div class="sm:border-l sm:pl-4">
						<p class="text-xs text-muted-foreground">{m.subscriptions_yearly_total()}</p>
						<p class="text-2xl font-semibold tabular-nums">{fmt.format(totals.yearly)}</p>
					</div>
				</div>
				<!-- Gross price equivalents, not cash due and not net of the
				     contributions the User collects back from Members. -->
				<p class="text-xs text-muted-foreground">{m.subscriptions_price_equivalents_note()}</p>
				{#if totals.excluded > 0}
					<p class="text-xs text-muted-foreground">{m.subscriptions_totals_excluded({ count: totals.excluded })}</p>
				{/if}
			</Card.Content>
		</Card.Root>
	{/if}

	{#if subscriptions === null}
		<!-- The unavailable notice above already says why; nothing is listed. -->
	{:else if subscriptions.length === 0}
		<Empty.Root class="border">
			<Empty.Title>{m.subscriptions_empty_title()}</Empty.Title>
			<Empty.Description>{m.subscriptions_empty_description()}</Empty.Description>
		</Empty.Root>
	{:else}
		<div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
			{#each subscriptions as sub (sub.id)}
				{@const subMembers = membersOf(sub)}
				{@const generation = billingGenerationStatus({ nextBillingDate: sub.nextBillingDate, today: data.obligationToday })}
				<Card.Root class="flex flex-col relative">
					<a
						href="/subscriptions/{sub.id}"
						class="absolute inset-0 rounded-[inherit] focus:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
						aria-label={m.subscriptions_view_aria({ name: sub.name })}
					></a>
					<Card.Content class="flex flex-1 flex-col space-y-3">
						<div class="flex items-start justify-between gap-2">
							<div class="min-w-0">
								<p class="font-semibold text-base truncate">{sub.name}</p>
								<p class="text-xl font-bold text-foreground mt-0.5">{fmt.format(sub.cost)}</p>
							</div>
							<div class="flex flex-col items-end gap-1 shrink-0">
								<Badge variant={typeBadgeVariant[sub.type]}>
									{sub.type === 'PERSONAL' ? m.subscription_personal() : m.subscription_shared()}
								</Badge>
								<Badge variant={cycleBadgeVariant[sub.billingCycle]}>
									{sub.billingCycle === 'MONTHLY' ? m.billing_monthly() : m.billing_yearly()}
								</Badge>
							</div>
						</div>

						<div class="text-xs text-muted-foreground space-y-0.5">
							<p>{billingGenerationLabel(generation.state)}{#if generation.scheduledDate}<span class="font-medium text-foreground">{' · '}{formatDateOnly(generation.scheduledDate, data.preferences.locale)}</span>{/if}</p>
							{#if sub.type === 'SHARED'}
								{#if subMembers === null}
									<!-- A failed member read must not render as "0 members",
									     which would also imply billing has nothing to create. -->
									<p>{m.subscriptions_members_count()} <span class="font-medium text-foreground">{m.subscriptions_members_count_unavailable()}</span></p>
								{:else}
									<p>{m.subscriptions_members_count()} <span class="font-medium text-foreground">{subMembers.length}</span></p>
								{/if}
							{/if}
						</div>

						{#if sub.type === 'SHARED' && subMembers !== null && subMembers.length === 0}
							<p class="text-xs text-muted-foreground">{m.subscriptions_no_members_yet_action()}</p>
						{/if}

						<!-- Buttons size to their labels; `flex-1` on Edit alone made it
						     hog the row while its neighbours stayed small. -->
						<div class="flex flex-wrap items-center gap-2 mt-auto pt-1 relative z-[1]">
							<Button variant="outline" size="sm" href="/subscriptions/{sub.id}">{m.common_view()}</Button>
							<Button variant="outline" size="sm" onclick={() => openEdit(sub)}>{m.common_edit()}</Button>
							{#if sub.type === 'SHARED'}
								<Button variant="outline" size="sm" onclick={() => openMembers(sub)}>
									{subMembers !== null && subMembers.length === 0
										? m.subscriptions_add_members()
										: m.subscriptions_members()}
								</Button>
							{/if}
							<Button variant="ghost" size="sm" class="ml-auto text-destructive hover:bg-destructive/10 hover:text-destructive" onclick={() => openDelete(sub)}>{m.common_delete()}</Button>
						</div>
					</Card.Content>
				</Card.Root>
			{/each}
		</div>
	{/if}
</div>

<!-- Create / Edit dialog -->
<Dialog.Root bind:open={dialogOpen}>
	<Dialog.Content class="sm:max-w-md">
		<Dialog.Header>
			<Dialog.Title>{editMode ? m.subscriptions_edit_title() : m.subscriptions_new_title()}</Dialog.Title>
			<Dialog.Description>
				{editMode
					? m.subscriptions_edit_description()
					: m.subscriptions_new_description()}
			</Dialog.Description>
		</Dialog.Header>

		{#if $message}
			<Alert.Root variant="destructive">
				<Alert.Description>{$message}</Alert.Description>
			</Alert.Root>
		{/if}

		<form
			method="POST"
			action={editMode ? '?/update' : '?/create'}
			use:enhance
			class="grid gap-4"
		>
			{#if editMode && $form.id}
				<input type="hidden" name="id" value={$form.id} />
			{/if}

			<Form.Field form={sf} name="name">
				<Form.Control>
					{#snippet children({ props })}
						<Form.Label>{m.common_name()}</Form.Label>
						<Input {...props} bind:value={$form.name} placeholder={m.subscriptions_placeholder_name()} />
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Form.Field form={sf} name="cost">
				<Form.Control>
					{#snippet children({ props })}
						<Form.Label>{m.subscriptions_cost_mxn()}</Form.Label>
						<Input {...props} type="number" step="0.01" min="0.01" bind:value={$form.cost} />
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<div class="grid grid-cols-2 gap-4">
				<Form.Field form={sf} name="billingCycle">
					<Form.Control>
						{#snippet children({ props })}
							{@const { name: fieldName, ...triggerProps } = props}
							<Form.Label>{m.common_billing_cycle()}</Form.Label>
							<NativeSelect
								name={fieldName}
								value={$form.billingCycle}
								onValueChange={(v) => { $form.billingCycle = v as 'MONTHLY' | 'YEARLY'; }}
								placeholder={m.common_select_cycle()}
								items={[
									{ value: 'MONTHLY', label: m.billing_monthly() },
									{ value: 'YEARLY', label: m.billing_yearly() },
								]}
								{...triggerProps}
							/>
						{/snippet}
					</Form.Control>
					<Form.FieldErrors />
				</Form.Field>

				<Form.Field form={sf} name="type">
					<Form.Control>
						{#snippet children({ props })}
							{@const { name: fieldName, ...triggerProps } = props}
							<Form.Label>{m.common_type()}</Form.Label>
							<NativeSelect
								name={fieldName}
								value={$form.type}
								onValueChange={(v) => { $form.type = v as 'PERSONAL' | 'SHARED'; }}
								placeholder={m.common_select_type()}
								items={[
									{ value: 'PERSONAL', label: m.subscription_personal() },
									{ value: 'SHARED', label: m.subscription_shared() },
								]}
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
						<Form.Label>{m.common_category_optional()}</Form.Label>
						{#if categories === null}
							<p class="text-xs text-muted-foreground">{m.section_categories_unavailable()}</p>
						{/if}
						<NativeSelect
							name={fieldName}
							value={$form.categoryId !== '' ? String($form.categoryId) : ''}
							onValueChange={(v) => { $form.categoryId = v ? Number(v) : ''; }}
							placeholder={m.common_none()}
							items={(categories ?? []).map(c => ({ value: String(c.id), label: c.name }))}
							{...triggerProps}
						/>
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			<Form.Field form={sf} name="nextBillingDate">
				<Form.Control>
					{#snippet children({ props })}
						{@const { name: fieldName, ...triggerProps } = props}
						<Form.Label>{m.subscriptions_next_billing_date()}</Form.Label>
						<NativeDatePicker
							name={fieldName}
							value={$form.nextBillingDate}
							onValueChange={(v) => { $form.nextBillingDate = v; }}
							{...triggerProps}
						/>
					{/snippet}
				</Form.Control>
				<Form.FieldErrors />
			</Form.Field>

			{#if $form.type === 'SHARED'}
				<Form.Field form={sf} name="ownerParticipates">
					<Form.Control>
						{#snippet children({ props })}
							<div class="flex items-center gap-2">
								<Checkbox
									id={props.id}
									bind:checked={$form.ownerParticipates}
								/>
								<Form.Label>{m.subscriptions_owner_participates()}</Form.Label>
							</div>
						{/snippet}
					</Form.Control>
				</Form.Field>
			{/if}

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
	bind:this={deleteForm}
	method="POST"
	action="?/delete"
	class="hidden"
	aria-hidden="true"
	use:kitEnhance={async () => {
		return async ({ result, update }) => {
			if (result.type === 'success') {
				deleteTargetId = null;
				toast.success(m.subscriptions_trashed());
				await update();
			} else {
				const msg =
					(result as { data?: { message?: string } }).data?.message ??
					m.subscriptions_delete_failed();
				toast.error(msg);
			}
		};
	}}
>
	<input type="hidden" name="id" value={deleteTargetId} />
</form>

<!-- Member management dialog (SHARED subscriptions only) -->
<Dialog.Root bind:open={memberDialogOpen}>
	<Dialog.Content class="sm:max-w-md">
		<Dialog.Header>
			<Dialog.Title>{m.subscriptions_members_title({ name: memberTargetSub?.name ?? '' })}</Dialog.Title>
			<Dialog.Description>{m.subscriptions_members_description()}</Dialog.Description>
		</Dialog.Header>

		{#if memberTargetSub}
			{@const dialogMembers = membersOf(memberTargetSub)}
			{@const dialogContacts = availableContacts(memberTargetSub)}
			<div class="space-y-4">
				<!-- Current members list -->
				{#if dialogMembers === null}
					<SectionUnavailable title={m.section_members_unavailable()} compact />
				{:else if dialogMembers.length === 0}
					<p class="text-sm text-muted-foreground">{m.subscriptions_no_members()}</p>
				{:else}
					<ul class="divide-y rounded-md border">
						{#each dialogMembers as member (member.id)}
							<li class="flex items-center justify-between px-3 py-2">
								<span class="text-sm">{member.contactName ?? m.contact_number({ id: member.contactId ?? member.id })}</span>
								<form
									method="POST"
									action="?/removeMember"
									use:kitEnhance={async () => {
										memberActionPending = true;
										return async ({ result, update }) => {
											memberActionPending = false;
											if (result.type === 'success') {
												toast.success(m.subscriptions_member_removed());
												// The dialog stays open on the reloaded list, so the
												// removal is visible where it was made.
												await update();
											} else {
												const msg =
													(result as { data?: { message?: string } }).data?.message ??
													m.subscriptions_member_remove_failed();
												toast.error(msg);
											}
										};
									}}
								>
									<input type="hidden" name="subscriptionId" value={memberTargetSub.id} />
									<input type="hidden" name="memberId" value={member.id} />
									<Button type="submit" variant="destructive" size="sm" disabled={memberActionPending}>
										{m.common_remove()}
									</Button>
								</form>
							</li>
						{/each}
					</ul>
				{/if}

				<!-- Add member -->
				{#if dialogContacts === null}
					<p class="text-sm text-muted-foreground">
						{contacts === null ? m.section_contacts_unavailable() : m.subscriptions_members_unavailable_hint()}
					</p>
				{:else if dialogContacts.length > 0}
					<form
						method="POST"
						action="?/addMember"
						use:kitEnhance={async () => {
							memberActionPending = true;
							return async ({ result, update }) => {
								memberActionPending = false;
								if (result.type === 'success') {
									selectedContactId = '';
									toast.success(m.subscriptions_member_added());
									// Stay open on the refreshed member list: adding one
									// Member is usually the first of several, and the
									// dialog now renders the live list rather than the
									// snapshot it opened with.
									await update();
								} else {
									const msg =
										(result as { data?: { message?: string } }).data?.message ??
										m.subscriptions_member_add_failed();
									toast.error(msg);
								}
							};
						}}
						class="flex gap-2"
					>
						<input type="hidden" name="subscriptionId" value={memberTargetSub.id} />
						<Select.Root name="contactId" bind:value={selectedContactId}>
							<Select.Trigger class="flex-1">
								<Select.Value placeholder={m.subscriptions_select_contact()} />
							</Select.Trigger>
							<Select.Content>
								{#each dialogContacts as c}
									<Select.Item value={String(c.id)}>{c.name}</Select.Item>
								{/each}
							</Select.Content>
						</Select.Root>
						<Button type="submit" disabled={!selectedContactId || memberActionPending}>
							{m.common_add()}
						</Button>
					</form>
				{:else}
					<p class="text-sm text-muted-foreground">{m.subscriptions_all_contacts_members()}</p>
				{/if}
			</div>
		{/if}

		<Dialog.Footer>
			<Button variant="outline" onclick={() => (memberDialogOpen = false)}>{m.common_close()}</Button>
		</Dialog.Footer>
	</Dialog.Content>
</Dialog.Root>
