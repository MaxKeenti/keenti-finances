/**
 * User-facing names for the supported currencies (ADR-0024).
 *
 * Codes come from the backend; this maps them to translated names and never
 * renders a raw code on its own where a name fits.
 */

import { m } from '$lib/paraglide/messages.js';
import { SUPPORTED_CURRENCIES } from '$lib/formatting';

export function currencyName(code: string): string {
	switch (code) {
		case 'MXN':
			return m.currency_name_mxn();
		case 'EUR':
			return m.currency_name_eur();
		case 'USD':
			return m.currency_name_usd();
		case 'GBP':
			return m.currency_name_gbp();
		default:
			return code;
	}
}

/** "Euro (EUR)": the name for recognition, the code to tell `$` currencies apart. */
export function currencyOptionLabel(code: string): string {
	return `${currencyName(code)} (${code})`;
}

export function currencyItems(codes: readonly string[] = SUPPORTED_CURRENCIES): { value: string; label: string }[] {
	return codes.map((code) => ({ value: code, label: currencyOptionLabel(code) }));
}
