/**
 * Loads one page section without letting its failure reach another one.
 *
 * `loadSection` never rejects and never returns a stand-in value: a caller gets
 * either parsed data or an explicit failure reason, so several sections can be
 * requested concurrently and one rejection cannot discard the results that did
 * arrive.
 */

import { sectionOk, sectionUnavailable, type Section } from '$lib/types/section';

export type SectionFetch = (input: string | URL | Request, init?: RequestInit) => Promise<Response>;

export type LoadSectionOptions<T> = {
	/** Validates the successful body; returning `null` marks it untrustworthy. */
	parse: (value: unknown) => T | null;
	headers?: Record<string, string>;
	/** Log prefix, e.g. `dashboard/summary`. */
	label: string;
};

/**
 * Loads a section whose backend documents an absent-but-normal state.
 *
 * Some endpoints answer 404 for "the User never configured this", which is a
 * fact about the data rather than a failure — e.g. a Credit Financial Account
 * with no Credit settings saved yet. Those statuses resolve to `ok` with `null`
 * data so the caller can distinguish "nothing configured" from "we could not
 * find out". Every other non-OK status stays a genuine failure.
 */
export async function loadOptionalSection<T>(
	fetchFn: SectionFetch,
	url: string,
	options: LoadSectionOptions<T> & { absentStatuses: number[] },
): Promise<Section<T | null>> {
	return loadSectionInternal<T | null>(fetchFn, url, options, options.absentStatuses);
}

export async function loadSection<T>(
	fetchFn: SectionFetch,
	url: string,
	options: LoadSectionOptions<T>,
): Promise<Section<T>> {
	return loadSectionInternal<T>(fetchFn, url, options, []);
}

async function loadSectionInternal<T>(
	fetchFn: SectionFetch,
	url: string,
	options: LoadSectionOptions<unknown>,
	absentStatuses: number[],
): Promise<Section<T>> {
	let response: Response;
	try {
		response = await fetchFn(url, { headers: options.headers ?? {} });
	} catch {
		console.error(`[${options.label}] unavailable: backend unreachable`);
		return sectionUnavailable('unreachable');
	}

	if (absentStatuses.includes(response.status)) {
		// Documented absence, not a failure: nothing is configured here.
		return sectionOk(null as T);
	}

	if (!response.ok) {
		console.error(`[${options.label}] unavailable: backend returned ${response.status}`);
		return sectionUnavailable('error');
	}

	let body: unknown;
	try {
		body = await response.json();
	} catch {
		console.error(`[${options.label}] unavailable: response body was not JSON`);
		return sectionUnavailable('invalid');
	}

	const parsed = options.parse(body) as T | null;
	if (parsed === null) {
		console.error(`[${options.label}] unavailable: response failed payload validation`);
		return sectionUnavailable('invalid');
	}

	return sectionOk(parsed);
}
