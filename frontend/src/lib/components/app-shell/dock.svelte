<script lang="ts">
	import { page } from '$app/stores';
	import { untrack } from 'svelte';
	import {
		LayoutDashboard,
		ArrowLeftRight,
		PackageOpen,
		CreditCard,
		HandCoins,
		Landmark,
		Settings,
		Layers,
		Users,
		Trash2,
		LogOut,
		EllipsisVertical
	} from '@lucide/svelte';
	import DockOverflowDialog from './dock-overflow-dialog.svelte';
	import { Separator } from '$lib/components/ui/separator';
	import { Button } from '$lib/components/ui/button';
	import { m } from '$lib/paraglide/messages.js';
	import type { Component } from 'svelte';

	type NavItem = {
		href: string;
		label: string;
		icon: Component<{ class?: string }>;
		activeHrefs?: string[];
	};

	const dockNavItems: NavItem[] = [
		{ href: '/', label: m.nav_dashboard(), icon: LayoutDashboard },
		{ href: '/transactions', label: m.nav_transactions(), icon: ArrowLeftRight },
		{ href: '/boxes', label: m.nav_boxes(), icon: PackageOpen },
		{ href: '/accounts', label: 'Accounts', icon: Landmark },
		{ href: '/subscriptions', label: m.nav_subscriptions(), icon: CreditCard },
		{ href: '/debts', label: m.nav_debts(), icon: HandCoins },
		{
			href: '/settings',
			label: m.nav_settings(),
			icon: Settings,
			activeHrefs: ['/categories', '/contacts', '/trash'],
		}
	];
	const managementNavItems: NavItem[] = [
		{ href: '/categories', label: m.nav_categories(), icon: Layers },
		{ href: '/contacts', label: m.nav_contacts(), icon: Users },
		{ href: '/trash', label: m.nav_trash(), icon: Trash2 },
	];
	const menuItems: NavItem[] = [...dockNavItems, ...managementNavItems];
	const defaultPinnedHrefs = ['/transactions', '/subscriptions', '/debts'];

	function normalizePinnedHrefs(csv: string | undefined) {
		const allowed = new Set(dockNavItems.map((item) => item.href));
		const requested = (csv ?? '')
			.split(',')
			.filter((href) => allowed.has(href));
		return [...requested, ...defaultPinnedHrefs]
			.filter((href, index, all) => all.indexOf(href) === index)
			.slice(0, 3);
	}

	const pinnedHrefs = $derived(
		normalizePinnedHrefs($page.data.preferences?.mobilePinnedNavItems as string | undefined),
	);
	const pinnedItems = $derived(
		pinnedHrefs
			.map((href) => dockNavItems.find((item) => item.href === href))
			.filter((item): item is NavItem => Boolean(item)),
	);
	const dockMagnification = $derived($page.data.preferences?.dockMagnification ?? true);

	const RECENTS_KEY = 'keenti.nav.recents';

	let overflowOpen = $state(false);
	let recentsLoaded = $state(false);
	let recentHrefs = $state<string[]>([]);

	function isPathMatch(href: string, pathname: string) {
		return href === '/' ? pathname === '/' : pathname === href || pathname.startsWith(`${href}/`);
	}

	function isItemActiveForPath(item: NavItem, pathname: string) {
		return (
			isPathMatch(item.href, pathname) ||
			(item.activeHrefs?.some((href) => isPathMatch(href, pathname)) ?? false)
		);
	}

	function isActive(item: NavItem) {
		return isItemActiveForPath(item, $page.url.pathname);
	}

	function matchingNavItem(pathname: string) {
		return dockNavItems.find((item) => isItemActiveForPath(item, pathname));
	}

	function readStoredRecents() {
		try {
			const value = JSON.parse(localStorage.getItem(RECENTS_KEY) ?? '[]');
			return Array.isArray(value)
				? value.filter((href): href is string => dockNavItems.some((item) => item.href === href)).slice(0, 4)
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
			.map((href) => dockNavItems.find((item) => item.href === href))
			.filter((item): item is NavItem => Boolean(item))
			.filter((item) => !isActive(item))
			.slice(0, 3),
	);

	// macOS-style dock magnification: each icon's width follows a cosine bell
	// centered on the cursor, so neighbours swell too and push each other apart
	// while their bottoms stay anchored to the shelf. Width (not transform) is
	// animated so siblings genuinely displace, like the real dock. Mouse-only —
	// the mobile dock is a separate, non-magnified layout.
	const MAGNIFY = 0.6; // extra scale at the cursor (1x -> 1.6x)
	const MAGNIFY_RANGE = 120; // px of influence to each side of the cursor
	let dockEl = $state<HTMLElement | undefined>(undefined);
	let magnifyRaf = 0;

	function magnifyDock(e: PointerEvent) {
		if (!dockMagnification || e.pointerType !== 'mouse' || !dockEl) return;
		const x = e.clientX;
		cancelAnimationFrame(magnifyRaf);
		magnifyRaf = requestAnimationFrame(() => {
			if (!dockEl) return;
			for (const el of dockEl.querySelectorAll<HTMLElement>('[data-dock-icon]')) {
				const rect = el.getBoundingClientRect();
				const t = Math.min(Math.abs(x - rect.left - rect.width / 2) / MAGNIFY_RANGE, 1);
				const scale = 1 + MAGNIFY * Math.cos((t * Math.PI) / 2) ** 2;
				el.style.setProperty('--scale', scale.toFixed(3));
			}
		});
	}

	function resetDockMagnify() {
		cancelAnimationFrame(magnifyRaf);
		if (!dockEl) return;
		for (const el of dockEl.querySelectorAll<HTMLElement>('[data-dock-icon]')) {
			el.style.removeProperty('--scale');
		}
	}

	$effect(() => {
		if (!dockMagnification) resetDockMagnify();
	});

	$effect(() => {
		function handleRecentsCleared() {
			recentHrefs = [];
		}

		window.addEventListener('keenti:nav-recents-cleared', handleRecentsCleared);
		return () => {
			window.removeEventListener('keenti:nav-recents-cleared', handleRecentsCleared);
			cancelAnimationFrame(magnifyRaf);
		};
	});
</script>

<nav
	bind:this={dockEl}
	onpointermove={magnifyDock}
	onpointerleave={resetDockMagnify}
	class="fixed inset-x-3 bottom-[calc(env(safe-area-inset-bottom)+0.75rem)] z-40 flex justify-center sm:inset-x-auto sm:left-1/2 sm:-translate-x-1/2"
	aria-label={m.nav_main()}
>
	<!-- Desktop: macOS-style magnifying dock -->
	{#snippet dockIcon(href: string, label: string, Icon: NavItem['icon'], active: boolean, small = false)}
		<a
			data-dock-icon
			{href}
			aria-label={label}
			class="group relative flex shrink-0 flex-col items-center justify-end outline-none transition-[width] duration-150 ease-out will-change-[width]
				{small ? 'w-[calc(var(--scale,1)*36px)]' : 'w-[calc(var(--scale,1)*44px)]'}"
		>
			<!-- Floating name label above the magnified icon -->
			<span
				aria-hidden="true"
				class="pointer-events-none absolute bottom-full left-1/2 mb-2.5 -translate-x-1/2 scale-90 whitespace-nowrap rounded-lg border border-sidebar-border/60 bg-popover/90 px-2.5 py-1 text-xs font-medium text-popover-foreground opacity-0 shadow-lg backdrop-blur-md transition-all duration-150 group-hover:scale-100 group-hover:opacity-100"
			>
				{label}
				<span
					class="absolute left-1/2 top-full -mt-1 size-2 -translate-x-1/2 rotate-45 rounded-[2px] border-b border-r border-sidebar-border/60 bg-popover/90"
				></span>
			</span>
			<div
				class="flex aspect-square w-full items-center justify-center rounded-[28%] border transition-shadow group-hover:shadow-md group-active:brightness-95 group-focus-visible:ring-2 group-focus-visible:ring-sidebar-ring
					{active
					? 'border-sidebar-border bg-sidebar-accent text-sidebar-accent-foreground shadow-md'
					: 'border-sidebar-border/50 bg-background/50 text-sidebar-foreground group-hover:text-sidebar-accent-foreground'}"
			>
				<Icon class="size-1/2 shrink-0" />
			</div>
			{#if active}
				<!-- Running-app dot -->
				<span class="absolute -bottom-[5px] left-1/2 size-1 -translate-x-1/2 rounded-full bg-sidebar-foreground/60"></span>
			{/if}
		</a>
	{/snippet}

	<div
		class="hidden items-end gap-1.5 rounded-3xl border border-sidebar-border/70 bg-sidebar/80 px-3 pb-2 pt-2.5 shadow-2xl shadow-black/10 backdrop-blur-xl sm:flex"
	>
		{#each dockNavItems as item}
			{@render dockIcon(item.href, item.label, item.icon, isActive(item))}
		{/each}

		{#if recentItems.length > 0}
			<Separator orientation="vertical" class="mx-0.5 h-7 self-center bg-sidebar-border" />
			{#each recentItems as item}
				{@render dockIcon(item.href, item.label, item.icon, false, true)}
			{/each}
		{/if}

		<Separator orientation="vertical" class="mx-0.5 h-7 self-center bg-sidebar-border" />

		{@render dockIcon('/logout', m.nav_logout(), LogOut, false)}
	</div>

	<!-- Mobile: 3 pinned + overflow menu button -->
	<div
		class="flex w-full items-center gap-1 rounded-2xl border border-sidebar-border/70 bg-sidebar/90 px-2 py-2 shadow-2xl shadow-black/15 backdrop-blur-xl sm:hidden"
	>
		{#each pinnedItems as item}
			{@const active = isActive(item)}
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

<DockOverflowDialog bind:open={overflowOpen} items={menuItems} />
