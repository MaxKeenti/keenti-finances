<script lang="ts">
	/**
	 * Chooses the Viewing Currency of a summary screen (ADR-0024).
	 *
	 * Plain links rather than client state: the server reads `?currency=` and
	 * every figure on the page is loaded in that one currency, so there is no
	 * moment when totals from two currencies share the screen. Hidden when the
	 * User holds a single currency, which is where every User starts.
	 */
	import { page } from '$app/state';
	import { currencyName } from '$lib/currency-labels';
	import { m } from '$lib/paraglide/messages.js';

	let { currencies, current }: { currencies: readonly string[]; current: string } = $props();

	function hrefFor(code: string): string {
		const url = new URL(page.url);
		url.searchParams.set('currency', code);
		return `${url.pathname}${url.search}`;
	}
</script>

{#if currencies.length > 1}
	<nav aria-label={m.currency_switcher_label()} class="inline-flex rounded-lg border bg-muted/40 p-0.5">
		{#each currencies as code (code)}
			<a
				href={hrefFor(code)}
				data-sveltekit-noscroll
				aria-current={code === current ? 'page' : undefined}
				title={currencyName(code)}
				class="rounded-md px-3 py-1 text-sm font-medium tabular-nums transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring {code === current
					? 'bg-background text-foreground shadow-sm'
					: 'text-muted-foreground hover:text-foreground'}"
			>
				{code}
			</a>
		{/each}
	</nav>
{/if}
