export type AppLocale = 'en' | 'es';

export function appLocale(locale: string | undefined): AppLocale {
	return locale === 'en' ? 'en' : 'es';
}

export function formatLocale(locale: string | undefined): string {
	return appLocale(locale) === 'en' ? 'en-US' : 'es-MX';
}

export function mxnFormatter(locale: string | undefined): Intl.NumberFormat {
	return new Intl.NumberFormat(formatLocale(locale), { style: 'currency', currency: 'MXN' });
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
