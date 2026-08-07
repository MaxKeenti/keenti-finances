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
	<Card.Root class={`h-full transition-colors hover:bg-muted/40${archived ? ' opacity-70' : ''}`}>
		<Card.Header class="flex-row items-start justify-between gap-3">
			<div class="min-w-0">
				<Card.Description>{kindLabel}{archived ? ` · ${m.account_archived()}` : ''}</Card.Description>
				<Card.Title class="mt-1 truncate">{account.name}</Card.Title>
				<p class:text-destructive={account.kind === 'CREDIT' && account.balance < 0} class="mt-2 text-xl font-semibold tabular-nums">{balanceLabel}</p>
			</div>
			<div class="flex size-10 shrink-0 items-center justify-center rounded-lg bg-muted text-muted-foreground">
				<Icon class="size-5" aria-hidden="true" />
			</div>
		</Card.Header>
	</Card.Root>
</a>
