<script lang="ts">
	import { scaleBand, scaleLinear } from 'd3-scale';
	import * as Card from '$lib/components/ui/card';
	import { Button } from '$lib/components/ui/button';
	import { mxnFormatter } from '$lib/formatting';
	import { m } from '$lib/paraglide/messages.js';
	import type { PageData } from './$types';

	let { data }: { data: PageData } = $props();

	const MONTH_NAMES = [
		m.month_jan,
		m.month_feb,
		m.month_mar,
		m.month_apr,
		m.month_may,
		m.month_jun,
		m.month_jul,
		m.month_aug,
		m.month_sep,
		m.month_oct,
		m.month_nov,
		m.month_dec,
	];

	const fmt = $derived(mxnFormatter(data.preferences.locale));
	const mxn = (v: number) => fmt.format(v);

	const BAR_W = 560;
	const BAR_H = 220;
	const PAD = { top: 10, right: 10, bottom: 40, left: 70 };
	const innerW = BAR_W - PAD.left - PAD.right;
	const innerH = BAR_H - PAD.top - PAD.bottom;

	const LINE_W = 560;
	const LINE_H = 180;
	const LINE_PAD = { top: 10, right: 10, bottom: 40, left: 70 };
	const lineInnerW = LINE_W - LINE_PAD.left - LINE_PAD.right;
	const lineInnerH = LINE_H - LINE_PAD.top - LINE_PAD.bottom;

	type BarItem = {
		month: number;
		gx: number;
		ingressX: number;
		ingressY: number;
		ingressH: number;
		egressX: number;
		egressY: number;
		egressH: number;
		bandW: number;
	};

	const barChartData = $derived(() => {
		const monthly = data.summary.monthly;
		const maxVal = Math.max(...monthly.flatMap((m) => [m.ingress, m.egress]), 1);

		const xOuter = scaleBand<number>()
			.domain(monthly.map((m) => m.month))
			.range([0, innerW])
			.padding(0.2);

		const xInner = scaleBand<string>()
			.domain(['ingress', 'egress'])
			.range([0, xOuter.bandwidth()])
			.padding(0.05);

		const y = scaleLinear().domain([0, maxVal * 1.05]).range([innerH, 0]);
		const yTicks = y.ticks(5);
		const bw = xInner.bandwidth();

		const bars: BarItem[] = monthly.map((m) => ({
			month: m.month,
			gx: xOuter(m.month) ?? 0,
			ingressX: xInner('ingress') ?? 0,
			ingressY: y(m.ingress),
			ingressH: Math.max(innerH - y(m.ingress), 0),
			egressX: xInner('egress') ?? 0,
			egressY: y(m.egress),
			egressH: Math.max(innerH - y(m.egress), 0),
			bandW: bw,
		}));

		const monthCenters = monthly.map((m) => ({
			month: m.month,
			cx: (xOuter(m.month) ?? 0) + xOuter.bandwidth() / 2,
		}));

		return { bars, yTicks, y, monthCenters };
	});

	const lineChartData = $derived(() => {
		const monthly = data.summary.monthly;
		const nets = monthly.map((m) => m.ingress - m.egress);
		const minNet = Math.min(...nets, 0);
		const maxNet = Math.max(...nets, 0);
		const pad = Math.max(Math.abs(maxNet - minNet) * 0.1, 100);

		const x = scaleLinear().domain([1, 12]).range([0, lineInnerW]);
		const y = scaleLinear().domain([minNet - pad, maxNet + pad]).range([lineInnerH, 0]);

		const points = monthly.map((m) => ({
			cx: x(m.month),
			cy: y(m.ingress - m.egress),
		}));

		const pathD =
			points.length > 0 ? `M ${points.map((p) => `${p.cx},${p.cy}`).join(' L ')}` : '';

		const yTicks = y.ticks(4);
		const zero = y(0);

		const monthLabels = monthly.map((m) => ({ month: m.month, cx: x(m.month) }));

		return { points, pathD, y, yTicks, zero, monthLabels };
	});

	const bc = $derived(barChartData());
	const lc = $derived(lineChartData());

	const isEmpty = $derived(
		data.summary.monthly.every((m) => m.ingress === 0 && m.egress === 0),
	);

	const prevYear = $derived(data.year - 1);
	const nextYear = $derived(data.year + 1);
	const currentYear = new Date().getFullYear();
</script>

<div class="space-y-6 p-6">
	<!-- Summary Cards -->
	<div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
		<Card.Root>
			<Card.Header class="pb-2">
				<Card.Description>{m.dashboard_net_balance()}</Card.Description>
				<Card.Title class="text-2xl font-bold">{mxn(data.summary.netBalance)}</Card.Title>
			</Card.Header>
			<Card.Content>
				<p class="text-muted-foreground text-xs">{m.dashboard_net_balance_description()}</p>
			</Card.Content>
		</Card.Root>
		<Card.Root>
			<Card.Header class="pb-2">
				<Card.Description>{m.dashboard_total_income({ year: data.year })}</Card.Description>
				<Card.Title class="text-2xl font-bold text-green-600">{mxn(data.summary.totalIngress)}</Card.Title>
			</Card.Header>
			<Card.Content>
				<p class="text-muted-foreground text-xs">{m.dashboard_total_income_description()}</p>
			</Card.Content>
		</Card.Root>
		<Card.Root>
			<Card.Header class="pb-2">
				<Card.Description>{m.dashboard_total_expenses({ year: data.year })}</Card.Description>
				<Card.Title class="text-2xl font-bold text-red-600">{mxn(data.summary.totalEgress)}</Card.Title>
			</Card.Header>
			<Card.Content>
				<p class="text-muted-foreground text-xs">{m.dashboard_total_expenses_description()}</p>
			</Card.Content>
		</Card.Root>
	</div>

	<!-- Year Selector -->
	<div class="flex items-center gap-3">
		<Button variant="outline" size="sm" href="?year={prevYear}">← {prevYear}</Button>
		<span class="text-lg font-semibold">{data.year}</span>
		<Button
			variant="outline"
			size="sm"
			href={nextYear > currentYear ? undefined : `?year=${nextYear}`}
			class={nextYear > currentYear ? 'pointer-events-none opacity-40' : ''}
		>
			{nextYear} →
		</Button>
	</div>

	<!-- Monthly Bar Chart -->
	<Card.Root>
		<Card.Header>
			<Card.Title>{m.dashboard_monthly_income_expenses()}</Card.Title>
			<Card.Description>{data.year}</Card.Description>
		</Card.Header>
		<Card.Content>
			{#if isEmpty}
				<p class="text-muted-foreground py-2 text-center text-sm">{m.dashboard_no_transactions()}</p>
			{/if}
			<div class="overflow-x-auto">
				<svg width={BAR_W} height={BAR_H} aria-label={m.dashboard_chart_monthly_income_expenses()}>
					<g transform="translate({PAD.left},{PAD.top})">
						{#each bc.yTicks as tick}
							<line
								x1="0"
								y1={bc.y(tick)}
								x2={innerW}
								y2={bc.y(tick)}
								stroke="currentColor"
								stroke-opacity="0.1"
								stroke-dasharray="4,2"
							/>
							<text
								x="-6"
								y={bc.y(tick)}
								text-anchor="end"
								dominant-baseline="middle"
								font-size="10"
								fill="currentColor"
								opacity="0.5"
							>
								{mxn(tick)}
							</text>
						{/each}

						{#each bc.bars as bar}
							<rect
								x={bar.gx + bar.ingressX}
								y={bar.ingressY}
								width={bar.bandW}
								height={bar.ingressH}
								fill="#22c55e"
								opacity="0.85"
								rx="2"
							/>
							<rect
								x={bar.gx + bar.egressX}
								y={bar.egressY}
								width={bar.bandW}
								height={bar.egressH}
								fill="#ef4444"
								opacity="0.85"
								rx="2"
							/>
						{/each}

						<line
							x1="0"
							y1={innerH}
							x2={innerW}
							y2={innerH}
							stroke="currentColor"
							stroke-opacity="0.2"
						/>
						{#each bc.monthCenters as mc}
							<text
								x={mc.cx}
								y={innerH + 16}
								text-anchor="middle"
								font-size="11"
								fill="currentColor"
								opacity="0.6"
							>
								{MONTH_NAMES[mc.month - 1]()}
							</text>
						{/each}
					</g>
				</svg>
			</div>
			<div class="mt-2 flex gap-4 text-xs">
				<span class="flex items-center gap-1">
					<span class="inline-block h-3 w-3 rounded-sm bg-green-500"></span> {m.dashboard_income()}
				</span>
				<span class="flex items-center gap-1">
					<span class="inline-block h-3 w-3 rounded-sm bg-red-500"></span> {m.dashboard_expenses()}
				</span>
			</div>
		</Card.Content>
	</Card.Root>

	<!-- Yearly Trend Line -->
	<Card.Root>
		<Card.Header>
			<Card.Title>{m.dashboard_monthly_net_trend()}</Card.Title>
			<Card.Description>{m.dashboard_monthly_net_trend_description({ year: data.year })}</Card.Description>
		</Card.Header>
		<Card.Content>
			{#if isEmpty}
				<p class="text-muted-foreground py-2 text-center text-sm">{m.dashboard_no_transactions()}</p>
			{/if}
			<div class="overflow-x-auto">
				<svg width={LINE_W} height={LINE_H} aria-label={m.dashboard_chart_monthly_net_trend()}>
					<g transform="translate({LINE_PAD.left},{LINE_PAD.top})">
						<line
							x1="0"
							y1={lc.zero}
							x2={lineInnerW}
							y2={lc.zero}
							stroke="currentColor"
							stroke-opacity="0.3"
							stroke-dasharray="4,2"
						/>

						{#each lc.yTicks as tick}
							<line
								x1="0"
								y1={lc.y(tick)}
								x2={lineInnerW}
								y2={lc.y(tick)}
								stroke="currentColor"
								stroke-opacity="0.08"
							/>
							<text
								x="-6"
								y={lc.y(tick)}
								text-anchor="end"
								dominant-baseline="middle"
								font-size="10"
								fill="currentColor"
								opacity="0.5"
							>
								{mxn(tick)}
							</text>
						{/each}

						{#if lc.pathD}
							<path
								d={lc.pathD}
								fill="none"
								stroke="#6366f1"
								stroke-width="2"
								stroke-linejoin="round"
								stroke-linecap="round"
							/>
						{/if}

						{#each lc.points as p}
							<circle cx={p.cx} cy={p.cy} r="3" fill="#6366f1" />
						{/each}

						<line
							x1="0"
							y1={lineInnerH}
							x2={lineInnerW}
							y2={lineInnerH}
							stroke="currentColor"
							stroke-opacity="0.2"
						/>
						{#each lc.monthLabels as ml}
							<text
								x={ml.cx}
								y={lineInnerH + 16}
								text-anchor="middle"
								font-size="11"
								fill="currentColor"
								opacity="0.6"
							>
								{MONTH_NAMES[ml.month - 1]()}
							</text>
						{/each}
					</g>
				</svg>
			</div>
		</Card.Content>
	</Card.Root>
</div>
