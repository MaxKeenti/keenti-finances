<script lang="ts">
	import { page } from '$app/stores';
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
	import { m } from '$lib/paraglide/messages.js';

	const allNavItems = [
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

	let overflowOpen = $state(false);
</script>

<nav
	class="flex items-center justify-center border-t border-sidebar-border bg-sidebar px-2 shrink-0"
	aria-label={m.nav_main()}
>
	<!-- Desktop: all items centered -->
	<div class="hidden sm:flex items-center gap-1 py-2">
		{#each allNavItems as item}
			{@const active = $page.url.pathname === item.href}
			<a
				href={item.href}
				title={item.label}
				aria-label={item.label}
				class="relative group flex items-center justify-center w-10 h-10 rounded-lg transition-colors
					{active
					? 'bg-sidebar-accent text-sidebar-accent-foreground'
					: 'text-sidebar-foreground hover:bg-sidebar-accent/50 hover:text-sidebar-accent-foreground'}"
			>
				<item.icon class="w-5 h-5 shrink-0" />
				<span
					class="pointer-events-none absolute bottom-full mb-2 left-1/2 -translate-x-1/2 whitespace-nowrap rounded bg-popover text-popover-foreground text-xs px-2 py-1 opacity-0 group-hover:opacity-100 transition-opacity shadow-md border border-border"
				>
					{item.label}
				</span>
			</a>
		{/each}

		<div class="w-px h-6 bg-sidebar-border mx-1" aria-hidden="true"></div>

		<a
			href="/logout"
			title={m.nav_logout()}
			aria-label={m.nav_logout()}
			class="relative group flex items-center justify-center w-10 h-10 rounded-lg transition-colors text-sidebar-foreground hover:bg-sidebar-accent/50 hover:text-sidebar-accent-foreground"
		>
			<LogOut class="w-5 h-5 shrink-0" />
			<span
				class="pointer-events-none absolute bottom-full mb-2 left-1/2 -translate-x-1/2 whitespace-nowrap rounded bg-popover text-popover-foreground text-xs px-2 py-1 opacity-0 group-hover:opacity-100 transition-opacity shadow-md border border-border"
			>
				{m.nav_logout()}
			</span>
		</a>
	</div>

	<!-- Mobile: 3 pinned + overflow menu button -->
	<div class="flex sm:hidden items-center w-full py-2 px-2">
		{#each pinnedItems as item}
			{@const active = $page.url.pathname === item.href}
			<a
				href={item.href}
				aria-label={item.label}
				class="flex-1 flex flex-col items-center justify-center gap-1 py-1 rounded-lg transition-colors text-xs font-medium
					{active
					? 'text-sidebar-accent-foreground bg-sidebar-accent'
					: 'text-sidebar-foreground hover:text-sidebar-accent-foreground'}"
			>
				<item.icon class="w-5 h-5 shrink-0" />
				<span class="truncate">{item.label}</span>
			</a>
		{/each}

		<button
			onclick={() => (overflowOpen = true)}
			aria-label={m.nav_more_options()}
			class="flex-1 flex flex-col items-center justify-center gap-1 py-1 rounded-lg transition-colors text-xs font-medium text-sidebar-foreground hover:text-sidebar-accent-foreground"
		>
			<EllipsisVertical class="w-5 h-5 shrink-0" />
			<span>{m.nav_more()}</span>
		</button>
	</div>
</nav>

<DockOverflowDialog bind:open={overflowOpen} items={overflowItems} />
