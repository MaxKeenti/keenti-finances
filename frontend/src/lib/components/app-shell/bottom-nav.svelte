<script lang="ts">
	import { page } from '$app/stores';
	import { LayoutDashboard, ArrowLeftRight, CreditCard, HandCoins, Layers, Users, LogOut } from '@lucide/svelte';

	const navItems = [
		{ href: '/', label: 'Dashboard', icon: LayoutDashboard },
		{ href: '/categories', label: 'Categories', icon: Layers },
		{ href: '/contacts', label: 'Contacts', icon: Users },
		{ href: '/transactions', label: 'Transactions', icon: ArrowLeftRight },
		{ href: '/subscriptions', label: 'Subscriptions', icon: CreditCard },
		{ href: '/debts', label: 'Debts', icon: HandCoins },
		{ href: '/logout', label: 'Logout', icon: LogOut }
	];
</script>

<nav class="sm:hidden fixed bottom-0 left-0 right-0 bg-sidebar border-t border-sidebar-border z-20 flex overflow-x-auto">
	{#each navItems as item}
		{@const active = $page.url.pathname === item.href}
		<a
			href={item.href}
			class="min-w-[64px] flex-1 flex flex-col items-center justify-center gap-1 py-2 text-xs font-medium transition-colors
				{active
				? 'text-sidebar-primary'
				: 'text-sidebar-foreground hover:text-sidebar-primary'}"
			aria-label={item.label}
		>
			<item.icon class="w-5 h-5 shrink-0" />
			<span class="truncate">{item.label}</span>
		</a>
	{/each}
</nav>
