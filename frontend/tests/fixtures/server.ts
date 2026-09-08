/**
 * Local fixture backend for browser verification.
 *
 * Serves one scenario's routes over HTTP so `vite dev` can be pointed at it
 * with `BACKEND_URL`. It reuses the same scenario data as the loader tests, so
 * the browser and the tests cannot disagree about a fixture's balances.
 *
 * Usage:
 *   bun run tests/fixtures/server.ts FX-BAL-OVERRESERVED-01
 *   FIXTURE_FAIL='GET /api/dashboard/summary=500' bun run tests/fixtures/server.ts FX-BAL-ZERO-01
 *
 * It is read-only: it never writes, and it never proxies to a real backend, so
 * pointing the dev server at it cannot reach or modify shared data. It binds
 * loopback only, so a fixture scenario is never exposed on the network.
 *
 * It does not control the browser's clock. Scenario clocks are inputs to the
 * data and to `withFixtureClock` in tests; a page served here renders against
 * the real wall clock of the machine.
 */

import { loadScenario, scenarioIds } from './scenarios';

const scenarioId = process.argv[2] ?? process.env.FIXTURE_SCENARIO;
if (!scenarioId) {
	console.error(`Usage: bun run tests/fixtures/server.ts <scenario-id>`);
	console.error(`Scenarios: ${scenarioIds().join(', ')}`);
	process.exit(1);
}

const scenario = loadScenario(scenarioId);
const port = Number(process.env.FIXTURE_PORT ?? 8099);

/**
 * `FIXTURE_FAIL='GET /api/x=500,GET /api/y=503'`
 *
 * Only HTTP status codes are supported. A `fetch` handler cannot drop a
 * connection — throwing from it produces a 500 — so this server deliberately
 * offers no "unreachable" mode rather than a misleading imitation of one. Real
 * network rejection is available in the in-process backend
 * (`createFixtureBackend(..., { failures: { key: { kind: 'unreachable' } } })`),
 * which is where loaders are tested. 503 is the documented surrogate here; note
 * that `fetchBackendWithWakeRetry` retries 502/503/504 GETs through its full
 * delay ladder (~16s) before the loader sees the error.
 */
const failures = new Map<string, number>(
	(process.env.FIXTURE_FAIL ?? '')
		.split(',')
		.map((entry) => entry.trim())
		.filter(Boolean)
		.map((entry) => {
			const separator = entry.lastIndexOf('=');
			const key = entry.slice(0, separator).trim();
			const value = entry.slice(separator + 1).trim();
			const status = Number(value);
			if (!Number.isInteger(status) || status < 400 || status > 599) {
				console.error(
					`[fixture-backend] FIXTURE_FAIL entry "${entry}" is not an HTTP error status. ` +
						`Use e.g. "${key}=500" or "${key}=503"; connection-level failure is only ` +
						`available in the in-process fixture backend.`,
				);
				process.exit(1);
			}
			return [key, status] as const;
		}),
);

function jsonResponse(body: unknown, status = 200): Response {
	return new Response(JSON.stringify(body ?? null), {
		status,
		headers: { 'content-type': 'application/json' },
	});
}

/**
 * Routes every authenticated page needs, regardless of scenario.
 *
 * `+layout.server.ts` requests these on each navigation. A scenario that
 * declares one wins; this only keeps the shell from degrading to defaults in
 * scenarios whose subject is something else. `timeZone` matches the fixture
 * clocks' Mexico City zone, so a date rendered in the browser is interpreted in
 * the same zone the scenarios were written against — the instant, however, is
 * the machine's real clock, not a fixture instant.
 */
const HARNESS_ROUTES: Record<string, unknown> = {
	'GET /api/user/preferences': {
		primaryHue: 91,
		headingFont: 'Fraunces',
		bodyFont: 'Geist',
		locale: 'es',
		transactionPageSize: 25,
		transactionSortBy: 'transactionDate',
		transactionSortDirection: 'desc',
		mobilePinnedNavItems: '/transactions,/subscriptions,/debts',
		dockMagnification: true,
		timeZone: 'America/Mexico_City',
		themeMode: 'system',
	},
};

// `@types/bun` is not a dependency of this package; this is the small slice of
// `Bun.serve` the fixture server actually uses.
declare const Bun: {
	serve(options: {
		port: number;
		hostname: string;
		fetch(request: Request): Response;
	}): { port: number; hostname: string };
};

const server = Bun.serve({
	port,
	// Loopback only. Binding every interface would publish invented balances on
	// whatever network the machine happens to be on.
	hostname: '127.0.0.1',
	fetch(request: Request): Response {
		const key = `${request.method.toUpperCase()} ${new URL(request.url).pathname}`;
		const failure = failures.get(key);

		if (failure !== undefined) {
			console.log(`[fixture-backend] ${key} -> ${failure} (injected)`);
			return jsonResponse({ error: 'injected fixture failure' }, failure);
		}

		if (key in scenario.routes) {
			console.log(`[fixture-backend] ${key} -> 200`);
			return jsonResponse(scenario.routes[key]);
		}

		if (key in HARNESS_ROUTES) {
			console.log(`[fixture-backend] ${key} -> 200 (harness default)`);
			return jsonResponse(HARNESS_ROUTES[key]);
		}

		console.log(`[fixture-backend] ${key} -> 404 (not declared by ${scenario.id})`);
		return jsonResponse({ error: `not declared by fixture ${scenario.id}` }, 404);
	},
});

console.log(`[fixture-backend] scenario ${scenario.id} on http://127.0.0.1:${server.port}`);
console.log(`[fixture-backend] ${scenario.description}`);
