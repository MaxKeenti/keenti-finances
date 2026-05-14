import type { RequestHandler } from '@sveltejs/kit';

export const GET: RequestHandler = async () => {
	return new Response(JSON.stringify({ status: 'UP' }), {
		status: 200,
		headers: { 'content-type': 'application/json' },
	});
};
