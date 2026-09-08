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
};

export type FixtureBackend = {
	scenario: Scenario;
	/** Drop-in replacement for SvelteKit's `fetch` in a loader test. */
	fetch: (input: string | URL | Request, init?: RequestInit) => Promise<Response>;
	/** Every request the code under test made, in order. */
	requests: RecordedRequest[];
	/** Clears recorded requests and injected failures. */
	reset: () => void;
	/** Adds or replaces failure injection after construction. */
	failRoute: (key: string, mode: FailureMode) => void;
	/** Removes failure injection for one route. */
	healRoute: (key: string) => void;
};

function routeKey(method: string, url: string): string {
	const path = new URL(url, 'http://fixture.backend').pathname;
	return `${method.toUpperCase()} ${path}`;
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

	async function fixtureFetch(
		input: string | URL | Request,
		init?: RequestInit,
	): Promise<Response> {
		const url = requestUrl(input);
		const method = requestMethod(input, init);
		const key = routeKey(method, url);
		requests.push({ method, url, key });

		const failure = failures[key];
		if (failure?.kind === 'unreachable') {
			throw new TypeError(failure.message ?? 'fetch failed');
		}
		if (failure?.kind === 'status') {
			return json(failure.body ?? { error: 'fixture failure' }, failure.status);
		}

		if (!(key in routes)) {
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
		reset() {
			requests.length = 0;
			failures = { ...initialFailures };
		},
		failRoute(key, mode) {
			failures[key] = mode;
		},
		healRoute(key) {
			delete failures[key];
		},
	};
}
