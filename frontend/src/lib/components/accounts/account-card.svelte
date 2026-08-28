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

	// A Credit Financial Account renders its balance as an absolute figure, so
	// the number alone cannot say whether it is money owed or a credit in the
	// User's favour. Name the role above the amount rather than leaving colour
	// to carry the meaning on its own.
	const owed = $derived(account.kind === 'CREDIT' && account.balance < 0);
	const balanceRole = $derived(
		account.kind !== 'CREDIT'
			? m.account_balance_asset()
			: owed
				? m.account_debt_current()
				: m.account_credit_positive(),
	);
</script>

<a class="rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" href={`/accounts/${account.id}`}>
	<Card.Root
		class={`h-full gap-0 bg-gradient-to-br from-[oklch(0.97_0.025_var(--account-hue))] to-card py-0 transition-colors hover:from-[oklch(0.95_0.045_var(--account-hue))] dark:from-[oklch(0.27_0.035_var(--account-hue))] dark:hover:from-[oklch(0.3_0.05_var(--account-hue))]${archived ? ' opacity-70' : ''}`}
		style={`--account-hue: ${account.hue}`}
	>
		<div class="flex items-start gap-3 p-4">
			<div class="flex size-10 shrink-0 items-center justify-center rounded-lg bg-[oklch(0.88_0.12_var(--account-hue))] text-[oklch(0.32_0.08_var(--account-hue))] shadow-sm ring-1 ring-black/5 dark:bg-[oklch(0.38_0.1_var(--account-hue))] dark:text-[oklch(0.9_0.05_var(--account-hue))]">
				<Icon class="size-5" aria-hidden="true" />
			</div>
			<div class="min-w-0 flex-1">
				<p class="truncate font-medium leading-tight">{account.name}</p>
				<p class="truncate text-xs text-muted-foreground">
					{kindLabel}{archived ? ` · ${m.account_archived()}` : ''}
				</p>
				<p class="mt-3 text-xs text-muted-foreground">{balanceRole}</p>
				<p class="text-xl font-semibold tabular-nums {owed ? 'text-money-negative' : ''}">
					{balanceLabel}
				</p>
			</div>
		</div>
	</Card.Root>
</a>
