import { createCipheriv, createDecipheriv, randomBytes, scryptSync } from 'node:crypto';
import type { Cookies } from '@sveltejs/kit';

export const COOKIE_NAME = 'wos_session';

const ALGORITHM = 'aes-256-gcm';
const SALT = 'workos-session-v1';

export interface WorkOSSessionUser {
	id: string;
	email: string;
	firstName: string | null;
	lastName: string | null;
}

export interface WorkOSSession {
	accessToken: string;
	refreshToken: string;
	user: WorkOSSessionUser;
}

function getKey(): Buffer {
	const password = process.env.WORKOS_COOKIE_PASSWORD;
	if (!password || password.length < 32) {
		throw new Error(
			'WORKOS_COOKIE_PASSWORD must be set and at least 32 characters long',
		);
	}
	// Derive a 32-byte key from the password; fixed salt is acceptable here
	// because the IV is randomized per encryption and the password is long.
	return scryptSync(password, SALT, 32);
}

export function sealSession(data: WorkOSSession): string {
	const key = getKey();
	const iv = randomBytes(12); // 96-bit IV for GCM
	const cipher = createCipheriv(ALGORITHM, key, iv);
	const plaintext = JSON.stringify(data);
	const encrypted = Buffer.concat([cipher.update(plaintext, 'utf8'), cipher.final()]);
	const authTag = cipher.getAuthTag();
	// Layout: iv(12) | authTag(16) | ciphertext
	const payload = Buffer.concat([iv, authTag, encrypted]);
	return payload.toString('base64url');
}

export function unsealSession(cookieValue: string): WorkOSSession | null {
	try {
		const key = getKey();
		const payload = Buffer.from(cookieValue, 'base64url');
		if (payload.length < 28) return null; // 12 + 16 minimum
		const iv = payload.subarray(0, 12);
		const authTag = payload.subarray(12, 28);
		const ciphertext = payload.subarray(28);
		const decipher = createDecipheriv(ALGORITHM, key, iv);
		decipher.setAuthTag(authTag);
		const decrypted = Buffer.concat([decipher.update(ciphertext), decipher.final()]);
		return JSON.parse(decrypted.toString('utf8')) as WorkOSSession;
	} catch {
		return null;
	}
}

export function getSession(cookies: Cookies): WorkOSSession | null {
	const value = cookies.get(COOKIE_NAME);
	if (!value) return null;
	return unsealSession(value);
}

export function setSession(cookies: Cookies, data: WorkOSSession): void {
	const isProd = process.env.NODE_ENV === 'production';
	cookies.set(COOKIE_NAME, sealSession(data), {
		path: '/',
		httpOnly: true,
		sameSite: 'lax',
		secure: isProd,
		maxAge: 60 * 60 * 24 * 7, // 7 days
	});
}

export function clearSession(cookies: Cookies): void {
	cookies.delete(COOKIE_NAME, { path: '/' });
}
