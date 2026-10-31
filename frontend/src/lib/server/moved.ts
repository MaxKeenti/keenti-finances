import { createHash } from 'node:crypto';

/**
 * Where an address goes now that this app has moved (ADR-0025). Each record kept its identity on
 * the way: its id there is worked out from its table and its id here, by the same rule the import
 * used, so no table of pairs has to outlive this one.
 */

/** Shared with the import that carried the records across. Changing it breaks every link below. */
const RECORD_NAMESPACE = '6ce15eb1-bd0d-4aca-80f8-276a19deac67';

/** The pages of one record, each with the table its numeric id belongs to. */
const RECORD_PAGES: [page: RegExp, table: string, list: string][] = [
	[/^\/accounts\/(\d+)$/, 'financial_account', '/accounts'],
	[/^\/boxes\/(\d+)$/, 'box', '/boxes'],
	[/^\/debts\/(\d+)$/, 'debt', '/debts'],
	[/^\/subscriptions\/(\d+)$/, 'subscription', '/subscriptions'],
];

/** Addresses with no page of their own there. The planning preview did not move. */
const NEAREST_PAGE: [page: RegExp, nearest: string][] = [
	[/^\/transactions\/\d+$/, '/transactions'],
	[/^\/planning$/, '/'],
	[/^\/callback$/, '/'],
	[/^\/logout$/, '/'],
];

/** What a page there reads from its address the way this one did. The rest would mislead it. */
const KEPT_QUERY: Record<string, string[]> = {
	'/': ['year', 'currency'],
	'/debts': ['direction'],
};

/** The version 5 uuid of `name` in `namespace`, as RFC 9562 defines it. */
function nameBasedUuid(namespace: string, name: string): string {
	const bytes = createHash('sha1')
		.update(Buffer.from(namespace.replaceAll('-', ''), 'hex'))
		.update(name, 'utf8')
		.digest()
		.subarray(0, 16);
	bytes[6] = (bytes[6] & 0x0f) | 0x50;
	bytes[8] = (bytes[8] & 0x3f) | 0x80;
	const hex = bytes.toString('hex');
	return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join('-');
}

function pathThere(path: string): string {
	for (const [page, table, list] of RECORD_PAGES) {
		const id = page.exec(path)?.[1];
		if (id) return `${list}/${nameBasedUuid(RECORD_NAMESPACE, `${table}:${Number(id)}`)}`;
	}
	return NEAREST_PAGE.find(([page]) => page.test(path))?.[1] ?? path;
}

/**
 * The address of `url` on `origin`. The path is put after the origin as text, never resolved
 * against it, so nothing in the old address can lead anywhere but there. Public Subscription
 * links keep their path, and so their token: it is the same token there.
 */
export function movedAddress(origin: string, url: URL): string {
	const path = pathThere(url.pathname);
	const query = new URLSearchParams();
	for (const name of KEPT_QUERY[path] ?? []) {
		const value = url.searchParams.get(name);
		if (value !== null) query.set(name, value);
	}
	const search = query.size > 0 ? `?${query}` : '';
	return `${origin.replace(/\/+$/, '')}${path}${search}`;
}
