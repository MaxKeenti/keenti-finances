import type { RequestHandler } from '@sveltejs/kit';
import { fetchBackendWithWakeRetry } from '$lib/server/backend-fetch';

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';
const TIMEOUT_MS = 30_000;

async function proxy(event: Parameters<RequestHandler>[0]): Promise<Response> {
	const path = event.params.path ?? '';
	const url = `${BACKEND}/api/${path}${event.url.search}`;

	const headers = new Headers();
	for (const [key, value] of event.request.headers) {
		if (['host', 'connection', 'transfer-encoding'].includes(key.toLowerCase())) continue;
		headers.set(key, value);
	}
	if (event.locals.session) {
		headers.set('X-WorkOS-User-Id', event.locals.session.user.id);
	}

	const hasBody = !['GET', 'HEAD'].includes(event.request.method);
	const controller = new AbortController();
	const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);

	let upstream: Response;
	try {
		const upstreamRequest = new Request(url, {
			method: event.request.method,
			headers,
			body: hasBody ? event.request.body : undefined,
			duplex: hasBody ? 'half' : undefined,
			signal: controller.signal,
		} as RequestInit);
		upstream = await fetchBackendWithWakeRetry(upstreamRequest, fetch);
	} catch (err) {
		clearTimeout(timer);
		const isTimeout = (err as Error).name === 'AbortError';
		return new Response(JSON.stringify({ error: isTimeout ? 'Gateway timeout' : 'Bad gateway' }), {
			status: isTimeout ? 504 : 502,
			headers: { 'content-type': 'application/json' },
		});
	}
	clearTimeout(timer);

	const responseHeaders = new Headers();
	for (const [key, value] of upstream.headers) {
		if (['transfer-encoding', 'connection'].includes(key.toLowerCase())) continue;
		responseHeaders.set(key, value);
	}

	return new Response(upstream.body, {
		status: upstream.status,
		headers: responseHeaders,
	});
}

export const GET: RequestHandler = (event) => proxy(event);
export const POST: RequestHandler = (event) => proxy(event);
export const PUT: RequestHandler = (event) => proxy(event);
export const PATCH: RequestHandler = (event) => proxy(event);
export const DELETE: RequestHandler = (event) => proxy(event);
