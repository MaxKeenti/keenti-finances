<script lang="ts">
	import { page } from '$app/stores';
	import { LayoutDashboard, ArrowLeftRight, CreditCard, HandCoins, Layers, Users, LogOut } from '@lucide/svelte';

	const navItems = [
		{ href: '/', label: 'Dashboard', icon: LayoutDashboard },
		{ href: '/categories', label: 'Categories', icon: Layers },
		{ href: '/contacts', label: 'Contacts', icon: Users },
		{ href: '/transactions', label: 'Transactions', icon: ArrowLeftRight },
		{ href: '/subscriptions', label: 'Subscriptions', icon: CreditCard },
		{ href: '/debts', label: 'Debts', icon: HandCoins }
	];
</script>

<aside class="hidden sm:flex flex-col fixed left-0 top-0 h-full w-60 bg-sidebar border-r border-sidebar-border z-20">
	<div class="px-6 py-5 border-b border-sidebar-border">
		<span class="font-heading text-lg font-semibold text-sidebar-foreground tracking-tight">
			Keenti Finances
		</span>
	</div>

	<nav class="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
		{#each navItems as item}
			{@const active = $page.url.pathname === item.href}
			<a
				href={item.href}
				class="flex items-center gap-3 px-3 py-2 rounded text-sm font-medium transition-colors
					{active
					? 'bg-sidebar-accent text-sidebar-accent-foreground'
					: 'text-sidebar-foreground hover:bg-sidebar-accent/50 hover:text-sidebar-accent-foreground'}"
			>
				<item.icon class="w-4 h-4 shrink-0" />
				{item.label}
			</a>
		{/each}
	</nav>

	<div class="px-3 py-4 border-t border-sidebar-border">
		<a
			href="/logout"
			class="flex items-center gap-3 px-3 py-2 rounded text-sm font-medium text-sidebar-foreground hover:bg-sidebar-accent/50 hover:text-sidebar-accent-foreground transition-colors w-full"
		>
			<LogOut class="w-4 h-4 shrink-0" />
			Logout
		</a>
	</div>
</aside>
