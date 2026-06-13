<script lang="ts">
	import { page } from '$app/stores';
	import { untrack } from 'svelte';
	import {
		LayoutDashboard,
		ArrowLeftRight,
		CreditCard,
		HandCoins,
		Layers,
		Users,
		Trash2,
		Settings,
		LogOut,
		EllipsisVertical
	} from '@lucide/svelte';
	import DockOverflowDialog from './dock-overflow-dialog.svelte';
	import * as Tooltip from '$lib/components/ui/tooltip';
	import { Separator } from '$lib/components/ui/separator';
	import { Button } from '$lib/components/ui/button';
	import { m } from '$lib/paraglide/messages.js';
	import type { Component } from 'svelte';

	type NavItem = {
		href: string;
		label: string;
		icon: Component<{ class?: string }>;
	};

	const allNavItems: NavItem[] = [
		{ href: '/', label: m.nav_dashboard(), icon: LayoutDashboard },
		{ href: '/transactions', label: m.nav_transactions(), icon: ArrowLeftRight },
		{ href: '/subscriptions', label: m.nav_subscriptions(), icon: CreditCard },
		{ href: '/debts', label: m.nav_debts(), icon: HandCoins },
		{ href: '/categories', label: m.nav_categories(), icon: Layers },
		{ href: '/contacts', label: m.nav_contacts(), icon: Users },
		{ href: '/trash', label: m.nav_trash(), icon: Trash2 },
		{ href: '/settings', label: m.nav_settings(), icon: Settings }
	];

	// Mobile: 3 pinned items
	const pinnedItems = allNavItems.filter((item) =>
		['/transactions', '/subscriptions', '/debts'].includes(item.href)
	);

	// Mobile overflow: remaining items
	const overflowItems = allNavItems.filter(
		(item) => !['/transactions', '/subscriptions', '/debts'].includes(item.href)
	);

	const RECENTS_KEY = 'keenti.nav.recents';

	let overflowOpen = $state(false);
	let recentsLoaded = $state(false);
	let recentHrefs = $state<string[]>([]);

	function isActive(href: string) {
		const pathname = $page.url.pathname;
		return href === '/' ? pathname === '/' : pathname === href || pathname.startsWith(`${href}/`);
	}

	function matchingNavItem(pathname: string) {
		return allNavItems.find((item) =>
			item.href === '/' ? pathname === '/' : pathname === item.href || pathname.startsWith(`${item.href}/`),
		);
	}

	function readStoredRecents() {
		try {
			const value = JSON.parse(localStorage.getItem(RECENTS_KEY) ?? '[]');
			return Array.isArray(value)
				? value.filter((href): href is string => allNavItems.some((item) => item.href === href)).slice(0, 4)
				: [];
		} catch {
			return [];
		}
	}

	$effect(() => {
		if (!recentsLoaded) {
			recentHrefs = readStoredRecents();
			recentsLoaded = true;
		}

		const current = matchingNavItem($page.url.pathname);
		if (!current) return;

		const previous = untrack(() => recentHrefs);
		const next = [current.href, ...previous.filter((href) => href !== current.href)].slice(0, 4);
		if (next.join('|') === previous.join('|')) return;

		recentHrefs = next;
		localStorage.setItem(RECENTS_KEY, JSON.stringify(next));
	});

	const recentItems = $derived(
		recentHrefs
			.filter((href) => !isActive(href))
			.map((href) => allNavItems.find((item) => item.href === href))
			.filter((item): item is NavItem => Boolean(item))
			.slice(0, 3),
	);
</script>

<nav
	class="fixed inset-x-3 bottom-[calc(env(safe-area-inset-bottom)+0.75rem)] z-40 flex justify-center sm:inset-x-auto sm:left-1/2 sm:-translate-x-1/2"
	aria-label={m.nav_main()}
>
	<!-- Desktop: all items centered -->
	<Tooltip.Provider delayDuration={150}>
		<div
			class="hidden items-end gap-1 rounded-2xl border border-sidebar-border/70 bg-sidebar/80 px-2.5 py-2 shadow-2xl shadow-black/10 backdrop-blur-xl sm:flex"
		>
			{#each allNavItems as item}
				{@const active = isActive(item.href)}
				<Tooltip.Root>
					<Tooltip.Trigger>
						{#snippet child({ props })}
							{@const { type: _triggerType, ...triggerProps } = props}
							<a
								{...triggerProps}
								href={item.href}
								aria-label={item.label}
								class="flex h-10 w-10 items-center justify-center rounded-xl transition-all duration-150 ease-out hover:-translate-y-1 hover:scale-110
									{active
									? 'bg-sidebar-accent text-sidebar-accent-foreground'
									: 'text-sidebar-foreground hover:bg-sidebar-accent/50 hover:text-sidebar-accent-foreground'}"
							>
								<item.icon class="w-5 h-5 shrink-0" />
							</a>
						{/snippet}
					</Tooltip.Trigger>
					<Tooltip.Content side="top" sideOffset={8}>{item.label}</Tooltip.Content>
				</Tooltip.Root>
			{/each}

			{#if recentItems.length > 0}
				<Separator orientation="vertical" class="mx-1 h-6 bg-sidebar-border" />
				{#each recentItems as item}
					<Tooltip.Root>
						<Tooltip.Trigger>
							{#snippet child({ props })}
								{@const { type: _triggerType, ...triggerProps } = props}
								<a
									{...triggerProps}
									href={item.href}
									aria-label={item.label}
									class="flex h-9 w-9 items-center justify-center rounded-xl text-sidebar-foreground/75 transition-all duration-150 ease-out hover:-translate-y-1 hover:scale-110 hover:bg-sidebar-accent/50 hover:text-sidebar-accent-foreground"
								>
									<item.icon class="h-4.5 w-4.5 shrink-0" />
								</a>
							{/snippet}
						</Tooltip.Trigger>
						<Tooltip.Content side="top" sideOffset={10}>{item.label}</Tooltip.Content>
					</Tooltip.Root>
				{/each}
			{/if}

			<Separator orientation="vertical" class="mx-1 h-6 bg-sidebar-border" />

			<Tooltip.Root>
				<Tooltip.Trigger>
					{#snippet child({ props })}
						{@const { type: _triggerType, ...triggerProps } = props}
						<a
							{...triggerProps}
							href="/logout"
							aria-label={m.nav_logout()}
							class="flex h-10 w-10 items-center justify-center rounded-xl text-sidebar-foreground transition-all duration-150 ease-out hover:-translate-y-1 hover:scale-110 hover:bg-sidebar-accent/50 hover:text-sidebar-accent-foreground"
						>
							<LogOut class="w-5 h-5 shrink-0" />
						</a>
					{/snippet}
				</Tooltip.Trigger>
				<Tooltip.Content side="top" sideOffset={8}>{m.nav_logout()}</Tooltip.Content>
			</Tooltip.Root>
		</div>
	</Tooltip.Provider>

	<!-- Mobile: 3 pinned + overflow menu button -->
	<div
		class="flex w-full items-center gap-1 rounded-2xl border border-sidebar-border/70 bg-sidebar/90 px-2 py-2 shadow-2xl shadow-black/15 backdrop-blur-xl sm:hidden"
	>
		{#each pinnedItems as item}
			{@const active = isActive(item.href)}
			<a
				href={item.href}
				aria-label={item.label}
				class="flex flex-1 flex-col items-center justify-center gap-1 rounded-xl py-1 text-xs font-medium transition-colors
					{active
					? 'text-sidebar-accent-foreground bg-sidebar-accent'
					: 'text-sidebar-foreground hover:text-sidebar-accent-foreground'}"
			>
				<item.icon class="w-5 h-5 shrink-0" />
				<span class="truncate">{item.label}</span>
			</a>
		{/each}

		<Button
			type="button"
			variant="ghost"
			onclick={() => (overflowOpen = true)}
			aria-label={m.nav_more_options()}
			class="h-auto flex-1 flex-col gap-1 rounded-xl py-1 text-xs text-sidebar-foreground hover:bg-sidebar-accent/50 hover:text-sidebar-accent-foreground"
		>
			<EllipsisVertical class="w-5 h-5 shrink-0" />
			<span>{m.nav_more()}</span>
		</Button>
	</div>
</nav>

<DockOverflowDialog bind:open={overflowOpen} items={overflowItems} />
