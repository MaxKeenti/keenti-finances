import { WorkOS } from '@workos-inc/node';

function getEnv(key: string): string {
	const value = process.env[key];
	if (!value) {
		throw new Error(`Missing required environment variable: ${key}`);
	}
	return value;
}

function createClient(): WorkOS {
	const apiKey = process.env.WORKOS_API_KEY;
	if (!apiKey) {
		throw new Error('Missing required environment variable: WORKOS_API_KEY');
	}
	return new WorkOS(apiKey);
}

// Singleton — module is server-only so this is safe
export const workos = createClient();

export function getAuthorizationUrl(redirectUri: string): string {
	const clientId = getEnv('WORKOS_CLIENT_ID');
	return workos.userManagement.getAuthorizationUrl({
		provider: 'authkit',
		redirectUri,
		clientId,
	});
}
