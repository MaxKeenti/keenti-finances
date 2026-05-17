import { WorkOS } from '@workos-inc/node';

function getEnv(key: string): string {
	const value = process.env[key];
	if (!value) {
		throw new Error(`Missing required environment variable: ${key}`);
	}
	return value;
}

// Lazy singleton — deferred until first call so build-time evaluation doesn't throw
let _client: WorkOS | null = null;

export function getWorkOS(): WorkOS {
	if (!_client) {
		_client = new WorkOS(getEnv('WORKOS_API_KEY'));
	}
	return _client;
}

export function getAuthorizationUrl(redirectUri: string): string {
	const clientId = getEnv('WORKOS_CLIENT_ID');
	return getWorkOS().userManagement.getAuthorizationUrl({
		provider: 'authkit',
		redirectUri,
		clientId,
	});
}
