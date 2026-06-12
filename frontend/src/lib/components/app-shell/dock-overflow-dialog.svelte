<script lang="ts">
	import { page } from '$app/stores';
	import * as Dialog from '$lib/components/ui/dialog/index.js';
	import { LogOut } from '@lucide/svelte';
	import { m } from '$lib/paraglide/messages.js';
	import type { Component } from 'svelte';

	interface NavItem {
		href: string;
		label: string;
		icon: Component<{ class?: string }>;
	}

	let {
		open = $bindable(false),
		items
	}: {
		open: boolean;
		items: NavItem[];
	} = $props();
</script>

<Dialog.Root bind:open>
	<Dialog.Content class="sm:max-w-xs">
		<Dialog.Header>
			<Dialog.Title>{m.nav_title()}</Dialog.Title>
		</Dialog.Header>
		<nav class="flex flex-col gap-1 py-2">
			{#each items as item}
				{@const active = $page.url.pathname === item.href}
				<a
					href={item.href}
					onclick={() => (open = false)}
					class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors
						{active
						? 'bg-sidebar-accent text-sidebar-accent-foreground'
						: 'text-sidebar-foreground hover:bg-sidebar-accent/50 hover:text-sidebar-accent-foreground'}"
				>
					<item.icon class="w-5 h-5 shrink-0" />
					{item.label}
				</a>
			{/each}

			<div class="w-full h-px bg-sidebar-border my-1" aria-hidden="true"></div>

			<a
				href="/logout"
				onclick={() => (open = false)}
				class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors text-sidebar-foreground hover:bg-sidebar-accent/50 hover:text-sidebar-accent-foreground"
			>
				<LogOut class="w-5 h-5 shrink-0" />
				{m.nav_logout()}
			</a>
		</nav>
	</Dialog.Content>
</Dialog.Root>
