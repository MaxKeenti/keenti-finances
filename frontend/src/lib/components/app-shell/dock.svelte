<script lang="ts">
	import { page } from '$app/stores';
	import {
		LayoutDashboard,
		ArrowLeftRight,
		PackageOpen,
		CreditCard,
		HandCoins,
		CalendarRange,
		Landmark,
		Settings,
		Layers,
		Users,
		Trash2,
		LayoutGrid,
		EllipsisVertical
	} from '@lucide/svelte';
	import DockOverflowDialog from './dock-overflow-dialog.svelte';
	import DockTile from './dock-tile.svelte';
	import { DOCK_SURFACE } from './dock-surface';
	import { dockSessionStore } from './dock-session.svelte';
	import { Button } from '$lib/components/ui/button';
	import { m } from '$lib/paraglide/messages.js';
	import { DASHBOARD_HREF, resolveMobileNavHrefs } from '$lib/navigation';
	import { cn } from '$lib/utils';
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
		{ href: '/accounts', label: m.nav_accounts(), icon: Landmark },
		{ href: '/subscriptions', label: m.nav_subscriptions(), icon: CreditCard },
		{ href: '/debts', label: m.nav_debts(), icon: HandCoins },
		{ href: '/planning', label: m.nav_planning(), icon: CalendarRange },
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

	// Dashboard leads; the User's own pins follow unchanged. See `navigation.ts`
	// for what the preference migration does and does not touch.
	const pinnedHrefs = $derived(
		resolveMobileNavHrefs($page.data.preferences?.mobilePinnedNavItems as string | undefined),
	);
	const pinnedItems = $derived(
		pinnedHrefs
			.map((href) => menuItems.find((item) => item.href === href))
			.filter((item): item is NavItem => Boolean(item)),
	);
	const dockMagnification = $derived($page.data.preferences?.dockMagnification ?? true);

	let overflowOpen = $state(false);

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

	// An area gets its running dot once it has been opened this session.
	// Dashboard always has one, like Finder.
	$effect(() => {
		const current = dockNavItems.find(isActive);
		if (current) dockSessionStore.open(current.href);
	});

	function isRunning(item: NavItem) {
		return (
			item.href === DASHBOARD_HREF || isActive(item) || dockSessionStore.opened.includes(item.href)
		);
	}

	// macOS-style dock magnification: each icon's width follows a cosine bell
	// centered on the cursor, so neighbours swell too and push each other apart
	// while their bottoms stay anchored to the shelf. Width (not transform) is
	// animated so siblings genuinely displace, like the real dock. Mouse-only —
	// the mobile dock is a separate, non-magnified layout.
	const MAGNIFY = 0.7; // extra scale at the cursor (1x -> 1.7x)
	const MAGNIFY_RANGE = 130; // px of influence to each side of the cursor
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

	$effect(() => () => cancelAnimationFrame(magnifyRaf));
</script>

<nav
	bind:this={dockEl}
	onpointermove={magnifyDock}
	onpointerleave={resetDockMagnify}
	class="fixed inset-x-3 bottom-[calc(env(safe-area-inset-bottom)+0.75rem)] z-40 flex justify-center sm:inset-x-auto sm:left-1/2 sm:-translate-x-1/2"
	aria-label={m.nav_main()}
>
	<!-- Desktop: macOS-style magnifying dock. The pill keeps a fixed height, so
	     magnified tiles rise above the shelf instead of stretching it. -->
	<div
		class={cn(
			DOCK_SURFACE,
			'hidden h-16 max-w-[calc(100vw-2rem)] items-end gap-2 rounded-3xl px-3.5 pb-2.5 sm:flex md:h-18',
		)}
	>
		{#each dockNavItems as item (item.href)}
			<DockTile label={item.label} href={item.href} active={isActive(item)} running={isRunning(item)}>
				<item.icon class="size-1/2 shrink-0" />
			</DockTile>
		{/each}

		<div aria-hidden="true" class="h-8 w-px shrink-0 self-center bg-border md:h-9"></div>

		<!-- Everything else, including the management areas and Logout -->
		<DockTile label={m.nav_more()} onclick={() => (overflowOpen = true)}>
			<LayoutGrid class="size-1/2 shrink-0" />
		</DockTile>
	</div>

	<!-- Mobile: 3 pinned + overflow menu button -->
	<div class={cn(DOCK_SURFACE, 'flex w-full items-center gap-1 rounded-2xl px-2 py-2 sm:hidden')}>
		{#each pinnedItems as item}
			{@const active = isActive(item)}
			<a
				href={item.href}
				aria-label={item.label}
				class="flex min-w-0 flex-1 flex-col items-center justify-center gap-1 rounded-xl py-1 text-xs font-medium transition-colors
					{active
					? 'text-sidebar-accent-foreground bg-sidebar-accent'
					: 'text-sidebar-foreground hover:text-sidebar-accent-foreground'}"
			>
				<item.icon class="w-5 h-5 shrink-0" />
				<span class={item.href === '/debts' ? 'w-full whitespace-normal text-center leading-tight' : 'w-full truncate text-center'}>{item.label}</span>
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
