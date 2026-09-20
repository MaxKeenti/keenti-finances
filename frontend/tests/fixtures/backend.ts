/**
 * In-process fixture backend and controlled failure injection.
 *
 * A scenario's routes are served from memory by a `fetch`-compatible function,
 * so loader tests never touch the network, the development service, or shared
 * data. Anything a loader requests that the scenario does not declare is an
 * error rather than an empty body: a silently-empty response is exactly the
 * failure mode Slice 1A-loader has to distinguish.
 */

import { loadScenario, type Scenario } from './scenarios';

/** How a route should fail instead of returning its fixture body. */
export type FailureMode =
	/** The request rejects, as when the backend is unreachable. */
	| { kind: 'unreachable'; message?: string }
	/** The request resolves with an error status. */
	| { kind: 'status'; status: number; body?: unknown };

export type FixtureBackendOptions = {
	/**
	 * Failure injection keyed exactly like a route, e.g.
	 * `{ 'GET /api/dashboard/summary': { kind: 'status', status: 500 } }`.
	 */
	failures?: Record<string, FailureMode>;
	/** Extra or overriding route bodies for a one-off variation. */
	routes?: Record<string, unknown>;
};

export type RecordedRequest = {
	method: string;
	/** Path without the origin, including any query string. */
	url: string;
	/** Route key used for lookup, e.g. `GET /api/accounts`. */
	key: string;
	/**
	 * The request body as sent, when there was one.
	 *
	 * A test that asserts what a loader forwarded needs the payload, not just
	 * that a write happened: sending the right route with the wrong body is the
	 * failure worth catching.
	 */
	body?: string;
};

export type FixtureBackend = {
	scenario: Scenario;
	/** Drop-in replacement for SvelteKit's `fetch` in a loader test. */
	fetch: (input: string | URL | Request, init?: RequestInit) => Promise<Response>;
	/** Every request the code under test made, in order. */
	requests: RecordedRequest[];
	/**
	 * Requests for routes the scenario does not declare, in order.
	 *
	 * The fixture backend answers those by throwing, which a loader cannot tell
	 * apart from an unreachable backend — so an outage test would still pass if
	 * the loader called a route that does not exist. This list is how a test
	 * proves the outage it asserts is the one it injected.
	 */
	undeclaredRequests: RecordedRequest[];
	/**
	 * Throws when any undeclared route was requested, naming the offenders.
	 *
	 * Call it after driving a loader — including in tests whose subject is a
	 * failure — so "unavailable" is only ever credited to the injected failure.
	 */
	assertNoUndeclaredRoutes: () => void;
	/** Clears recorded requests and injected failures. */
	reset: () => void;
	/**
	 * Declares route bodies for keys the scenario and the test did not cover.
	 *
	 * For shell routes every page needs (see `harness-routes`), so a scenario
	 * about something else does not have to restate them. Existing declarations
	 * always win, and nothing here suppresses injected failures.
	 */
	declareDefaultRoutes: (defaults: Record<string, unknown>) => void;
	/** Adds or replaces failure injection after construction. */
	failRoute: (key: string, mode: FailureMode) => void;
	/** Removes failure injection for one route. */
	healRoute: (key: string) => void;
};

/**
 * Route keys for one request, most specific first.
 *
 * A path alone cannot distinguish `GET /api/boxes` from
 * `GET /api/boxes?archived=true`, which are different lists of the User's
 * Boxes. A scenario may therefore declare either spelling: the query-bearing
 * key wins when present, and the bare path stays the default so every 0A
 * scenario keeps matching unchanged.
 */
export function routeKeys(method: string, url: string): string[] {
	const parsed = new URL(url, 'http://fixture.backend');
	const verb = method.toUpperCase();
	const bare = `${verb} ${parsed.pathname}`;
	return parsed.search ? [`${bare}${parsed.search}`, bare] : [bare];
}

function routeKey(method: string, url: string): string {
	return routeKeys(method, url)[0];
}

function requestUrl(input: string | URL | Request): string {
	if (typeof input === 'string') return input;
	if (input instanceof URL) return input.toString();
	return input.url;
}

function requestMethod(input: string | URL | Request, init?: RequestInit): string {
	if (init?.method) return init.method.toUpperCase();
	if (input instanceof Request) return input.method.toUpperCase();
	return 'GET';
}

function json(body: unknown, status = 200): Response {
	return new Response(JSON.stringify(body ?? null), {
		status,
		headers: { 'content-type': 'application/json' },
	});
}

/**
 * Builds a fixture backend for one scenario.
 *
 * The returned object is per-test state; construct a new one in each test
 * rather than sharing it, so recorded requests and injected failures cannot
 * leak between tests.
 */
export function createFixtureBackend(
	scenarioId: string,
	options: FixtureBackendOptions = {},
): FixtureBackend {
	const scenario = loadScenario(scenarioId);
	const routes: Record<string, unknown> = { ...scenario.routes, ...(options.routes ?? {}) };
	const initialFailures = { ...(options.failures ?? {}) };
	let failures: Record<string, FailureMode> = { ...initialFailures };
	const requests: RecordedRequest[] = [];
	const undeclaredRequests: RecordedRequest[] = [];

	async function fixtureFetch(
		input: string | URL | Request,
		init?: RequestInit,
	): Promise<Response> {
		const url = requestUrl(input);
		const method = requestMethod(input, init);
		const candidates = routeKeys(method, url);
		// The recorded key is the one that answered, so an assertion about which
		// routes were called names the declaration the loader actually hit.
		const key =
			candidates.find((candidate) => candidate in failures) ??
			candidates.find((candidate) => candidate in routes) ??
			routeKey(method, url);
		const request: RecordedRequest = { method, url, key };
		const body = init?.body;
		if (typeof body === 'string') request.body = body;
		requests.push(request);

		const failure = failures[key];
		if (failure?.kind === 'unreachable') {
			throw new TypeError(failure.message ?? 'fetch failed');
		}
		if (failure?.kind === 'status') {
			return json(failure.body ?? { error: 'fixture failure' }, failure.status);
		}

		if (!(key in routes)) {
			undeclaredRequests.push(request);
			throw new Error(
				`Fixture scenario ${scenario.id} does not declare ${key}. ` +
					`Declared routes: ${Object.keys(routes).sort().join(', ') || '(none)'}`,
			);
		}

		return json(routes[key]);
	}

	return {
		scenario,
		fetch: fixtureFetch,
		requests,
		undeclaredRequests,
		assertNoUndeclaredRoutes() {
			if (undeclaredRequests.length === 0) return;
			const keys = [...new Set(undeclaredRequests.map((request) => request.key))].sort();
			throw new Error(
				`Fixture scenario ${scenario.id} was asked for undeclared route(s): ${keys.join(', ')}. ` +
					`A loader that calls an undeclared route sees the same rejection as an unreachable ` +
					`backend, so this would otherwise pass as an injected outage.`,
			);
		},
		reset() {
			requests.length = 0;
			undeclaredRequests.length = 0;
			failures = { ...initialFailures };
		},
		declareDefaultRoutes(defaults) {
			for (const [key, body] of Object.entries(defaults)) {
				if (!(key in routes)) routes[key] = body;
			}
		},
		failRoute(key, mode) {
			failures[key] = mode;
		},
		healRoute(key) {
			delete failures[key];
		},
	};
}
