export type PublicSubscriptionLoadResult = 'backend_unreachable' | 'not_found' | 'backend_error';

/**
 * Logs Public Subscription View outcomes without accepting the capability token.
 * Never add a token, token fingerprint, request path, or capability URL here.
 */
export function logPublicSubscriptionLoad(
	result: PublicSubscriptionLoadResult,
	status: number,
): void {
	const message = `public.subscription.load result=${result} status=${status}`;
	if (result === 'not_found') {
		console.info(message);
		return;
	}
	console.error(message);
}
