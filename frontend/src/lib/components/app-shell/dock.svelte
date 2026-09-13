<script lang="ts">
	import { page } from '$app/stores';
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
	import { resolveMobileNavHrefs } from '$lib/navigation';
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

	$effect(() => () => cancelAnimationFrame(magnifyRaf));
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
			class="group relative flex shrink-0 flex-col items-center justify-end outline-none transition-[width] duration-150 ease-out will-change-[width]
				{small
					? 'w-[max(calc(var(--scale,1)*36px),3.5rem)] md:w-[max(calc(var(--scale,1)*36px),4.5rem)]'
					: 'w-[max(calc(var(--scale,1)*44px),3.5rem)] md:w-[max(calc(var(--scale,1)*44px),4.5rem)]'}"
		>
			<div
				class="flex aspect-square items-center justify-center rounded-[28%] border transition-shadow group-hover:shadow-md group-active:brightness-95 group-focus-visible:ring-2 group-focus-visible:ring-sidebar-ring
					{small ? 'w-[calc(var(--scale,1)*36px)]' : 'w-[calc(var(--scale,1)*44px)]'}
					{active
					? 'border-sidebar-border bg-sidebar-accent text-sidebar-accent-foreground shadow-md'
					: 'border-sidebar-border/50 bg-background/50 text-sidebar-foreground group-hover:text-sidebar-accent-foreground'}"
			>
				<Icon class="size-1/2 shrink-0" />
			</div>
			<!-- The label is always visible: a name that only appears on hover is
			     unavailable to touch, to keyboard focus, and to anyone scanning the
			     bar, so the icon alone had to be recognized. It stays the link's
			     accessible name rather than being duplicated by an aria-label. -->
			<!-- Fixed height so a two-line name cannot shift its neighbours' icons. -->
			<span class="mt-1 line-clamp-2 h-[1.75rem] w-full overflow-hidden text-center text-[10px] font-medium leading-tight break-words
				{active ? 'text-sidebar-accent-foreground' : 'text-sidebar-foreground/80 group-hover:text-sidebar-accent-foreground'}">
				{label}
			</span>
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
