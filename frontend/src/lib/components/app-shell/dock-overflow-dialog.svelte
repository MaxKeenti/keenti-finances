<script lang="ts">
	import { page } from '$app/stores';
	import * as Dialog from '$lib/components/ui/dialog/index.js';
	import { Separator } from '$lib/components/ui/separator';
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
	<Dialog.Content
		class="!left-3 !top-3 !h-[calc(100dvh-1.5rem)] !w-[calc(100vw-1.5rem)] !max-w-none !translate-x-0 !translate-y-0 gap-5 rounded-2xl p-5 sm:!left-1/2 sm:!top-1/2 sm:!h-auto sm:!w-full sm:!max-w-md sm:!-translate-x-1/2 sm:!-translate-y-1/2"
	>
		<Dialog.Header>
			<Dialog.Title>{m.nav_title()}</Dialog.Title>
		</Dialog.Header>
		<nav class="grid grid-cols-3 gap-3">
			{#each items as item}
				{@const active = $page.url.pathname === item.href}
				<a
					href={item.href}
					onclick={() => (open = false)}
					class="flex aspect-square flex-col items-center justify-center gap-2 rounded-xl border px-2 text-center text-xs font-medium transition-colors
						{active
						? 'border-sidebar-accent bg-sidebar-accent text-sidebar-accent-foreground'
						: 'border-sidebar-border/70 bg-background/70 text-sidebar-foreground hover:bg-sidebar-accent/50 hover:text-sidebar-accent-foreground'}"
				>
					<item.icon class="h-6 w-6 shrink-0" />
					<span class="max-w-full truncate">{item.label}</span>
				</a>
			{/each}

			<Separator class="col-span-3 my-1 bg-sidebar-border" />

			<a
				href="/logout"
				onclick={() => (open = false)}
				class="flex aspect-square flex-col items-center justify-center gap-2 rounded-xl border border-sidebar-border/70 bg-background/70 px-2 text-center text-xs font-medium text-sidebar-foreground transition-colors hover:bg-sidebar-accent/50 hover:text-sidebar-accent-foreground"
			>
				<LogOut class="h-6 w-6 shrink-0" />
				<span class="max-w-full truncate">{m.nav_logout()}</span>
			</a>
		</nav>
	</Dialog.Content>
</Dialog.Root>
