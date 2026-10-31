export type AppLocale = 'en' | 'es';

export function appLocale(locale: string | undefined): AppLocale {
	return locale === 'en' ? 'en' : 'es';
}

export function formatLocale(locale: string | undefined): string {
	return appLocale(locale) === 'en' ? 'en-US' : 'es-MX';
}

/** The currencies Keenti supports (ADR-0024). The backend is the authority; this mirrors it. */
export const SUPPORTED_CURRENCIES = ['MXN', 'EUR', 'USD', 'GBP'] as const;
export type CurrencyCode = (typeof SUPPORTED_CURRENCIES)[number];
export const DEFAULT_CURRENCY: CurrencyCode = 'MXN';

export function isSupportedCurrency(value: unknown): value is CurrencyCode {
	return typeof value === 'string' && (SUPPORTED_CURRENCIES as readonly string[]).includes(value);
}

const moneyFormatters = new Map<string, Intl.NumberFormat>();

/**
 * Formats an amount in its own currency. Every amount carries one (ADR-0024);
 * never assume MXN.
 *
 * `currencyDisplay: 'narrowSymbol'` renders `$` for MXN in es-MX and `€` for
 * EUR everywhere, so pesos stay as they always looked. When a screen shows
 * several currencies side by side, pass `{ code: true }` to disambiguate `$`.
 *
 * A missing currency can only come from a record written before currencies
 * existed, and every such amount was MXN; that is the one case the fallback
 * covers.
 */
export function moneyFormatter(
	locale: string | undefined,
	currency: string | null | undefined,
	options: { code?: boolean } = {},
): Intl.NumberFormat {
	const code = currency ?? DEFAULT_CURRENCY;
	const key = `${formatLocale(locale)}|${code}|${options.code ? 'code' : 'symbol'}`;
	let formatter = moneyFormatters.get(key);
	if (!formatter) {
		formatter = new Intl.NumberFormat(formatLocale(locale), {
			style: 'currency',
			currency: code,
			currencyDisplay: options.code ? 'code' : 'narrowSymbol',
		});
		moneyFormatters.set(key, formatter);
	}
	return formatter;
}

/**
 * Axis-tick formatter: `1.2 k` rather than `$1,200.00`. Full precision on an
 * axis is noise — the tick only has to locate the gridline, and the exact
 * figure belongs in the tooltip. Deliberately *not* currency-styled: es-MX
 * renders compact currency as `10 k$`, and the symbol earns nothing on an axis
 * whose card title and tooltip already say what the amounts are.
 */
export function compactAmountFormatter(locale: string | undefined): Intl.NumberFormat {
	return new Intl.NumberFormat(formatLocale(locale), {
		notation: 'compact',
		maximumFractionDigits: 1,
	});
}

export function shortDateFormatter(locale: string | undefined): Intl.DateTimeFormat {
	return new Intl.DateTimeFormat(formatLocale(locale), {
		day: '2-digit',
		month: 'short',
		year: 'numeric',
	});
}

export function monthYearFormatter(locale: string | undefined): Intl.DateTimeFormat {
	return new Intl.DateTimeFormat(formatLocale(locale), { month: 'short', year: 'numeric' });
}

/** Formats an ISO calendar date without converting it through UTC. */
export function formatDateOnly(value: string, locale: string | undefined): string {
	const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
	if (!match) return value;

	const [, year, month, day] = match;
	return shortDateFormatter(locale).format(new Date(Number(year), Number(month) - 1, Number(day)));
}

/**
 * Formats an ISO calendar date as a month and year, without a UTC round-trip.
 *
 * Mirrors `formatDateOnly` in returning the raw value when it is not an ISO
 * calendar date: `Intl` throws on an invalid `Date`, and a date that slipped
 * through from a malformed backend response should not take the whole page
 * down with it.
 */
export function formatMonthYear(value: string, locale: string | undefined): string {
	const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
	if (!match) return value;

	const [, year, month, day] = match;
	return monthYearFormatter(locale).format(new Date(Number(year), Number(month) - 1, Number(day)));
}

/** Returns the calendar date seen in an IANA time zone as an ISO date. */
export function dateInTimeZone(timeZone: string | undefined, instant = new Date()): string {
	let formatter: Intl.DateTimeFormat;
	try {
		formatter = new Intl.DateTimeFormat('en-US', {
			timeZone: timeZone || 'UTC',
			year: 'numeric',
			month: '2-digit',
			day: '2-digit',
		});
	} catch {
		formatter = new Intl.DateTimeFormat('en-US', {
			timeZone: 'UTC',
			year: 'numeric',
			month: '2-digit',
			day: '2-digit',
		});
	}
	const parts = new Map(
		formatter
			.formatToParts(instant)
			.filter((part) => part.type === 'year' || part.type === 'month' || part.type === 'day')
			.map((part) => [part.type, part.value]),
	);
	return `${parts.get('year')}-${parts.get('month')}-${parts.get('day')}`;
}
