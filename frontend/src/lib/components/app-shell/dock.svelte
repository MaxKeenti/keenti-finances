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
	import * as Tooltip from '$lib/components/ui/tooltip';
	import { Separator } from '$lib/components/ui/separator';
	import { Button } from '$lib/components/ui/button';
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
	<Tooltip.Provider delayDuration={150}>
		<div class="hidden sm:flex items-center gap-1 py-2">
			{#each allNavItems as item}
				{@const active = $page.url.pathname === item.href}
				<Tooltip.Root>
					<Tooltip.Trigger>
						{#snippet child({ props })}
							{@const { type: _triggerType, ...triggerProps } = props}
							<a
								{...triggerProps}
								href={item.href}
								aria-label={item.label}
								class="flex items-center justify-center w-10 h-10 rounded-lg transition-colors
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

			<Separator orientation="vertical" class="h-6 bg-sidebar-border mx-1" />

			<Tooltip.Root>
				<Tooltip.Trigger>
					{#snippet child({ props })}
						{@const { type: _triggerType, ...triggerProps } = props}
						<a
							{...triggerProps}
							href="/logout"
							aria-label={m.nav_logout()}
							class="flex items-center justify-center w-10 h-10 rounded-lg transition-colors text-sidebar-foreground hover:bg-sidebar-accent/50 hover:text-sidebar-accent-foreground"
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

		<Button
			type="button"
			variant="ghost"
			onclick={() => (overflowOpen = true)}
			aria-label={m.nav_more_options()}
			class="h-auto flex-1 flex-col gap-1 py-1 text-xs text-sidebar-foreground hover:bg-transparent hover:text-sidebar-accent-foreground"
		>
			<EllipsisVertical class="w-5 h-5 shrink-0" />
			<span>{m.nav_more()}</span>
		</Button>
	</div>
</nav>

<DockOverflowDialog bind:open={overflowOpen} items={overflowItems} />
