// @ts-nocheck
import { expect, test } from 'bun:test';
import { logPublicSubscriptionLoad } from '../src/lib/server/public-subscription-log';

test('public subscription logging exposes only stable result and status fields', () => {
	const messages = [];
	const originalInfo = console.info;
	const originalError = console.error;
	console.info = (message) => messages.push(message);
	console.error = (message) => messages.push(message);

	try {
		logPublicSubscriptionLoad('not_found', 404);
		logPublicSubscriptionLoad('backend_unreachable', 502);
		logPublicSubscriptionLoad('backend_error', 503);
	} finally {
		console.info = originalInfo;
		console.error = originalError;
	}

	expect(messages).toEqual([
		'public.subscription.load result=not_found status=404',
		'public.subscription.load result=backend_unreachable status=502',
		'public.subscription.load result=backend_error status=503',
	]);
});
