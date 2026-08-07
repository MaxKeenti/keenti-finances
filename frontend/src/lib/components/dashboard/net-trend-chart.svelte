<script lang="ts">
	import { scaleLinear } from 'd3-scale';
	import * as Card from '$lib/components/ui/card';
	import { mxnFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';

	type MonthlySummary = { month: number; ingress: number; egress: number };

	let { monthly, year, locale }: { monthly: MonthlySummary[]; year: number; locale: string } = $props();

	const width = 560;
	const height = 180;
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
		const netValues = monthly.map((item) => item.ingress - item.egress);
		const minimum = Math.min(...netValues, 0);
		const maximum = Math.max(...netValues, 0);
		const domainPadding = Math.max(Math.abs(maximum - minimum) * 0.1, 100);
		const x = scaleLinear().domain([1, 12]).range([0, innerWidth]);
		const y = scaleLinear().domain([minimum - domainPadding, maximum + domainPadding]).range([innerHeight, 0]);
		const points = monthly.map((item) => ({ month: item.month, x: x(item.month), y: y(item.ingress - item.egress) }));
		return {
			y,
			yTicks: y.ticks(4),
			zero: y(0),
			points,
			path: points.length ? `M ${points.map((point) => `${point.x},${point.y}`).join(' L ')}` : '',
		};
	});
</script>

<Card.Root>
	<Card.Header>
		<Card.Title>{m.dashboard_monthly_net_trend()}</Card.Title>
		<Card.Description>{m.dashboard_monthly_net_trend_description({ year })}</Card.Description>
	</Card.Header>
	<Card.Content>
		{#if empty}<p class="py-2 text-center text-sm text-muted-foreground">{m.dashboard_no_transactions()}</p>{/if}
		<div class="overflow-x-auto">
			<svg width={width} height={height} role="img" aria-label={m.dashboard_chart_monthly_net_trend()}>
				<g transform="translate({padding.left},{padding.top})">
					<line x1="0" y1={chart.zero} x2={innerWidth} y2={chart.zero} class="stroke-current" stroke-opacity="0.3" stroke-dasharray="4,2" />
					{#each chart.yTicks as tick}
						<line x1="0" y1={chart.y(tick)} x2={innerWidth} y2={chart.y(tick)} class="stroke-current" stroke-opacity="0.08" />
						<text x="-6" y={chart.y(tick)} text-anchor="end" dominant-baseline="middle" font-size="10" fill="currentColor" opacity="0.5">{fmt.format(tick)}</text>
					{/each}
					{#if chart.path}<path d={chart.path} fill="none" class="stroke-indigo-500" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" />{/if}
					{#each chart.points as point}<circle cx={point.x} cy={point.y} r="3" class="fill-indigo-500" />{/each}
					<line x1="0" y1={innerHeight} x2={innerWidth} y2={innerHeight} class="stroke-current" stroke-opacity="0.2" />
					{#each chart.points as point}
						<text x={point.x} y={innerHeight + 16} text-anchor="middle" font-size="11" fill="currentColor" opacity="0.6">{monthNames[point.month - 1]()}</text>
					{/each}
				</g>
			</svg>
		</div>
	</Card.Content>
</Card.Root>
