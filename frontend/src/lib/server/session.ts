import { createHmac, timingSafeEqual } from 'node:crypto';

const COOKIE_NAME = 'session';
const SEP = '.';

function getSessionSecret(): string {
	if (process.env.NODE_ENV === 'production' && !process.env.SESSION_SECRET) {
		throw new Error('SESSION_SECRET env var must be set in production');
	}
	return process.env.SESSION_SECRET ?? 'dev-secret-change-in-production';
}

function sign(username: string): string {
	const sig = createHmac('sha256', getSessionSecret()).update(username).digest('base64url');
	return `${username}${SEP}${sig}`;
}

export function createSessionCookieValue(username: string): string {
	return sign(username);
}

export function validateSessionCookieValue(value: string): string | null {
	const lastDot = value.lastIndexOf(SEP);
	if (lastDot === -1) return null;
	const username = value.slice(0, lastDot);
	const expected = sign(username);
	try {
		const a = Buffer.from(value);
		const b = Buffer.from(expected);
		if (a.length !== b.length) return null;
		if (!timingSafeEqual(a, b)) return null;
	} catch {
		return null;
	}
	return username;
}

export { COOKIE_NAME };
