<script lang="ts">
	import { scaleBand, scaleLinear } from 'd3-scale';
	import * as Card from '$lib/components/ui/card';
	import { compactAmountFormatter, mxnFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
	import ChartFrame from './chart-frame.svelte';

	type MonthlySummary = { month: number; ingress: number; egress: number };

	let { monthly, year, locale }: { monthly: MonthlySummary[]; year: number; locale: string } = $props();

	const height = 240;
	const padding = { top: 8, right: 8, bottom: 28, left: 52 };
	const monthNames = [
		m.month_jan, m.month_feb, m.month_mar, m.month_apr, m.month_may, m.month_jun,
		m.month_jul, m.month_aug, m.month_sep, m.month_oct, m.month_nov, m.month_dec,
	];
	const fmt = $derived(mxnFormatter(locale));
	const axisFmt = $derived(compactAmountFormatter(locale));
	const empty = $derived(monthly.every((item) => item.ingress === 0 && item.egress === 0));

	let activeIndex = $state<number | null>(null);

	/** Bar with its data-end rounded and its base square on the axis. */
	function barPath(x: number, y: number, width: number, barHeight: number) {
		if (barHeight <= 0) return '';
		const r = Math.min(4, width / 2, barHeight);
		return `M${x},${y + barHeight} L${x},${y + r} Q${x},${y} ${x + r},${y} L${x + width - r},${y} Q${x + width},${y} ${x + width},${y + r} L${x + width},${y + barHeight} Z`;
	}

	function chartFor(innerWidth: number, innerHeight: number) {
		const maxValue = Math.max(...monthly.flatMap((item) => [item.ingress, item.egress]), 1);
		const xOuter = scaleBand<number>().domain(monthly.map((item) => item.month)).range([0, innerWidth]).padding(0.28);
		const xInner = scaleBand<string>().domain(['ingress', 'egress']).range([0, xOuter.bandwidth()]).padding(0.14);
		const y = scaleLinear().domain([0, maxValue * 1.05]).range([innerHeight, 0]).nice();
		return {
			y,
			yTicks: y.ticks(4),
			bars: monthly.map((item) => ({
				month: item.month,
				groupX: xOuter(item.month) ?? 0,
				ingressX: xInner('ingress') ?? 0,
				ingressY: y(item.ingress),
				ingressHeight: Math.max(innerHeight - y(item.ingress), 0),
				egressX: xInner('egress') ?? 0,
				egressY: y(item.egress),
				egressHeight: Math.max(innerHeight - y(item.egress), 0),
				bandWidth: xInner.bandwidth(),
			})),
			monthCenters: monthly.map((item) => ({
				month: item.month,
				x: (xOuter(item.month) ?? 0) + xOuter.bandwidth() / 2,
			})),
		};
	}
</script>

<Card.Root class="h-full">
	<Card.Header>
		<Card.Title>{m.dashboard_monthly_income_expenses()}</Card.Title>
		<Card.Description>{year}</Card.Description>
	</Card.Header>
	<Card.Content>
		{#if empty}
			<p class="py-8 text-center text-sm text-muted-foreground">{m.dashboard_no_transactions()}</p>
		{:else}
			<ChartFrame
				{height}
				{padding}
				columns={monthly.length}
				label={m.dashboard_chart_monthly_income_expenses()}
				bind:activeIndex
			>
				{#snippet plot({ innerWidth, innerHeight })}
					{@const chart = chartFor(innerWidth, innerHeight)}
					{#each chart.yTicks as tick}
						<line
							x1="0"
							y1={chart.y(tick)}
							x2={innerWidth}
							y2={chart.y(tick)}
							class="stroke-border"
						/>
						<text
							x="-8"
							y={chart.y(tick)}
							text-anchor="end"
							dominant-baseline="middle"
							font-size="10"
							class="fill-muted-foreground tabular-nums"
						>
							{axisFmt.format(tick)}
						</text>
					{/each}

					{#each chart.bars as bar, index}
						<g class="transition-opacity" opacity={activeIndex === null || activeIndex === index ? 1 : 0.4}>
							<path
								d={barPath(bar.groupX + bar.ingressX, bar.ingressY, bar.bandWidth, bar.ingressHeight)}
								class="fill-chart-income"
							/>
							<path
								d={barPath(bar.groupX + bar.egressX, bar.egressY, bar.bandWidth, bar.egressHeight)}
								class="fill-chart-expense"
							/>
						</g>
					{/each}

					<line x1="0" y1={innerHeight} x2={innerWidth} y2={innerHeight} class="stroke-border" />
					{#each chart.monthCenters as item, index}
						<text
							x={item.x}
							y={innerHeight + 16}
							text-anchor="middle"
							font-size="11"
							class={activeIndex === index ? 'fill-foreground' : 'fill-muted-foreground'}
						>
							{monthNames[item.month - 1]()}
						</text>
					{/each}
				{/snippet}

				{#snippet tooltip({ index })}
					{@const item = monthly[index]}
					<p class="mb-1 font-medium">{monthNames[item.month - 1]()} {year}</p>
					<p class="flex items-center gap-1.5 tabular-nums">
						<span class="inline-block size-2 rounded-full bg-chart-income"></span>
						<span class="text-muted-foreground">{m.dashboard_income()}</span>
						<span class="ml-auto">{fmt.format(item.ingress)}</span>
					</p>
					<p class="flex items-center gap-1.5 tabular-nums">
						<span class="inline-block size-2 rounded-full bg-chart-expense"></span>
						<span class="text-muted-foreground">{m.dashboard_expenses()}</span>
						<span class="ml-auto">{fmt.format(item.egress)}</span>
					</p>
				{/snippet}
			</ChartFrame>

			<div class="mt-3 flex gap-4 text-xs text-muted-foreground">
				<span class="flex items-center gap-1.5">
					<span class="inline-block size-2.5 rounded-full bg-chart-income"></span>{m.dashboard_income()}
				</span>
				<span class="flex items-center gap-1.5">
					<span class="inline-block size-2.5 rounded-full bg-chart-expense"></span>{m.dashboard_expenses()}
				</span>
			</div>
		{/if}
	</Card.Content>
</Card.Root>
