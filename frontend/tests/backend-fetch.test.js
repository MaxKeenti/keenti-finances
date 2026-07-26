// @ts-nocheck
import { describe, expect, test } from 'bun:test';
import { fetchBackendWithWakeRetry } from '../src/lib/server/backend-fetch';

const noRealWait = (delays) => async (delayMs) => {
	delays.push(delayMs);
};

describe('fetchBackendWithWakeRetry', () => {
	test('retries a safe read after network failures', async () => {
		let calls = 0;
		const delays = [];
		const response = await fetchBackendWithWakeRetry(
			new Request('http://backend/api/categories'),
			async () => {
				calls += 1;
				if (calls < 3) throw new TypeError('fetch failed');
				return new Response('ready', { status: 200 });
			},
			{ delaysMs: [100, 200], wait: noRealWait(delays) },
		);

		expect(response.status).toBe(200);
		expect(calls).toBe(3);
		expect(delays).toEqual([100, 200]);
	});

	test('retries transient gateway responses', async () => {
		let calls = 0;
		const delays = [];
		const response = await fetchBackendWithWakeRetry(
			new Request('http://backend/api/categories'),
			async () => {
				calls += 1;
				return new Response(null, { status: calls === 1 ? 503 : 404 });
			},
			{ delaysMs: [250], wait: noRealWait(delays) },
		);

		expect(response.status).toBe(404);
		expect(calls).toBe(2);
		expect(delays).toEqual([250]);
	});

	test('does not retry a normal application response', async () => {
		let calls = 0;
		const response = await fetchBackendWithWakeRetry(
			new Request('http://backend/api/public/subscriptions/missing'),
			async () => {
				calls += 1;
				return new Response(null, { status: 404 });
			},
			{ delaysMs: [1], wait: noRealWait([]) },
		);

		expect(response.status).toBe(404);
		expect(calls).toBe(1);
	});

	test('never retries a write after an ambiguous failure', async () => {
		let calls = 0;
		const failure = new TypeError('fetch failed');

		await expect(
			fetchBackendWithWakeRetry(
				new Request('http://backend/api/transactions', { method: 'POST', body: '{}' }),
				async () => {
					calls += 1;
					throw failure;
				},
				{ delaysMs: [1, 2], wait: noRealWait([]) },
			),
		).rejects.toBe(failure);

		expect(calls).toBe(1);
	});

	test('returns the final transient response after the retry budget', async () => {
		let calls = 0;
		const delays = [];
		const response = await fetchBackendWithWakeRetry(
			new Request('http://backend/q/health/ready'),
			async () => {
				calls += 1;
				return new Response(null, { status: 503 });
			},
			{ delaysMs: [10, 20], wait: noRealWait(delays) },
		);

		expect(response.status).toBe(503);
		expect(calls).toBe(3);
		expect(delays).toEqual([10, 20]);
	});
});
