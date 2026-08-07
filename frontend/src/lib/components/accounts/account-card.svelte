<script lang="ts">
	import Banknote from '@lucide/svelte/icons/banknote';
	import CreditCard from '@lucide/svelte/icons/credit-card';
	import Landmark from '@lucide/svelte/icons/landmark';
	import PiggyBank from '@lucide/svelte/icons/piggy-bank';
	import WalletCards from '@lucide/svelte/icons/wallet-cards';
	import * as Card from '$lib/components/ui/card';
	import { m } from '$lib/paraglide/messages.js';
	import type { Account } from './types';

	let {
		account,
		kindLabel,
		balanceLabel,
		archived = false,
	}: { account: Account; kindLabel: string; balanceLabel: string; archived?: boolean } = $props();

	const Icon = $derived(
		account.kind === 'CREDIT'
			? CreditCard
			: account.kind === 'SAVINGS'
				? PiggyBank
				: account.kind === 'CASH'
					? Banknote
					: account.kind === 'CHECKING'
						? Landmark
						: WalletCards,
	);
</script>

<a class="rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" href={`/accounts/${account.id}`}>
	<Card.Root
		class={`h-full bg-gradient-to-br from-[oklch(0.97_0.025_var(--account-hue))] to-card transition-colors hover:from-[oklch(0.95_0.045_var(--account-hue))] dark:from-[oklch(0.27_0.035_var(--account-hue))] dark:hover:from-[oklch(0.3_0.05_var(--account-hue))]${archived ? ' opacity-70' : ''}`}
		style={`--account-hue: ${account.hue}`}
	>
		<Card.Header class="flex-row items-start justify-between gap-3">
			<div class="min-w-0">
				<Card.Description>{kindLabel}{archived ? ` · ${m.account_archived()}` : ''}</Card.Description>
				<Card.Title class="mt-1 truncate">{account.name}</Card.Title>
				<p class:text-destructive={account.kind === 'CREDIT' && account.balance < 0} class="mt-2 text-xl font-semibold tabular-nums">{balanceLabel}</p>
			</div>
			<div class="flex size-10 shrink-0 items-center justify-center rounded-lg bg-[oklch(0.88_0.12_var(--account-hue))] text-[oklch(0.32_0.08_var(--account-hue))] shadow-sm ring-1 ring-black/5 dark:bg-[oklch(0.38_0.1_var(--account-hue))] dark:text-[oklch(0.9_0.05_var(--account-hue))]">
				<Icon class="size-5" aria-hidden="true" />
			</div>
		</Card.Header>
	</Card.Root>
</a>
