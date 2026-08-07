<script lang="ts">
	import { scaleBand, scaleLinear } from 'd3-scale';
	import * as Card from '$lib/components/ui/card';
	import { mxnFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';

	type MonthlySummary = { month: number; ingress: number; egress: number };

	let { monthly, year, locale }: { monthly: MonthlySummary[]; year: number; locale: string } = $props();

	const width = 560;
	const height = 220;
	const padding = { top: 10, right: 10, bottom: 40, left: 70 };
	const innerWidth = width - padding.left - padding.right;
	const innerHeight = height - padding.top - padding.bottom;
	const monthNames = [
		m.month_jan, m.month_feb, m.month_mar, m.month_apr, m.month_may, m.month_jun,
		m.month_jul, m.month_aug, m.month_sep, m.month_oct, m.month_nov, m.month_dec,
	];
	const fmt = $derived(mxnFormatter(locale));
	const empty = $derived(monthly.every((item) => item.ingress === 0 && item.egress === 0));

	const chart = $derived.by(() => {
		const maxValue = Math.max(...monthly.flatMap((item) => [item.ingress, item.egress]), 1);
		const xOuter = scaleBand<number>().domain(monthly.map((item) => item.month)).range([0, innerWidth]).padding(0.2);
		const xInner = scaleBand<string>().domain(['ingress', 'egress']).range([0, xOuter.bandwidth()]).padding(0.05);
		const y = scaleLinear().domain([0, maxValue * 1.05]).range([innerHeight, 0]);
		return {
			y,
			yTicks: y.ticks(5),
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
	});
</script>

<Card.Root>
	<Card.Header>
		<Card.Title>{m.dashboard_monthly_income_expenses()}</Card.Title>
		<Card.Description>{year}</Card.Description>
	</Card.Header>
	<Card.Content>
		{#if empty}<p class="py-2 text-center text-sm text-muted-foreground">{m.dashboard_no_transactions()}</p>{/if}
		<div class="overflow-x-auto">
			<svg width={width} height={height} role="img" aria-label={m.dashboard_chart_monthly_income_expenses()}>
				<g transform="translate({padding.left},{padding.top})">
					{#each chart.yTicks as tick}
						<line x1="0" y1={chart.y(tick)} x2={innerWidth} y2={chart.y(tick)} class="stroke-current" stroke-opacity="0.1" stroke-dasharray="4,2" />
						<text x="-6" y={chart.y(tick)} text-anchor="end" dominant-baseline="middle" font-size="10" fill="currentColor" opacity="0.5">{fmt.format(tick)}</text>
					{/each}
					{#each chart.bars as bar}
						<rect x={bar.groupX + bar.ingressX} y={bar.ingressY} width={bar.bandWidth} height={bar.ingressHeight} class="fill-green-500" opacity="0.85" rx="2" />
						<rect x={bar.groupX + bar.egressX} y={bar.egressY} width={bar.bandWidth} height={bar.egressHeight} class="fill-red-500" opacity="0.85" rx="2" />
					{/each}
					<line x1="0" y1={innerHeight} x2={innerWidth} y2={innerHeight} class="stroke-current" stroke-opacity="0.2" />
					{#each chart.monthCenters as item}
						<text x={item.x} y={innerHeight + 16} text-anchor="middle" font-size="11" fill="currentColor" opacity="0.6">{monthNames[item.month - 1]()}</text>
					{/each}
				</g>
			</svg>
		</div>
		<div class="mt-2 flex gap-4 text-xs">
			<span class="flex items-center gap-1"><span class="inline-block size-3 rounded-sm bg-green-500"></span>{m.dashboard_income()}</span>
			<span class="flex items-center gap-1"><span class="inline-block size-3 rounded-sm bg-red-500"></span>{m.dashboard_expenses()}</span>
		</div>
	</Card.Content>
</Card.Root>
