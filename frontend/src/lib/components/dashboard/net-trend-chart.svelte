<script lang="ts">
	import { scaleLinear } from 'd3-scale';
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
	const nets = $derived(monthly.map((item) => item.ingress - item.egress));

	// Months after the last one with activity have no net — they are unrecorded,
	// not zero. Plotting them drew a flat line along the axis that read as
	// "you broke exactly even" for the rest of the year. The axis still shows
	// every month; only the series stops.
	const plotted = $derived(
		monthly.reduce(
			(last, item, index) => (item.ingress !== 0 || item.egress !== 0 ? index : last),
			-1,
		) + 1,
	);

	let activeIndex = $state<number | null>(null);

	function chartFor(innerWidth: number, innerHeight: number) {
		const recorded = nets.slice(0, plotted);
		const minimum = Math.min(...recorded, 0);
		const maximum = Math.max(...recorded, 0);
		const domainPadding = Math.max(Math.abs(maximum - minimum) * 0.1, 100);
		// Points sit at band centres so they line up with the hover bands and
		// with the bar chart's month columns beside it.
		const band = innerWidth / monthly.length;
		const x = (month: number) => band * (month - 1) + band / 2;
		const y = scaleLinear().domain([minimum - domainPadding, maximum + domainPadding]).range([innerHeight, 0]).nice();
		const points = monthly
			.slice(0, plotted)
			.map((item, index) => ({ month: item.month, x: x(item.month), y: y(nets[index]) }));
		return {
			y,
			yTicks: y.ticks(4),
			zero: y(0),
			points,
			// Every month keeps an axis label even where the series has stopped.
			labels: monthly.map((item) => ({ month: item.month, x: x(item.month) })),
			path: points.length ? `M ${points.map((point) => `${point.x},${point.y}`).join(' L ')}` : '',
		};
	}
</script>

<Card.Root class="h-full">
	<Card.Header>
		<Card.Title>{m.dashboard_monthly_net_trend()}</Card.Title>
		<Card.Description>{m.dashboard_monthly_net_trend_description({ year })}</Card.Description>
	</Card.Header>
	<Card.Content>
		{#if empty}
			<p class="py-8 text-center text-sm text-muted-foreground">{m.dashboard_no_transactions()}</p>
		{:else}
			<ChartFrame
				{height}
				{padding}
				columns={monthly.length}
				label={m.dashboard_chart_monthly_net_trend()}
				bind:activeIndex
			>
				{#snippet plot({ innerWidth, innerHeight })}
					{@const chart = chartFor(innerWidth, innerHeight)}
					{#each chart.yTicks as tick}
						<line x1="0" y1={chart.y(tick)} x2={innerWidth} y2={chart.y(tick)} class="stroke-border" />
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

					<!-- Zero line is the reference this series is read against, so it
					     is the one rule allowed to outrank the grid. -->
					<line
						x1="0"
						y1={chart.zero}
						x2={innerWidth}
						y2={chart.zero}
						class="stroke-muted-foreground"
						stroke-dasharray="3,3"
						stroke-opacity="0.5"
					/>

					{#if activeIndex !== null && activeIndex < chart.points.length}
						<line
							x1={chart.points[activeIndex].x}
							y1="0"
							x2={chart.points[activeIndex].x}
							y2={innerHeight}
							class="stroke-border"
						/>
					{/if}

					{#if chart.path}
						<path
							d={chart.path}
							fill="none"
							class="stroke-chart-net"
							stroke-width="2"
							stroke-linejoin="round"
							stroke-linecap="round"
						/>
					{/if}

					{#each chart.points as point, index}
						<circle
							cx={point.x}
							cy={point.y}
							r={activeIndex === index ? 5 : 3.5}
							class="fill-chart-net stroke-card transition-[r]"
							stroke-width="2"
						/>
					{/each}

					<line x1="0" y1={innerHeight} x2={innerWidth} y2={innerHeight} class="stroke-border" />
					{#each chart.labels as label, index}
						<text
							x={label.x}
							y={innerHeight + 16}
							text-anchor="middle"
							font-size="11"
							class={activeIndex === index ? 'fill-foreground' : 'fill-muted-foreground'}
						>
							{monthNames[label.month - 1]()}
						</text>
					{/each}
				{/snippet}

				{#snippet tooltip({ index })}
					<p class="mb-1 font-medium">{monthNames[monthly[index].month - 1]()} {year}</p>
					{#if index < plotted}
						<p class="tabular-nums {nets[index] < 0 ? 'text-money-negative' : 'text-money-positive'}">
							{fmt.format(nets[index])}
						</p>
					{:else}
						<p class="text-muted-foreground">{m.dashboard_no_transactions()}</p>
					{/if}
				{/snippet}
			</ChartFrame>
		{/if}
	</Card.Content>
</Card.Root>
