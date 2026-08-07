<script lang="ts">
	import {
		Check,
		ChevronRight,
		DatabaseBackup,
		Layers,
		Loader2,
		Trash2,
		Users,
	} from '@lucide/svelte';
	import { untrack } from 'svelte';
	import { ColorPicker } from '$lib/components/color-picker';
	import * as Card from '$lib/components/ui/card';
	import { Label } from '$lib/components/ui/label';
	import { NativeSelect } from '$lib/components/native-select';
	import { Badge } from '$lib/components/ui/badge';
	import { Button } from '$lib/components/ui/button';
	import { Checkbox } from '$lib/components/ui/checkbox';
	import type { PageData } from './$types';
	import { invalidateAll } from '$app/navigation';
	import { m } from '$lib/paraglide/messages.js';
	import { setLocale } from '$lib/paraglide/runtime';

	const { data }: { data: PageData } = $props();

	const BODY_FAMILY: Record<string, string> = {
		Geist: "'Geist Variable'",
		Inter: "'Inter Variable'",
		'System UI': 'system-ui',
	};
	const HEADING_FAMILY: Record<string, string> = {
		Fraunces: "'Fraunces Variable'",
		'Playfair Display': "'Playfair Display Variable'",
	};

	type TransactionSortBy = 'transactionDate' | 'amount' | 'direction' | 'description' | 'categoryName' | 'contactName';
	type TransactionSortDirection = 'asc' | 'desc';

	type SaveState = 'idle' | 'saving' | 'saved' | 'error';

	const localeItems = [
		{ value: 'es', label: m.settings_language_spanish() },
		{ value: 'en', label: m.settings_language_english() },
	];
	const timeZoneItems = $derived(
		data.timeZones.map((value) => ({
			value,
			label: value.replaceAll('_', ' '),
		})),
	);
	const pageSizeItems = [10, 25, 50, 100].map((size) => ({
		value: String(size),
		label: m.transactions_page_size({ size }),
	}));
	const sortItems: { value: TransactionSortBy; label: string }[] = [
		{ value: 'transactionDate', label: m.common_date() },
		{ value: 'amount', label: m.common_amount() },
		{ value: 'description', label: m.common_description() },
		{ value: 'categoryName', label: m.common_category() },
		{ value: 'contactName', label: m.common_contact() },
		{ value: 'direction', label: m.common_direction() },
	];
	const sortDirectionItems: { value: TransactionSortDirection; label: string }[] = [
		{ value: 'desc', label: m.transactions_sort_descending() },
		{ value: 'asc', label: m.transactions_sort_ascending() },
	];
	const navOptions = [
		{ value: '/', label: m.nav_dashboard() },
		{ value: '/transactions', label: m.nav_transactions() },
		{ value: '/boxes', label: m.nav_boxes() },
		{ value: '/subscriptions', label: m.nav_subscriptions() },
		{ value: '/debts', label: m.nav_debts() },
		{ value: '/settings', label: m.nav_settings() },
	];
	const defaultPinnedNavItems = ['/transactions', '/subscriptions', '/debts'];
	const managementItems = [
		{
			href: '/categories',
			label: m.nav_categories(),
			description: m.settings_categories_description(),
			icon: Layers,
		},
		{
			href: '/contacts',
			label: m.nav_contacts(),
			description: m.settings_contacts_description(),
			icon: Users,
		},
		{
			href: '/trash',
			label: m.nav_trash(),
			description: m.settings_trash_description(),
			icon: Trash2,
		},
	];

	const initialPreferences = untrack(() => data.preferences);

	let primaryHue = $state(initialPreferences.primaryHue);
	let headingFont = $state(initialPreferences.headingFont);
	let bodyFont = $state(initialPreferences.bodyFont);
	let locale = $state(initialPreferences.locale);
	let transactionPageSize = $state(initialPreferences.transactionPageSize);
	let transactionSortBy = $state(initialPreferences.transactionSortBy as TransactionSortBy);
	let transactionSortDirection = $state(initialPreferences.transactionSortDirection as TransactionSortDirection);
	let mobilePinnedNavItems = $state(normalizePinnedNavItems(initialPreferences.mobilePinnedNavItems));
	let dockMagnification = $state(initialPreferences.dockMagnification);
	let timeZone = $state(initialPreferences.timeZone);
	let saveState = $state<SaveState>('idle');
	let savedTimer: ReturnType<typeof setTimeout> | null = null;
	let debounceTimer: ReturnType<typeof setTimeout> | null = null;

	function normalizePinnedNavItems(csv: string | undefined): string[] {
		const allowed = new Set(navOptions.map((item) => item.value));
		const items = (csv ?? '')
			.split(',')
			.filter((href) => allowed.has(href));
		const next = [...items, ...defaultPinnedNavItems].filter(
			(href, index, all) => all.indexOf(href) === index,
		);
		return next.slice(0, 3);
	}

	function applyLocally(hue: number, body: string, heading: string) {
		const root = document.documentElement;
		root.style.setProperty('--primary-hue', String(hue));
		root.style.setProperty('--user-body-font', BODY_FAMILY[body] ?? BODY_FAMILY.Geist);
		root.style.setProperty('--user-heading-font', HEADING_FAMILY[heading] ?? HEADING_FAMILY.Fraunces);
	}

	async function persist({ reloadLocale = false }: { reloadLocale?: boolean } = {}) {
		saveState = 'saving';
		try {
			const res = await fetch('/api/user/preferences', {
				method: 'PUT',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({
					primaryHue,
					headingFont,
					bodyFont,
					locale,
					transactionPageSize,
					transactionSortBy,
					transactionSortDirection,
					mobilePinnedNavItems: mobilePinnedNavItems.join(','),
					dockMagnification,
					timeZone,
				}),
			});
			if (!res.ok) {
				saveState = 'error';
				return;
			}
			saveState = 'saved';
			await invalidateAll();
			if (savedTimer) clearTimeout(savedTimer);
			savedTimer = setTimeout(() => {
				if (saveState === 'saved') saveState = 'idle';
			}, 2000);
			if (reloadLocale) {
				setLocale(locale as 'en' | 'es');
			}
		} catch {
			saveState = 'error';
		}
	}

	function onHueChange(next: number) {
		primaryHue = next;
		applyLocally(next, bodyFont, headingFont);
		if (debounceTimer) clearTimeout(debounceTimer);
		debounceTimer = setTimeout(persist, 500);
	}

	function onBodyChange(next: string) {
		bodyFont = next;
		applyLocally(primaryHue, next, headingFont);
		void persist();
	}

	function onHeadingChange(next: string) {
		headingFont = next;
		applyLocally(primaryHue, bodyFont, next);
		void persist();
	}

	function onLocaleChange(next: string) {
		locale = next;
		void persist({ reloadLocale: true });
	}

	function onTimeZoneChange(next: string) {
		timeZone = next;
		void persist();
	}

	function onTransactionDefaultChange(
		next: Partial<{
			transactionPageSize: number;
			transactionSortBy: TransactionSortBy;
			transactionSortDirection: TransactionSortDirection;
		}>,
	) {
		transactionPageSize = next.transactionPageSize ?? transactionPageSize;
		transactionSortBy = next.transactionSortBy ?? transactionSortBy;
		transactionSortDirection = next.transactionSortDirection ?? transactionSortDirection;
		void persist();
	}

	function onPinnedNavItemChange(index: number, href: string) {
		const next = [...mobilePinnedNavItems];
		next[index] = href;
		const seen = new Set<string>();
		mobilePinnedNavItems = [...next, ...defaultPinnedNavItems].filter((item) => {
			if (seen.has(item)) return false;
			seen.add(item);
			return true;
		}).slice(0, 3);
		void persist();
	}

	function toggleDockMagnification() {
		dockMagnification = !dockMagnification;
		void persist();
	}

	function clearRecents() {
		localStorage.removeItem('keenti.nav.recents');
		window.dispatchEvent(new CustomEvent('keenti:nav-recents-cleared'));
	}
</script>

<div class="max-w-4xl space-y-6">
	<div class="flex items-start justify-between gap-4">
		<div>
			<h1 class="text-2xl font-semibold tracking-tight">{m.settings_title()}</h1>
			<p class="text-sm text-muted-foreground">{m.settings_description()}</p>
		</div>
		<div class="min-w-24 text-right text-sm text-muted-foreground" aria-live="polite">
			{#if saveState === 'saving'}
				<Badge variant="secondary" class="gap-1">
					<Loader2 class="h-3.5 w-3.5 animate-spin" />
					{m.common_saving()}
				</Badge>
			{:else if saveState === 'saved'}
				<Badge variant="success" class="gap-1">
					<Check class="h-3.5 w-3.5" />
					{m.settings_saved()}
				</Badge>
			{:else if saveState === 'error'}
				<Badge variant="destructive">{m.settings_could_not_save()}</Badge>
			{/if}
		</div>
	</div>

	<section class="space-y-3">
		<div>
			<h2 class="text-base font-semibold">{m.settings_management()}</h2>
			<p class="text-sm text-muted-foreground">{m.settings_management_description()}</p>
		</div>
		<div class="grid gap-3 md:grid-cols-3">
			{#each managementItems as item}
				<a
					href={item.href}
					class="group flex min-h-28 flex-col justify-between rounded-xl border bg-card p-4 text-card-foreground transition-colors hover:bg-muted/50 focus:outline-none focus-visible:ring-2 focus-visible:ring-ring"
				>
					<div class="flex items-start justify-between gap-3">
						<div class="flex items-center gap-2">
							<item.icon class="h-4 w-4 text-muted-foreground" />
							<h3 class="font-medium">{item.label}</h3>
						</div>
						<ChevronRight class="h-4 w-4 text-muted-foreground transition-transform group-hover:translate-x-0.5" />
					</div>
					<p class="text-sm text-muted-foreground">{item.description}</p>
				</a>
			{/each}
		</div>
	</section>

	<section class="space-y-3">
		<div>
			<h2 class="text-base font-semibold">{m.settings_appearance()}</h2>
			<p class="text-sm text-muted-foreground">{m.settings_appearance_description()}</p>
		</div>
		<div class="grid gap-4 lg:grid-cols-2">
			<Card.Root>
				<Card.Header>
					<Card.Title>{m.settings_primary_colour()}</Card.Title>
					<Card.Description>{m.settings_primary_colour_description()}</Card.Description>
				</Card.Header>
				<Card.Content>
					<ColorPicker name={m.settings_primary_colour()} hue={primaryHue} onchange={onHueChange} />
				</Card.Content>
			</Card.Root>

			<Card.Root>
				<Card.Header>
					<Card.Title>{m.settings_typography()}</Card.Title>
					<Card.Description>{m.settings_typography_description()}</Card.Description>
				</Card.Header>

				<Card.Content>
					<div class="grid gap-4">
						<div class="grid gap-1.5">
							<Label for="heading-font">{m.settings_heading_font()}</Label>
							<NativeSelect
								id="heading-font"
								name="heading-font"
								value={headingFont}
								onValueChange={onHeadingChange}
								items={[
									{ value: 'Fraunces', label: 'Fraunces' },
									{ value: 'Playfair Display', label: 'Playfair Display' },
								]}
							/>
							<p class="font-heading mt-1 text-xl">{m.settings_font_preview_short()}</p>
						</div>

						<div class="grid gap-1.5">
							<Label for="body-font">{m.settings_body_font()}</Label>
							<NativeSelect
								id="body-font"
								name="body-font"
								value={bodyFont}
								onValueChange={onBodyChange}
								items={[
									{ value: 'Geist', label: 'Geist' },
									{ value: 'Inter', label: 'Inter' },
									{ value: 'System UI', label: 'System UI' },
								]}
							/>
							<p class="mt-1 text-sm">{m.settings_font_preview_long()}</p>
						</div>
					</div>
				</Card.Content>
			</Card.Root>
		</div>
	</section>

	<section class="grid gap-4 lg:grid-cols-2">
		<Card.Root>
			<Card.Header>
				<Card.Title>{m.settings_regional()}</Card.Title>
				<Card.Description>{m.settings_regional_description()}</Card.Description>
			</Card.Header>
			<Card.Content>
				<div class="grid gap-4">
					<div class="grid gap-1.5">
						<Label for="locale">{m.settings_language()}</Label>
						<NativeSelect
							id="locale"
							name="locale"
							value={locale}
							onValueChange={onLocaleChange}
							items={localeItems}
						/>
					</div>
					<div class="grid gap-1.5">
						<Label for="time-zone">{m.settings_time_zone()}</Label>
						<NativeSelect
							id="time-zone"
							name="time-zone"
							value={timeZone}
							onValueChange={onTimeZoneChange}
							items={timeZoneItems}
						/>
						<p class="text-xs text-muted-foreground">{m.settings_time_zone_description()}</p>
					</div>
				</div>
			</Card.Content>
		</Card.Root>

		<Card.Root>
			<Card.Header>
				<Card.Title>{m.settings_transaction_defaults()}</Card.Title>
				<Card.Description>{m.settings_transaction_defaults_description()}</Card.Description>
			</Card.Header>
			<Card.Content>
				<div class="grid gap-4">
					<div class="grid gap-1.5">
						<Label for="transaction-page-size">{m.settings_default_page_size()}</Label>
						<NativeSelect
							id="transaction-page-size"
							name="transaction-page-size"
							value={String(transactionPageSize)}
							onValueChange={(value) => onTransactionDefaultChange({ transactionPageSize: Number(value) })}
							items={pageSizeItems}
						/>
					</div>
					<div class="grid gap-1.5">
						<Label for="transaction-sort-by">{m.settings_default_sort()}</Label>
						<NativeSelect
							id="transaction-sort-by"
							name="transaction-sort-by"
							value={transactionSortBy}
							onValueChange={(value) => onTransactionDefaultChange({ transactionSortBy: value as TransactionSortBy })}
							items={sortItems}
						/>
					</div>
					<div class="grid gap-1.5">
						<Label for="transaction-sort-direction">{m.transactions_sort_direction()}</Label>
						<NativeSelect
							id="transaction-sort-direction"
							name="transaction-sort-direction"
							value={transactionSortDirection}
							onValueChange={(value) =>
								onTransactionDefaultChange({ transactionSortDirection: value as TransactionSortDirection })}
							items={sortDirectionItems}
						/>
					</div>
				</div>
			</Card.Content>
		</Card.Root>
	</section>

	<Card.Root>
		<Card.Header>
			<Card.Title>{m.settings_navigation()}</Card.Title>
			<Card.Description>{m.settings_navigation_description()}</Card.Description>
		</Card.Header>
		<Card.Content>
			<div class="grid gap-5 lg:grid-cols-[1fr_auto] lg:items-start">
				<div class="grid gap-4 md:grid-cols-3">
					{#each mobilePinnedNavItems as href, index}
						<div class="grid gap-1.5">
							<Label for="mobile-pin-{index}">{m.settings_mobile_pin({ index: index + 1 })}</Label>
							<NativeSelect
								id="mobile-pin-{index}"
								name="mobile-pin-{index}"
								value={href}
								onValueChange={(value) => onPinnedNavItemChange(index, value)}
								items={navOptions}
							/>
						</div>
					{/each}
				</div>
				<div class="flex flex-col gap-3">
					<label class="flex items-center gap-2 text-sm font-medium">
						<Checkbox checked={dockMagnification} onclick={toggleDockMagnification} />
						<span>{m.settings_dock_magnification()}</span>
					</label>
					<Button variant="outline" onclick={clearRecents}>
						<DatabaseBackup data-icon="inline-start" />
						{m.settings_clear_recents()}
					</Button>
				</div>
			</div>
		</Card.Content>
	</Card.Root>
</div>
