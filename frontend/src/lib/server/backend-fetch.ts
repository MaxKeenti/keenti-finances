type BackendFetcher = (request: Request) => Promise<Response>;
type Wait = (delayMs: number, signal: AbortSignal) => Promise<void>;

export type BackendWakeRetryOptions = {
	delaysMs?: readonly number[];
	wait?: Wait;
};

const WAKE_RETRY_DELAYS_MS = [250, 500, 1_000, 2_000, 3_000, 4_000, 5_000] as const;
const RETRYABLE_STATUSES = new Set([502, 503, 504]);
const RETRYABLE_METHODS = new Set(['GET', 'HEAD']);

function waitFor(delayMs: number, signal: AbortSignal): Promise<void> {
	if (signal.aborted) {
		return Promise.reject(signal.reason ?? new DOMException('The operation was aborted', 'AbortError'));
	}

	return new Promise((resolve, reject) => {
		const timer = setTimeout(() => {
			signal.removeEventListener('abort', onAbort);
			resolve();
		}, delayMs);

		function onAbort() {
			clearTimeout(timer);
			reject(signal.reason ?? new DOMException('The operation was aborted', 'AbortError'));
		}

		signal.addEventListener('abort', onAbort, { once: true });
	});
}

async function discard(response: Response): Promise<void> {
	try {
		await response.body?.cancel();
	} catch {
		// A failed retry response is deliberately discarded before the next attempt.
	}
}

/**
 * Waits through a Railway backend cold start for idempotent reads.
 *
 * Mutating requests are always single-attempt: automatically replaying a write
 * could duplicate a transaction after an ambiguous network failure.
 */
export async function fetchBackendWithWakeRetry(
	request: Request,
	fetcher: BackendFetcher,
	options: BackendWakeRetryOptions = {},
): Promise<Response> {
	if (!RETRYABLE_METHODS.has(request.method.toUpperCase())) {
		return fetcher(request);
	}

	const delaysMs = options.delaysMs ?? WAKE_RETRY_DELAYS_MS;
	const wait = options.wait ?? waitFor;

	for (let attempt = 0; ; attempt += 1) {
		try {
			const response = await fetcher(request);
			if (!RETRYABLE_STATUSES.has(response.status) || attempt >= delaysMs.length) {
				return response;
			}
			await discard(response);
		} catch (error) {
			if (attempt >= delaysMs.length || request.signal.aborted) {
				throw error;
			}
		}

		await wait(delaysMs[attempt], request.signal);
	}
}
