<script lang="ts">
	/**
	 * States which formula produced the Net Balance on screen (decision D1).
	 *
	 * The explanation comes from the authoritative `active` boolean on
	 * `GET /api/accounts/status`, never from comparing totals. When that read
	 * fails the total keeps its place — it loaded independently and is still
	 * true — and only its explanation becomes unavailable, with a Retry.
	 */
	import { SectionUnavailable } from '$lib/components/section-status';
	import { netBalanceSource, type TrackingStatus } from '$lib/balance-presentation';
	import { m } from '$lib/paraglide/messages.js';

	let { tracking }: { tracking: TrackingStatus } = $props();

	const source = $derived(netBalanceSource(tracking));
</script>

{#if source === 'unknown'}
	<SectionUnavailable
		title={m.section_tracking_unavailable()}
		description={m.balance_source_unknown()}
		compact
	/>
{:else}
	<p class="text-xs text-muted-foreground">
		{source === 'accounts' ? m.balance_source_accounts() : m.balance_source_transactions()}
	</p>
{/if}
