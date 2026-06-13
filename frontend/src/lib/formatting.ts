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
